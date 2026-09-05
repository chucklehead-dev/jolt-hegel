(ns hegel.test-support
  "Host-neutral test harness mechanics. This namespace never loads a suite or
  the runner, so suites can depend on it without bootstrap cycles."
  (:import [java.io File]))

(def default-timeout-ms 60000)

(defn await-result!
  "Run a scenario body and return its result or the harness timeout sentinel.
  Kept injectable in an invocation context so terminal-reporting controls do
  not depend on scheduler timing."
  [body timeout-ms]
  (deref (future (try (body) {:ok? true}
                      (catch Throwable error {:ok? false :error error})))
         timeout-ms ::timeout))

(defn new-context
  "Create state for exactly one runner invocation. Callers may provide a
  progress path and timeout to make controls deterministic."
  [{:keys [suite manifest progress-path timeout-ms optional-namespaces await-result!]
    :or {timeout-ms default-timeout-ms
         await-result! await-result!}}]
  {:suite suite
   :manifest manifest
   :progress-path progress-path
   :optional-namespaces optional-namespaces
   :timeout-ms timeout-ms
   :await-result! await-result!
   :failures (atom 0)})

(defn failure-count [context] @(:failures context))

(defn reset-progress! [{:keys [progress-path]}]
  (when progress-path
    ;; The file is harness-owned. Windows rename does not replace an existing
    ;; destination, hence reset before the first atomic-spit style write.
    (let [file (File. progress-path)]
      (when (and (.exists file) (not (.delete file)))
        (throw (ex-info (str "could not reset test progress file " progress-path)
                        {:path progress-path})))
      (spit progress-path "jolt-hegel test run\n"))))

(defn progress! [{:keys [progress-path]} message]
  (when progress-path
    (spit progress-path (str message "\n") :append true)))

(defn check! [context description condition]
  (if condition
    (println "PASS" description)
    (do
      (swap! (:failures context) inc)
      (println "FAIL" description)))
  condition)

(defn throws?
  "True when the thunk throws. Shared by generator and stateful contracts."
  [f]
  (try (f) false (catch Throwable _ true)))

(defn codepoint-count [value]
  ;; Both Java's regex engine and Jolt's irregex iterate by Unicode code point,
  ;; unlike String/count, which counts UTF-16 code units on the JVM.
  (count (re-seq #"(?s)." value)))

(defn scenario!
  "Run one scenario under the invocation's timeout. `exit!` is injectable for
  focused controls; production runner passes System/exit. A timeout is terminal
  even if progress/reporting itself throws."
  ([context description body] (scenario! context description body System/exit))
  ([context description body exit!]
   (progress! context (str "START " description))
   (let [result ((:await-result! context) body (:timeout-ms context))]
     (cond
       (= ::timeout result)
       (try
         (progress! context (str "TIMEOUT " description))
         (println "FAIL" description "timed out; aborting suite")
         (flush)
         (finally (exit! 1)))

       (:ok? result) (println "SCENARIO" description "completed")
       :else (do
               (swap! (:failures context) inc)
               (println "FAIL" description (ex-message (:error result)))))
     (progress! context (str "END " description)))))

(defn validate-manifest!
  "Reject omitted, duplicate, or reordered stable scenario ids. The reviewed
  fixture is an explicit vector; suite views are filtered from it, never
  reconstructed by namespace load order."
  [fixture manifest]
  (let [ids (mapv :id manifest)]
    (when-not (= fixture manifest)
      (throw (ex-info "scenario manifest differs from reviewed fixture"
                      {:expected fixture :actual manifest})))
    (when-not (= (count ids) (count (set ids)))
      (throw (ex-info "scenario manifest has duplicate ids" {:ids ids})))
    manifest))

(defn suite-view [manifest suite]
  (let [selected (filterv #(= suite (:suite %)) manifest)]
    (when (empty? selected)
      (throw (ex-info "unknown or empty suite" {:type ::unknown-suite
                                                  :suite suite
                                                  :available (sort (set (map :suite manifest)))})))
    selected))
