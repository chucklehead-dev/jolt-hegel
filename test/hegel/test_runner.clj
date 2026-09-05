(ns hegel.test-runner
  (:require [hegel.native :as native]
            [hegel.scenario-manifest :as scenarios]
            [hegel.test-support :as support]))

(defn- default-progress-file []
  (let [filename (str "jolt-hegel-test-progress-" (random-uuid) ".log")]
    (if (= :windows (:os (native/platform)))
      filename
      (native/join-path
       (or (native/nonblank-env "RUNNER_TEMP")
           (native/nonblank-env "TMPDIR")
           (native/nonblank-env "TEMP")
           (native/nonblank-env "TMP")
           (System/getProperty "java.io.tmpdir") ".")
       filename))))

(def ^:private child-arguments
  {"--terminal-timeout-child" 'hegel.suites.timeout/timeout-regression-child!
   "--progress-identity-child" 'hegel.suites.timeout/progress-identity-child!})

(defn- bootstrap-load-probe []
  {:status (if (or (find-ns 'malli.core) (find-ns 'hegel.malli)) 1 0)
   :mode :bootstrap-load-probe
   :optional-namespaces {'malli.core (boolean (find-ns 'malli.core))
                         'hegel.malli (boolean (find-ns 'hegel.malli))}})

(defn- resolve-entrypoint [entrypoint]
  (or (requiring-resolve entrypoint)
      (throw (ex-info "manifest entrypoint did not resolve" {:entrypoint entrypoint}))))

(defn- parse-arguments [args]
  (cond
    (empty? args) {:mode :aggregate}
    (= ["--list-suites"] args) {:mode :list}
    (and (= 2 (count args)) (= "--suite" (first args)))
    {:mode :suite :suite (keyword (second args))}
    :else (throw (ex-info "invalid test runner arguments"
                          {:type ::invalid-arguments
                           :args args :usage "--list-suites | --suite NAME"}))))

(defn available-suites [manifest]
  (->> manifest (map :suite) distinct sort vec))

(defn run-dispatch!
  "Non-exiting manifest dispatcher; production `-main` is the exit wrapper."
  ([args] (run-dispatch! args {}))
  ([args {:keys [fixture manifest progress-path exit!]
          :or {fixture scenarios/fixture
               manifest scenarios/manifest
               progress-path (default-progress-file)
               exit! System/exit}}]
   ;; Exact child flags win before validation and CLI parsing.
   (cond
     (= ["--bootstrap-load-probe"] args) (bootstrap-load-probe)
     (get child-arguments (first args))
     (if (= 1 (count args))
       (let [context (support/new-context {:progress-path progress-path
                                           :timeout-ms support/default-timeout-ms})]
         ((resolve-entrypoint (get child-arguments (first args))) context exit!)
         {:status 0 :mode :child})
       {:status 2 :error :invalid-child-arguments})
     :else (try
       (let [manifest (support/validate-manifest! fixture manifest)
             {:keys [mode suite]} (parse-arguments args)]
         (if (= :list mode)
           {:status 0 :mode :list :suites (available-suites manifest)}
           (let [entries (if (= :suite mode)
                           (support/suite-view manifest suite)
                           manifest)
                 context (support/new-context
                          {:suite (when (= :suite mode) suite)
                           :manifest manifest
                           :progress-path progress-path
                           :timeout-ms support/default-timeout-ms
                           ;; A fresh focused process proves absence. Capturing this
                           ;; state prevents a prior consumer load being blamed on
                           ;; bootstrap when a process hosts multiple invocations.
                           :optional-namespaces
                           {'malli.core (boolean (find-ns 'malli.core))
                            'hegel.malli (boolean (find-ns 'hegel.malli))}})]
             (support/reset-progress! context)
             (doseq [{:keys [description entrypoint]} entries]
               (support/scenario! context description
                                  #((resolve-entrypoint entrypoint) context)
                                  exit!))
             {:status (if (zero? (support/failure-count context)) 0 1)
              :mode mode :suite suite :context context :entries entries})))
       (catch Throwable error
         (let [data (ex-data error)
               argument-error? (contains? #{::invalid-arguments ::support/unknown-suite}
                                           (:type data))]
           {:status (if argument-error? 2 1)
            :error (if argument-error? :invalid-arguments :harness-failure)
            :exception error}))))))

(defn -main [& args]
  (let [{:keys [status mode suites context exception optional-namespaces]}
        (run-dispatch! args)]
    (cond
      (= :list mode) (doseq [suite suites] (println (name suite)))
      (= :bootstrap-load-probe mode) (println "bootstrap optional namespaces:" optional-namespaces)
      exception (binding [*out* *err*]
                  (println "test runner error:" (ex-message exception)))
      context (println "Ran jolt-hegel scenarios;"
                       (support/failure-count context) "failures"))
    (flush)
    (System/exit status)))
