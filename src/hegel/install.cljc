(ns hegel.install
  "Install pinned native dependencies for a jolt-hegel Git dependency.

  This namespace lives on the normal source path. Consumers activate whichever
  alias contains the dependency, for example
  `jolt -A:test -m hegel.install`; repository aliases are not inherited."
  (:require [clojure.string :as str]
            [hegel.host :as host]
            [hegel.install.backend :as install-backend]
            [hegel.native :as native]
            [hegel.version :as version]))

(def ^:private libhegel-release-base
  (str "https://github.com/hegeldev/hegel-rust/releases/download/v"
       version/libhegel-version))

(def ^:private libhegel-assets
  {[:linux "amd64"]
   {:name "libhegel-linux-amd64.so"
    :sha256 "12d804f8767f926f8ec22a733e57af265f0b79f383ce78cc8d9fcbae2a8a3ad7"}

   [:linux "arm64"]
   {:name "libhegel-linux-arm64.so"
    :sha256 "9cc5725fae13f9d79708dbce4d3c2343a193f2029fed7002f88e0f08f26a2efa"}

   [:darwin "arm64"]
   {:name "libhegel-darwin-arm64.dylib"
    :sha256 "ac3939a523ca5d98ed741e5894c3b44d0c7b0fa8baaa3deabc5653bb1d754df9"}

   [:windows "amd64"]
   {:name "libhegel-windows-amd64.dll"
    :sha256 "b0d334228b46177d93f7dd92a9da0882d23b0cea15927b3707d8562d3395741b"}

   [:windows "arm64"]
   {:name "libhegel-windows-arm64.dll"
    :sha256 "e7837056eb3de1b3842e98805e6e4cb85b22ddcd5f8e246dea1f52046f71d600"}})

(def ^:private jolt? (= :jolt (host/runtime)))

(defn- nonblank [value]
  (when-not (str/blank? value)
    value))

(defn- property [name]
  (nonblank (install-backend/property name)))

(defn- normalize-architecture [machine]
  (case (some-> machine str/trim str/lower-case)
    ("x86_64" "amd64" "x64") "amd64"
    ("aarch64" "arm64") "arm64"
    nil))

(defn- uname-machine []
  (nonblank (install-backend/uname-machine)))

(defn architecture
  "Return the release architecture name for the current Jolt process."
  []
  (let [{:keys [os]} (native/platform)
        candidates [(native/nonblank-env "HEGEL_NATIVE_ARCH")
                    (native/nonblank-env "HEGEL_LIBHEGEL_ARCH")
                    (property "os.arch")
                    (native/nonblank-env "RUNNER_ARCH")
                    (native/nonblank-env "PROCESSOR_ARCHITECTURE")
                    (native/nonblank-env "HOSTTYPE")
                    (when-not (= :windows os) (uname-machine))]
        architecture (some normalize-architecture candidates)]
    (or architecture
        (throw
         (ex-info
          (str "jolt-hegel has no native release mapping for architecture "
               (pr-str (first (remove str/blank? candidates))))
          {:type ::unsupported-architecture
           :candidates candidates})))))

(defn- path-exists? [path]
  (install-backend/path-exists? path))

(defn- directory? [path]
  (install-backend/directory? path))

(defn- regular-file? [path]
  (and (path-exists? path) (not (directory? path))))

(defn- ensure-directory! [path]
  (when-not (or (directory? path)
                (install-backend/mkdirs! path)
                ;; Another installer may have created it after our first
                ;; check; mkdirs returning false does not mean it is absent.
                (directory? path))
    (throw
     (ex-info (str "could not create " path)
              {:type ::directory-failed
               :path path})))
  path)

(defn- require-file! [description path]
  (when-not (regular-file? path)
    (throw
     (ex-info (str description " does not exist: " path)
              {:type ::missing-file
               :description description
               :path path})))
  path)

(defn- version-source-path []
  (native/join-path
   (native/join-path native/project-root-path "src")
   "hegel/version.cljc"))

(defn- source-jolt-hegel-version []
  (let [path (require-file! "jolt-hegel version source"
                            (version-source-path))
        match (re-find
               #"(?s)\(def\s+jolt-hegel-version\s+\"[^\"]*\"\s+\"([^\"]+)\"\s*\)"
               (install-backend/read-text path))]
    (or (second match)
        (throw
         (ex-info (str "could not read jolt-hegel version from " path)
                  {:type ::invalid-version-source
                   :path path})))))

(defn verify-source-version!
  "On Jolt, fail when AOT code came from a different release than the resolved
  source checkout. Other hosts do not AOT dependency namespaces and therefore
  return the loaded version without assuming the consumer's working directory
  is the dependency checkout."
  []
  (let [loaded version/jolt-hegel-version]
    (if-not jolt?
      loaded
      (let [source (source-jolt-hegel-version)]
        (when-not (= loaded source)
          (throw
           (ex-info
            (str "Jolt loaded jolt-hegel " loaded
                 " from a stale AOT cache, but the resolved source checkout is "
                 source ". Set JOLT_CACHE_DIR to a fresh directory keyed by the "
                 "pinned release SHA and rerun the install and test commands.")
            {:type ::stale-aot-cache
             :loaded-version loaded
             :source-version source
             :source-path (version-source-path)
             :jolt-cache-directory
             (native/nonblank-env "JOLT_CACHE_DIR")})))
        source))))

(defn- delete-if-present! [path]
  (when (and (path-exists? path)
             (not (install-backend/delete-file! path)))
    (throw
     (ex-info (str "could not remove " path)
              {:type ::delete-failed
               :path path}))))

(defn- download! [url path]
  (println "native: downloading" url)
  (delete-if-present! path)
  (host/try-catch-all
   (do
     (install-backend/download! (:os (native/platform)) url path)
     (require-file! "download" path))
   cause
   (delete-if-present! path)
   (throw
    (ex-info (str "failed to download " url)
             {:type ::download-failed
              :url url
              :path path
              :cause cause}))))

(defn- file-sha256 [path]
  (when (regular-file? path)
    (install-backend/sha256 (:os (native/platform)) path)))

(defn- checksum-matches? [path expected]
  (let [actual (file-sha256 path)]
    (and (some? actual) (= expected actual))))

(defn- verify-file! [path expected]
  (let [actual (file-sha256 path)]
    (when-not (and (some? actual) (= expected actual))
      (throw
       (ex-info (str "SHA-256 mismatch for " path)
                {:type ::checksum-mismatch
                 :expected expected
                 :actual actual
                 :path path})))
    path))

(defn- replace-file! [source target expected]
  ;; Never remove the target first. Some hosts atomically replace it; others
  ;; refuse an existing destination. A matching concurrent winner is success
  ;; in either case, but a mismatching target must survive a failed publish.
  (let [result (host/try-catch-all
                {:published? (install-backend/rename-file! source target)}
                cause
                {:published? false :cause cause})]
    (when-not (or (:published? result)
                  (checksum-matches? target expected))
      (throw
       (ex-info (str "could not publish verified download to " target
                     "; existing target was not removed")
                (cond-> {:type ::replace-failed
                         :source source
                         :path target}
                  (:cause result) (assoc :cause (:cause result))))))
    target))

(defn- download-verified! [url target expected]
  (let [staged (str target ".download-" (random-uuid))]
    (download! url staged)
    (host/try-catch-all
     (do
       (verify-file! staged expected)
       (replace-file! staged target expected)
       ;; A host that refuses replacement may have accepted another verified
       ;; winner. Its own staging file still belongs to this invocation.
       (delete-if-present! staged))
     cause
     (delete-if-present! staged)
     (throw cause))
    (println "native: verified" target "(sha256" expected ")")
    target))

(defn fetch-libhegel!
  "Fetch and verify the libhegel release pinned by hegel.version."
  []
  (verify-source-version!)
  (if (native/nonblank-env "HEGEL_LIBHEGEL_LIBRARY")
    (do
      (require-file! "HEGEL_LIBHEGEL_LIBRARY" native/library-path)
      (println "libhegel: using" native/library-path
               "(HEGEL_LIBHEGEL_LIBRARY)")
      native/library-path)
    (let [{:keys [os]} (native/platform)
          architecture (architecture)
          asset (get libhegel-assets [os architecture])]
      (when-not asset
        (throw
         (ex-info
          (str "libhegel v" version/libhegel-version
               " has no prebuilt release for " (name os) "/" architecture
               "; set HEGEL_LIBHEGEL_LIBRARY to a local build")
          {:type ::unavailable-libhegel-release
           :os os
           :architecture architecture})))
      (ensure-directory! native/cache-directory-path)
      (let [{:keys [name sha256]} asset
            url (str (or (native/nonblank-env "HEGEL_LIBHEGEL_RELEASE_BASE")
                         libhegel-release-base)
                     "/" name)]
        (if (checksum-matches? native/library-path sha256)
          (do
            (println "libhegel: already verified" native/library-path
                     "(sha256" sha256 ")")
            native/library-path)
          (download-verified! url native/library-path sha256))))))

(defn setup!
  "Install the libhegel release pinned by hegel.version."
  []
  (verify-source-version!)
  {:libhegel (fetch-libhegel!)})

(defn- print-paths! []
  (verify-source-version!)
  (println "project-root:" native/project-root-path)
  (println "cache-directory:" native/cache-directory-path)
  (println "libhegel:" native/library-path)
  nil)

(defn- print-version! []
  (println (verify-source-version!))
  nil)

(defn- usage! []
  (println
   (str "Usage: " (name (host/runtime)) " -m hegel.install "
        "[setup|fetch-libhegel|verify-source|paths|version]"))
  nil)

(defn -main [& arguments]
  (case (first arguments)
    (nil "setup") (setup!)
    "fetch-libhegel" (fetch-libhegel!)
    "verify-source" (println (verify-source-version!))
    "paths" (print-paths!)
    "version" (print-version!)
    (do
      (usage!)
      (throw
       (ex-info (str "unknown hegel.install command "
                     (pr-str (first arguments)))
                {:type ::unknown-command
                 :command (first arguments)}))))
  nil)
