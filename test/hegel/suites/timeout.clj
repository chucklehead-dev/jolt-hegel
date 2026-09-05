(ns hegel.suites.timeout
  "Terminal-timeout supervision and child entrypoints, loaded on demand."
  (:require [clojure.string :as str]
            [hegel.host :as host]
            [hegel.native :as native]
            [hegel.test-support :as support]))

(def ^:private child-deadline-ms 15000)
(def ^:private child-reap-ms 5000)
(def ^:private timeout-regression-deadline-ms 45000)
(def ^:private nanos-per-millisecond 1000000)

(defn- child-command [mode]
  (case (host/runtime)
    :bb ["bb" "test" mode]
    ;; setup-clojure can expose `clojure` as a PowerShell function on Windows,
    ;; but ProcessBuilder requires an executable. The parent already has the
    ;; resolved project classpath, so launch its JDK directly.
    :jvm [(.getPath (java.io.File.
                      (System/getProperty "java.home")
                      (if (= :windows (:os (native/platform)))
                        "bin/java.exe"
                        "bin/java")))
          "--enable-native-access=ALL-UNNAMED"
          "-cp" (System/getProperty "java.class.path")
          "clojure.main" "-m" "hegel.test-runner" mode]
    :jolt ["jolt" "-M:test" mode]
    (throw (ex-info "timeout regression has no child launcher for this runtime"
                    {:runtime (host/runtime)}))))

(defn- remaining-nanos [deadline]
  (- deadline (System/nanoTime)))

(defn- bounded-wait-ms [deadline cap-ms]
  (let [remaining (remaining-nanos deadline)]
    (when (pos? remaining)
      ;; Timed deref takes milliseconds. Retain a positive sub-millisecond
      ;; remainder rather than treating it as an already-expired deadline.
      (min cap-ms (max 1 (quot remaining nanos-per-millisecond))))))

(defn- read-redirected-output [output-file]
  (try
    {:output (slurp output-file)}
    (catch Throwable error
      ;; Preserve the child's command/exit result for the parent diagnostic;
      ;; a missing or unreadable transcript must not conceal it.
      {:output nil
       :output-error (str (ex-message error))})))

(defn- read-redirected-error [error-file]
  (when error-file
    (try
      {:error-output (slurp error-file)}
      (catch Throwable error
        {:error-output nil
         :error-output-error (str (ex-message error))}))))

(defn- windows-jolt-child? []
  (and (= :jolt (host/runtime))
       (= :windows (:os (native/platform)))))

(defn- windows-jolt-child-run! [mode deadline output-file]
  ;; The released Jolt 0.8.1 ProcessBuilder host shim invokes /bin/sh even on
  ;; Windows, so ProcessBuilder cannot supervise a nested Jolt there. Keep the
  ;; workaround entirely in the test harness and use PowerShell's native
  ;; process boundary with the same monotonic aggregate allowance. `system`
  ;; itself is synchronous, so the outer 60s terminal scenario remains the
  ;; fail-closed bound if PowerShell cannot start at all.
  (let [wait-ms (bounded-wait-ms deadline child-deadline-ms)
        reap-ms (when wait-ms
                  (let [remaining-after-wait
                        (- (remaining-nanos deadline)
                           (* wait-ms nanos-per-millisecond))]
                    (max 0 (min child-reap-ms
                                (quot (max 0 remaining-after-wait)
                                      nanos-per-millisecond)))))]
    (if wait-ms
      (let [result ((requiring-resolve 'hegel.timeout-windows/run-child!)
                    ["jolt.exe" "-M:test" mode] output-file wait-ms reap-ms)]
        (merge result
               (read-redirected-output output-file)
               (read-redirected-error (:error-file result))))
      {:command ["jolt.exe" "-M:test" mode]
       :skipped? true
       :reason :aggregate-deadline-exhausted})))

(defn- child-run! [context mode deadline]
  (if-not (pos? (remaining-nanos deadline))
    {:command (child-command mode)
     :skipped? true
     :reason :aggregate-deadline-exhausted}
    (let [output-file (str (:progress-path context) ".child-"
                           (random-uuid) ".log")]
      (if (windows-jolt-child?)
        (windows-jolt-child-run! mode deadline output-file)
        (let [process (.start (doto (ProcessBuilder.
                                     (into-array String (child-command mode)))
                                (.redirectErrorStream true)
                                ;; Jolt's ProcessBuilder shim accepts the explicit
                                ;; Redirect descriptor, while JVM Clojure also
                                ;; accepts it. Passing File selects a JVM overload
                                ;; that the shim cannot represent.
                                (.redirectOutput
                                 (java.lang.ProcessBuilder$Redirect/to
                                  (java.io.File. output-file)))))
              wait-ms (bounded-wait-ms deadline child-deadline-ms)
              waited (if wait-ms
                       (deref (future (.waitFor process)) wait-ms ::timeout)
                       ::timeout)
              forced? (= ::timeout waited)
              exit (if forced?
                     (do
                       ;; This is a parent-process fallback only. The child harness
                       ;; must exit itself on timeout; cancellation is not treated
                       ;; as native termination.
                       (.destroyForcibly process)
                       (if-let [reap-ms (bounded-wait-ms deadline child-reap-ms)]
                         (deref (future (.waitFor process)) reap-ms ::unreaped)
                         ::unreaped))
                     waited)
              result {:command (child-command mode)
                      :forced? forced?
                      :exit exit
                      :output-file output-file}]
          ;; Do not read a pipe after an unreaped process: its EOF may never come.
          ;; The redirected output remains beside the progress transcript.
          (if (= ::unreaped exit)
            result
            (merge result (read-redirected-output output-file))))))))

(defn- child-progress-path [output]
  (when (string? output)
    (some (fn [line]
            (when (str/starts-with? line "HEGEL_TIMEOUT_PROGRESS ")
              (subs line (count "HEGEL_TIMEOUT_PROGRESS "))))
          (str/split-lines output))))

(defn- child-transcript [result]
  (when-let [path (child-progress-path (:output result))]
    (when (.exists (java.io.File. path))
      (try
        (slurp path)
        (catch Throwable _ nil)))))

(defn terminal-timeout-regression [context]
  ;; Bound the entire regression rather than each child independently, using a
  ;; monotonic deadline: a launch failure must not turn three 15s+5s child
  ;; paths into a minute-long suite stall or be distorted by a wall-clock jump.
  (let [deadline (+ (System/nanoTime)
                    (* timeout-regression-deadline-ms nanos-per-millisecond))
        identity-a (child-run! context "--progress-identity-child" deadline)
        identity-b (child-run! context "--progress-identity-child" deadline)
        timed-out (child-run! context "--terminal-timeout-child" deadline)
        path-a (child-progress-path (:output identity-a))
        path-b (child-progress-path (:output identity-b))
        timeout-path (child-progress-path (:output timed-out))
        timeout-log (child-transcript timed-out)
        identities-ok? (and (not (:forced? identity-a))
                            (not (:forced? identity-b))
                            (= 0 (:exit identity-a))
                            (= 0 (:exit identity-b))
                            (string? path-a)
                            (string? path-b)
                            (not= path-a path-b)
                            (string? (child-transcript identity-a))
                            (string? (child-transcript identity-b)))
        timeout-ok? (and (not (:forced? timed-out))
                         (= 1 (:exit timed-out))
                         (string? timeout-path)
                         (string? timeout-log)
                         (str/includes? timeout-log
                                        "START controlled terminal timeout")
                         (str/includes? timeout-log "BODY ENTERED")
                         (str/includes? timeout-log
                                        "TIMEOUT controlled terminal timeout")
                         (not (str/includes? timeout-log "END controlled terminal timeout"))
                         (not (str/includes? timeout-log "SENTINEL")))]
    (support/check! context "default progress artifacts are distinct across runner invocations"
           identities-ok?)
    (support/check! context "timeout child aborts after entering its body without a later sentinel"
           timeout-ok?)
    (when-not (and identities-ok? timeout-ok?)
      ;; Retain child progress artifacts for diagnosis; print the captured
      ;; command/output as well so a failed CI job preserves both transcripts.
      (println "timeout regression child results"
               (pr-str {:identity-a identity-a
                        :identity-b identity-b
                        :timed-out timed-out
                        :timeout-progress timeout-log})))))

(defn timeout-regression-child!
  ([context] (timeout-regression-child! context System/exit))
  ([context exit!]
  (support/reset-progress! context)
  (println "HEGEL_TIMEOUT_PROGRESS" (:progress-path context))
  (flush)
  (support/scenario! (assoc context :timeout-ms 100) "controlled terminal timeout"
              #(do
                 (support/progress! context "BODY ENTERED")
                 (flush)
                 (deref (promise)))
              exit!)
  ;; If a timeout ever resumes execution, this marker makes the regression
  ;; fail rather than treating a dropped future as isolation.
  (support/progress! context "SENTINEL")))

(defn progress-identity-child!
  ([context] (progress-identity-child! context System/exit))
  ([context _exit!]
   (support/reset-progress! context)
   (println "HEGEL_TIMEOUT_PROGRESS" (:progress-path context))
   (flush)
   (support/progress! context "IDENTITY")))
