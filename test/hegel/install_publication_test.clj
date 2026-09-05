(ns hegel.install-publication-test
  "Focused publication ownership and concurrent-winner tests for issue #46."
  (:require [clojure.string :as str]
            [clojure.test :as t]
            [hegel.install :as install]
            [hegel.install.backend :as backend]
            [hegel.native :as native]))

(def ^:private expected "verified-bytes")

(defn- rendezvous [participants]
  (let [arrivals (atom 0)
        opened (promise)]
    {:await! (fn []
               (when (= participants (swap! arrivals inc))
                 (deliver opened true))
               (deref opened 1000 ::barrier-timeout))
     ;; On a test failure, release every point before attempting reaping. This
     ;; prevents a future from observing restored production vars.
     :release! #(deliver opened ::barrier-released)}))

(defn- join-workers! [workers barriers]
  (let [initial (mapv #(deref % 5000 ::worker-timeout) workers)]
    (if-not (some #{::worker-timeout} initial)
      initial
      (do
        (doseq [barrier barriers]
          ((:release! barrier)))
        (let [reaped (mapv #(deref % 1000 ::worker-timeout) workers)]
          (when (some #{::worker-timeout} reaped)
            ;; A worker can no longer be safely left running after the seam is
            ;; restored. This is a test-harness terminal condition, not a
            ;; cancellation claim.
            (println "FAIL installer publication worker did not reap")
            (flush)
            (System/exit 1))
          reaped)))))

(defn- invoke-download-verified! [url target digest]
  ((deref #'install/download-verified!) url target digest))

(defn- publication-error [thunk]
  (try
    (thunk)
    nil
    (catch Throwable error error)))

(defn- unique-path [suffix]
  (let [name (str "jolt-hegel-install-publication-" (random-uuid) suffix)]
    ;; Jolt's Windows atomic-spit path treats a drive-rooted target as
    ;; relative. These test-owned artifacts use the launch directory there.
    (if (= :windows (:os (native/platform)))
      name
      (native/join-path (or (System/getProperty "java.io.tmpdir") ".") name))))

(defn- publish-once! [files source destination]
  ;; CAS, rather than a swap! side effect, makes exactly one fake installer the
  ;; winner even if Clojure retries the update function.
  (loop []
    (let [current @files]
      (if (or (contains? current destination)
              (not (contains? current source)))
        false
        (let [next (assoc (dissoc current source) destination (get current source))]
          (if (compare-and-set! files current next)
            true
            (recur)))))))

(t/deftest concurrent-installers-own-distinct-staging-and-accept-a-matching-winner
  (let [files (atom {})
        deletes (atom [])
        stages (atom [])
        download-barrier (rendezvous 2)
        verify-barrier (rendezvous 2)
        publish-barrier (rendezvous 2)
        barrier-failures (atom [])
        verified-stages (atom #{})
        target "cache/libhegel"
        exists? #(contains? @files %)
        download-verified! #(invoke-download-verified! % target expected)]
    (with-redefs [backend/path-exists? exists?
                  backend/directory? (constantly false)
                  backend/delete-file! (fn [path]
                                         (swap! deletes conj path)
                                         (swap! files dissoc path)
                                         true)
                  backend/download! (fn [_os _url staged]
                                      (swap! stages conj staged)
                                      (when (= ::barrier-timeout ((:await! download-barrier)))
                                        (swap! barrier-failures conj :download))
                                      (swap! files assoc staged expected)
                                      true)
                  backend/sha256 (fn [_os path]
                                   (when (and (str/includes? path ".download-")
                                              (not (contains? @verified-stages path)))
                                     (swap! verified-stages conj path)
                                     (when (= ::barrier-timeout ((:await! verify-barrier)))
                                       (swap! barrier-failures conj :verify)))
                                   (get @files path))
                  backend/rename-file! (fn [source destination]
                                         (when (= ::barrier-timeout ((:await! publish-barrier)))
                                           (swap! barrier-failures conj :publish))
                                         (publish-once! files source destination))]
      ;; Both workers are joined while the test seam is still installed.
      (let [a (future (publication-error #(download-verified! "release/a")))
            b (future (publication-error #(download-verified! "release/b")))
            results (join-workers! [a b]
                                   [download-barrier verify-barrier publish-barrier])]
        (t/is (not-any? #{::worker-timeout} results))
        (t/is (every? nil? results))
        (t/is (empty? @barrier-failures))
        (t/is (= expected (get @files target)))
        (t/is (= 2 (count @stages)))
        (t/is (apply not= @stages))
        (t/is (every? #(not (contains? @files %)) @stages))
        ;; The target is never cleanup owned by either installer.
        (t/is (not-any? #(= target %) @deletes))))))

(t/deftest mismatching-concurrent-winner-is-preserved-and-reported
  (let [files (atom {})
        target "cache/libhegel"
        staged (atom nil)]
    (with-redefs [backend/path-exists? #(contains? @files %)
                  backend/directory? (constantly false)
                  backend/delete-file! (fn [path] (swap! files dissoc path) true)
                  backend/download! (fn [_os _url path]
                                      (reset! staged path)
                                      (swap! files assoc path expected)
                                      true)
                  backend/sha256 (fn [_os path] (get @files path))
                  backend/rename-file! (fn [_source destination]
                                         (swap! files assoc destination "other-bytes")
                                         false)]
      (let [error (publication-error
                   #(invoke-download-verified! "release" target expected))]
        (t/is (= ::install/replace-failed (:type (ex-data error))))
        (t/is (= "other-bytes" (get @files target)))
        (t/is (not (contains? @files @staged)))))))

(t/deftest failed-publication-preserves-an-existing-old-target
  (let [target "cache/libhegel"
        files (atom {target "old-bytes"})
        staged (atom nil)]
    (with-redefs [backend/path-exists? #(contains? @files %)
                  backend/directory? (constantly false)
                  backend/delete-file! (fn [path] (swap! files dissoc path) true)
                  backend/download! (fn [_os _url path]
                                      (reset! staged path)
                                      (swap! files assoc path expected)
                                      true)
                  backend/sha256 (fn [_os path] (get @files path))
                  backend/rename-file! (constantly false)]
      (let [error (publication-error
                   #(invoke-download-verified! "release" target expected))]
        (t/is (= ::install/replace-failed (:type (ex-data error))))
        (t/is (= "old-bytes" (get @files target)))
        (t/is (not (contains? @files @staged)))))))

(t/deftest checksum-rejection-cleans-only-the-current-staging-file
  (let [target "cache/libhegel"
        files (atom {})
        staged (atom nil)
        publishes (atom 0)]
    (with-redefs [backend/path-exists? #(contains? @files %)
                  backend/directory? (constantly false)
                  backend/delete-file! (fn [path] (swap! files dissoc path) true)
                  backend/download! (fn [_os _url path]
                                      (reset! staged path)
                                      (swap! files assoc path "corrupt-bytes")
                                      true)
                  backend/sha256 (fn [_os path] (get @files path))
                  backend/rename-file! (fn [& _] (swap! publishes inc) true)]
      (let [error (publication-error
                   #(invoke-download-verified! "release" target expected))]
        (t/is (= ::install/checksum-mismatch (:type (ex-data error))))
        (t/is (zero? @publishes))
        (t/is (not (contains? @files @staged)))
        (t/is (not (contains? @files target)))))))

(t/deftest explicit-library-override-never-enters-publication
  (let [override "already-installed-library"
        downloads (atom 0)]
    (with-redefs [native/nonblank-env (fn [name]
                                         (when (= name "HEGEL_LIBHEGEL_LIBRARY")
                                           override))
                  native/library-path override
                  install/verify-source-version! (constantly "test-version")
                  backend/path-exists? #(= override %)
                  backend/directory? (constantly false)
                  backend/download! (fn [& _] (swap! downloads inc))]
      (t/is (= override (install/fetch-libhegel!)))
      (t/is (zero? @downloads)))))

(t/deftest concurrent-directory-creation-is-accepted-after-a-false-mkdirs-result
  (let [directory? (atom false)
        ensure-directory! (deref #'install/ensure-directory!)]
    (with-redefs [backend/directory? (fn [_] @directory?)
                  backend/mkdirs! (fn [_]
                                    ;; Simulate the other installer winning
                                    ;; between the initial check and mkdirs.
                                    (reset! directory? true)
                                    false)]
      (t/is (= "cache" (ensure-directory! "cache"))))))

(t/deftest selected-backend-publishes-a-unique-owned-local-staging-file
  (let [staged (unique-path ".download")
        target (unique-path ".library")]
    (try
      (spit staged expected)
      (t/is (true? (backend/rename-file! staged target)))
      (t/is (= expected (slurp target)))
      (t/is (not (backend/path-exists? staged)))
      (finally
        (when (backend/path-exists? staged)
          (backend/delete-file! staged))
        (when (backend/path-exists? target)
          (backend/delete-file! target))))))

(t/deftest selected-backend-existing-destination-either-replaces-or-preserves
  (let [staged (unique-path ".download")
        target (unique-path ".library")]
    (try
      (spit staged expected)
      (spit target "old-bytes")
      (let [outcome (try
                      {:published? (backend/rename-file! staged target)}
                      (catch Throwable error
                        {:published? false :error error}))]
        (if (:published? outcome)
          (do
            (t/is (= expected (slurp target)))
            (t/is (not (backend/path-exists? staged))))
          (do
            (t/is (= "old-bytes" (slurp target)))
            (t/is (= expected (slurp staged))))))
      (finally
        (when (backend/path-exists? staged)
          (backend/delete-file! staged))
        (when (backend/path-exists? target)
          (backend/delete-file! target))))))

(defn -main [& _]
  (let [result (t/run-tests 'hegel.install-publication-test)]
    (System/exit (if (zero? (+ (:fail result) (:error result))) 0 1))))
