(ns hegel.collect-safe-characterization
  "Dev-only bounded measurement of the jolt.ffi native boundary cost.

  Two independent comparisons run, each binding the same native function
  twice -- once ordinary and once :blocking -- and reporting raw timings.
  This is a cost characterization, not a benchmark with a pass/fail
  threshold.

  1. getpid: a harmless no-argument integer-returning process function.
  2. hegel_state_machine_next_rule: libhegel's real per-step draw call,
     exercised by running the same deterministic, successful, one-rule
     hegel.core/run-test! + hegel.stateful/run! workload through an ordinary
     versus a :blocking binding of that one function, with everything else
     about the workload held fixed."
  (:require [hegel.core :as h]
            [hegel.ffi :as hffi]
            [hegel.stateful :as stateful]
            [jolt.ffi :as ffi]))

;; Requiring jolt.ffi directly makes this script fail closed on any runtime
;; where the namespace does not exist (e.g. plain JVM Clojure).
;; hegel.ffi is required above and loads libhegel first; this subsequent
;; process-symbol load keeps getpid available without replacing that handle.
(ffi/load-library)

(def ^:private ordinary-fn (ffi/foreign-fn "getpid" [] :int))
(def ^:private blocking-fn (ffi/foreign-fn "getpid" [] :int :blocking))

(def ^:private warmup-calls 2000)
(def ^:private call-count 20000)
(def ^:private sample-count 6)

(defn- warm! [f]
  (dotimes [_ warmup-calls] (f)))

(defn- call-n!
  "Call f exactly n times, returning elapsed nanoseconds and the last return
  value observed. Normal return from dotimes establishes completion."
  [f n]
  (let [return* (volatile! nil)
        start (System/nanoTime)]
    (dotimes [_ n]
      (vreset! return* (f)))
    {:elapsed-ns (- (System/nanoTime) start)
     :return @return*}))

(defn- sample
  "Measure one ordinary/blocking pair, alternating call order by index to
  avoid systematic bias from whichever function runs first."
  [index]
  (let [ordinary-first? (even? index)
        first-key (if ordinary-first? :ordinary :blocking)
        second-key (if ordinary-first? :blocking :ordinary)
        first-fn (if ordinary-first? ordinary-fn blocking-fn)
        second-fn (if ordinary-first? blocking-fn ordinary-fn)
        first-result (call-n! first-fn call-count)
        second-result (call-n! second-fn call-count)]
    {:order [first-key second-key]
     first-key first-result
     second-key second-result}))

(defn- median [values]
  (let [sorted (vec (sort values))
        n (count sorted)
        mid (quot n 2)]
    (if (odd? n)
      (nth sorted mid)
      (/ (+ (double (nth sorted (dec mid)))
            (double (nth sorted mid)))
         2.0))))

(defn- check-samples! [samples]
  (let [returns (into #{}
                       (mapcat (fn [s] [(get-in s [:ordinary :return])
                                        (get-in s [:blocking :return])]))
                       samples)]
    (when-not (= 1 (count returns))
      (throw (ex-info "ordinary and blocking bindings returned different values"
                      {:returns returns})))
    (when-not (pos? (first returns))
      (throw (ex-info "getpid returned a non-positive process id"
                      {:return (first returns)})))))

(defn- paired-ratios [samples]
  (mapv (fn [sample]
          (double (/ (get-in sample [:blocking :elapsed-ns])
                     (get-in sample [:ordinary :elapsed-ns]))))
        samples))

(defn collect!
  "Run the bounded measurement and return one EDN-safe report map."
  []
  (warm! ordinary-fn)
  (warm! blocking-fn)
  (let [samples (mapv sample (range sample-count))]
    (check-samples! samples)
    (let [ordinary-ns (mapv #(get-in % [:ordinary :elapsed-ns]) samples)
          blocking-ns (mapv #(get-in % [:blocking :elapsed-ns]) samples)
          ordinary-median (median ordinary-ns)
          blocking-median (median blocking-ns)]
      {:function "getpid"
       :warmup-calls warmup-calls
       :call-count call-count
       :sample-count sample-count
       :samples samples
       :ordinary-ns-raw ordinary-ns
       :blocking-ns-raw blocking-ns
       :paired-blocking-ordinary-ratios (paired-ratios samples)
       :ordinary-ns-median ordinary-median
       :blocking-ns-median blocking-median
       :median-blocking-ordinary-ratio
       (double (/ blocking-median ordinary-median))})))

;; --- libhegel state-machine-next-rule comparison -------------------------
;;
;; This section reuses the production route selector. The alternate is not
;; rebound here: production owns descriptor validation, signature derivation,
;; and Jolt's literal-form `foreign-fn` construction.
(def ^:private ordinary-state-machine-next-rule hffi/c-state-machine-next-rule)
(def ^:private blocking-state-machine-next-rule
  hffi/c-state-machine-next-rule-collect-safe)

(when (identical? ordinary-state-machine-next-rule
                  blocking-state-machine-next-rule)
  (throw (ex-info "ordinary and blocking state-machine bindings are identical"
                  {:function :state-machine-next-rule})))

(def ^:private state-machine-test-cases 5)
(def ^:private state-machine-step-count 1000)
(def ^:private state-machine-seed 424242)
(def ^:private state-machine-sample-count 6)

(def ^:private workload-options
  {:test-cases state-machine-test-cases
   :stateful-step-count state-machine-step-count
   :seed state-machine-seed
   :database ""
   :verbosity :quiet
   :report-multiple-failures? false
   ;; The :blocking route is deliberately slower per call; suppress the
   ;; unrelated "too slow" health check rather than letting it fail a route.
   :suppress-health-checks [:too-slow]})

(defn- one-rule-state-machine-config []
  {:initial-state 0
   :rules [(stateful/rule :increment inc)]})

(defn- run-state-machine-workload! []
  (h/run-test! workload-options
               (fn [_test-case]
                 (stateful/run! (one-rule-state-machine-config)))))

(defn- counting-fn
  "Wrap f so every actual call is counted. Returns [wrapped count-atom]."
  [f]
  (let [calls (atom 0)]
    [(fn [& args]
       (swap! calls inc)
       (apply f args))
     calls]))

(defn- semantic-summary
  "The parts of a run-test! result that must agree between routes: everything
  except this diagnostic's own concerns (timing is out of scope here)."
  [result]
  (select-keys result [:passed? :status :seed :flaky?
                        :test-cases :valid-test-cases :invalid-test-cases
                        :overrun-test-cases :interesting-test-cases
                        :n-failures]))

(defn- run-state-machine-route!
  "Run the workload once with raw-fn standing in for
  hffi/c-state-machine-next-rule for the duration of the call, timing the
  whole workload and counting actual calls to raw-fn."
  [raw-fn]
  (let [[wrapped calls] (counting-fn raw-fn)]
    (with-redefs [hffi/c-state-machine-next-rule wrapped]
      (let [start (System/nanoTime)
            result (run-state-machine-workload!)]
        {:elapsed-ns (- (System/nanoTime) start)
         :summary (semantic-summary result)
         :calls @calls}))))

(defn- state-machine-warm! [raw-fn]
  (run-state-machine-route! raw-fn))

(defn- state-machine-sample
  "Measure one ordinary/blocking pair, alternating call order by index to
  avoid systematic bias from whichever route runs first."
  [index]
  (let [ordinary-first? (even? index)
        first-key (if ordinary-first? :ordinary :blocking)
        second-key (if ordinary-first? :blocking :ordinary)
        first-fn (if ordinary-first?
                   ordinary-state-machine-next-rule
                   blocking-state-machine-next-rule)
        second-fn (if ordinary-first?
                    blocking-state-machine-next-rule
                    ordinary-state-machine-next-rule)
        first-result (run-state-machine-route! first-fn)
        second-result (run-state-machine-route! second-fn)]
    {:order [first-key second-key]
     first-key first-result
     second-key second-result}))

(defn- check-state-machine-samples! [samples]
  (doseq [route [:ordinary :blocking]]
    (when-not (every? #(get-in % [route :summary :passed?]) samples)
      (throw (ex-info (str "state-machine-next-rule " (name route)
                           " route did not pass")
                      {:route route
                       :passed (mapv #(get-in % [route :summary :passed?])
                                     samples)}))))
  (let [summaries (into #{}
                         (mapcat (fn [s] [(get-in s [:ordinary :summary])
                                          (get-in s [:blocking :summary])]))
                         samples)]
    (when-not (= 1 (count summaries))
      (throw (ex-info
              "ordinary and blocking state-machine-next-rule routes produced different semantic summaries"
              {:summaries summaries}))))
  (let [call-counts (into #{}
                           (mapcat (fn [s] [(get-in s [:ordinary :calls])
                                            (get-in s [:blocking :calls])]))
                           samples)]
    (when-not (= 1 (count call-counts))
      (throw (ex-info
              "ordinary and blocking state-machine-next-rule routes made different actual call counts"
              {:call-counts call-counts})))
    (when-not (pos? (first call-counts))
      (throw (ex-info "state-machine-next-rule was never actually called"
                      {:call-counts call-counts})))))

(defn collect-state-machine-next-rule!
  "Run the bounded libhegel state-machine-next-rule measurement and return
  one EDN-safe report map."
  []
  (state-machine-warm! ordinary-state-machine-next-rule)
  (state-machine-warm! blocking-state-machine-next-rule)
  (let [samples (mapv state-machine-sample (range state-machine-sample-count))]
    (check-state-machine-samples! samples)
    (let [ordinary-ns (mapv #(get-in % [:ordinary :elapsed-ns]) samples)
          blocking-ns (mapv #(get-in % [:blocking :elapsed-ns]) samples)
          ordinary-median (median ordinary-ns)
          blocking-median (median blocking-ns)]
      {:function "state-machine-next-rule"
       :workload-options workload-options
       :sample-count state-machine-sample-count
       :actual-call-count (get-in (first samples) [:ordinary :calls])
       :ordinary-ns-raw ordinary-ns
       :blocking-ns-raw blocking-ns
       :paired-workload-blocking-ordinary-ratios (paired-ratios samples)
       :ordinary-ns-median ordinary-median
       :blocking-ns-median blocking-median
       :ordinary-workload-ns-per-next-rule-call
       (double (/ ordinary-median (get-in (first samples) [:ordinary :calls])))
       :blocking-workload-ns-per-next-rule-call
       (double (/ blocking-median (get-in (first samples) [:blocking :calls])))
       :workload-median-blocking-ordinary-ratio
       (double (/ blocking-median ordinary-median))})))

;; --- two-worker native contention characterization -----------------------
;;
;; This is intentionally a development characterization, not the #24 public
;; API. Jolt futures run on distinct host threads; each gets a clone and its
;; own libhegel context because contexts themselves are not thread-safe. The
;; root handle stays on the coordinator for next-group. Workers use only the
;; four production collect-safe helpers and report each selected rule as
;; rejected, avoiding a user rule body while still driving libhegel's native
;; per-worker protocol.

(def ^:private process-watchdog-timeout-ms 30000)
(def ^:private configured-contention-rounds 4)
(def ^:private worker-round-limit 2048)

(defn- route-helpers [route]
  (case route
    :collect-safe
    {:state-machine-next-rule hffi/state-machine-next-rule-collect-safe!
     :state-machine-rule-rejected hffi/state-machine-rule-rejected-collect-safe!
     :pool-add hffi/pool-add-collect-safe!
     :pool-generate hffi/pool-generate-collect-safe!}

    :ordinary
    {:state-machine-next-rule hffi/state-machine-next-rule!
     :state-machine-rule-rejected hffi/state-machine-rule-rejected!
     :pool-add hffi/pool-add!
     :pool-generate hffi/pool-generate!}

    (throw (ex-info "unknown two-worker characterization route"
                    {:route route
                     :supported-routes [:collect-safe :ordinary]}))))

(defn- worker-result [route helpers worker-index start entered clone state-machine pool]
  (let [ctx (hffi/context-new!)]
    (try
      (if (= ::abort @start)
        {:status :aborted :worker-index worker-index}
        (do
          ;; This signal is emitted immediately before the first selected-route
          ;; call. The coordinator starts its GC probe only after both workers
          ;; have reached this point; OS scheduling can still run GC before a
          ;; particular native instruction, so this is progress evidence, not
          ;; proof of simultaneous lock ownership.
          (deliver entered worker-index)
          (loop [calls {:state-machine-next-rule 0
                        :state-machine-rule-rejected 0
                        :pool-add 0
                        :pool-generate 0}]
            (when (>= (:state-machine-next-rule calls) worker-round-limit)
              (throw (ex-info "two-worker characterization exceeded its bounded rule loop"
                              {:worker-index worker-index
                               :calls calls
                               :limit worker-round-limit})))
            (if-some [_rule-index
                      ((:state-machine-next-rule helpers)
                       ctx clone state-machine worker-index)]
              (do
                ((:pool-add helpers) ctx clone pool)
                ((:pool-generate helpers) ctx clone pool false)
                ((:state-machine-rule-rejected helpers)
                 ctx clone state-machine worker-index)
                (recur (-> calls
                           (update :state-machine-next-rule inc)
                           (update :pool-add inc)
                           (update :pool-generate inc)
                           (update :state-machine-rule-rejected inc))))
              {:status :ok :route route :worker-index worker-index :calls calls}))))
      (finally
        (hffi/context-free! ctx)))))

(defn- start-worker! [route helpers worker-index ready start entered clone state-machine pool]
  (future
    (try
      (deliver ready worker-index)
      (worker-result route helpers worker-index start entered clone state-machine pool)
      (catch Throwable error
        {:status :error
         :worker-index worker-index
         :message (ex-message error)
         :data (ex-data error)}))))

(defn- await-worker! [worker]
  ;; This runs only in the explicit --child mode. Never turn a possibly live
  ;; native worker into a throwable inside h/run-test!: its outer cleanup owns
  ;; the root test-case and run. A process parent supplies the timeout instead.
  @worker)

(defn- run-two-worker-round! [route reports]
  (let [test-case (h/current-test-case!)
        root-context (:context test-case)
        root-handle (:handle test-case)
        events (atom [])
        helpers (route-helpers route)
        {:keys [state-machine concurrency]}
        (hffi/new-state-machine-with-concurrency!
         root-context root-handle ["left" "right"] [0 0] [] 2 2)]
    (when-not (= 2 concurrency)
      (hffi/state-machine-free! root-context state-machine)
      (throw (ex-info "two-worker characterization did not receive concurrency two"
                      {:concurrency concurrency})))
    (let [pool (atom nil)
          left-clone (atom nil)
          right-clone (atom nil)
          cleanup-safe? (atom true)
          joined? (atom false)
          completed (atom nil)]
      (try
        ;; Stage ownership under this try so a pre-worker allocation or
        ;; coordinator failure releases every handle acquired so far.
        (reset! pool (hffi/new-pool! root-context root-handle))
        (reset! left-clone (hffi/test-case-clone! root-context root-handle))
        (reset! right-clone (hffi/test-case-clone! root-context root-handle))
        ;; Coordinator-only, ordinary route: no worker may call next-group.
        (when-not (some? (hffi/state-machine-next-group!
                          root-context root-handle state-machine))
          (throw (ex-info "two-worker characterization stopped before its first group"
                          {})))
        (let [start (promise)
              left-ready (promise)
              right-ready (promise)
              left-entered (promise)
              right-entered (promise)]
          ;; From the first launch attempt until both futures return, native
          ;; liveness is ambiguous. An exception in this interval must stay in
          ;; the watched child instead of releasing shared handles underneath a
          ;; possibly live worker.
          (reset! cleanup-safe? false)
          (let [left (start-worker! route helpers 0 left-ready start left-entered
                                    @left-clone state-machine @pool)
                right (start-worker! route helpers 1 right-ready start right-entered
                                     @right-clone state-machine @pool)]
            ;; In child mode these waits are deliberately unbounded. A
            ;; host-thread stall leaves the child inside its body, so
            ;; h/run-test! never gets a chance to release a root/run still
            ;; referenced by a worker.
            @left-ready
            @right-ready
            (deliver start :go)
            @left-entered
            @right-entered
            ;; Launch GC only after both workers have crossed the start barrier
            ;; and entered their contended loops. Completion remains evidence
            ;; of progress, not a timing threshold.
            (let [gc (future (System/gc) :gc-completed)
                  left-result (await-worker! left)
                  right-result (await-worker! right)]
              ;; Both futures have returned, so release is now safe. Never free
              ;; a pool or machine after a timeout, where native work may remain.
              (reset! joined? true)
              (reset! cleanup-safe? true)
              (swap! events conj :workers-joined)
              ;; stateful-step-count one bounds this probe to one round; a
              ;; second coordinator call must report completion after both
              ;; workers join.
              (when (some? (hffi/state-machine-next-group!
                            root-context root-handle state-machine))
                (throw (ex-info "bounded two-worker probe unexpectedly opened a second group"
                                {:left left-result :right right-result})))
              (swap! events conj :coordinator-next-group)
              (when-not (and (= :ok (:status left-result))
                             (= :ok (:status right-result))
                             (every? pos?
                                     (mapcat (comp vals :calls)
                                             [left-result right-result])))
                (throw (ex-info "two-worker selected route did not exercise every native operation"
                                {:left left-result :right right-result})))
              (reset! completed
                      {:left left-result
                       :right right-result
                       :route route
                       :gc-result @gc}))))
        (finally
          ;; Join-before-free is a protocol rule, not a collect-safe route.
          ;; Pre-launch failures are safe to clean. After launch, cleanup is
          ;; re-enabled only once both workers return; otherwise remain inside
          ;; the child until its process watchdog intervenes.
          (if @cleanup-safe?
            (do
              (when-let [clone @left-clone]
                (hffi/test-case-free! root-context clone))
              (when-let [clone @right-clone]
                (hffi/test-case-free! root-context clone))
              (when @joined?
                (swap! events conj :clones-freed))
              (when-let [pool-handle @pool]
                (hffi/pool-free! root-context pool-handle))
              (when @joined?
                (swap! events conj :pool-freed))
              (hffi/state-machine-free! root-context state-machine)
              (when @joined?
                (swap! events conj :state-machine-freed)
                (when-let [report @completed]
                  (swap! reports conj (assoc report :events @events)))))
            @(promise)))))))

(defn collect-two-worker-contention!
  "Exercise ROUTE through two real Jolt futures. This function never applies a
  worker timeout: invoke it only from an externally watched child process."
  ([route]
   (route-helpers route)
   (let [reports (atom [])
         result (h/run-test!
                 {:test-cases configured-contention-rounds
                  :stateful-step-count 1
                  :seed 880105
                  :database ""
                  :verbosity :quiet
                  :report-multiple-failures? false
                  :suppress-health-checks [:too-slow]}
                 (fn [_] (run-two-worker-round! route reports)))
         expected-events [:workers-joined :coordinator-next-group
                          :clones-freed :pool-freed :state-machine-freed]
         complete-round?
         (fn [{:keys [left right gc-result events]}]
           (and (= :gc-completed gc-result)
                (= expected-events events)
                (= :ok (:status left))
                (= :ok (:status right))
                (every? pos? (mapcat (comp vals :calls) [left right]))))]
     ;; Collect-safe is the production claim and must fail closed. The
     ;; ordinary child reports its actual completed result; a parent timeout
     ;; is the equally valid observation when it cannot complete.
     (when (and (= :collect-safe route)
                (not (and (:passed? result)
                          (= configured-contention-rounds (count @reports))
                          (= configured-contention-rounds (:valid-test-cases result))
                          (every? complete-round? @reports))))
       (throw (ex-info "two-worker collect-safe characterization did not complete every required round"
                       {:result (semantic-summary result)
                        :round-count (count @reports)
                        :expected-rounds configured-contention-rounds
                        :reports @reports})))
     {:route route
      :function-set ["hegel_state_machine_next_rule"
                     "hegel_state_machine_rule_rejected"
                     "hegel_pool_add"
                     "hegel_pool_generate"]
      :completed-rounds @reports
      :run-summary (semantic-summary result)
      :completed? (and (:passed? result)
                       (= configured-contention-rounds (count @reports))
                       (= configured-contention-rounds (:valid-test-cases result))
                       (every? complete-round? @reports))}))
  ([] (collect-two-worker-contention! :collect-safe)))

;; --- worker-body exception and cancellation characterization --------------
;;
;; This is the #24 prerequisite that the successful native-contention probe
;; above intentionally does not cover. A user-shaped worker body throws only
;; after both workers have entered it. The other worker waits for the published
;; cancellation rather than taking another native pull. There is deliberately
;; no worker timeout: this code runs only in --child mode, where the parent
;; process owns liveness if a native call or worker body never returns.

(def ^:private worker-body-error-type
  ::expected-worker-body-error)

(defn- worker-body-error
  [worker-index]
  (ex-info "expected concurrent worker-body failure"
           {:type worker-body-error-type
            :hegel/origin "hegel.collect-safe-characterization/worker-body"
            :worker-index worker-index}))

(defn- exception-worker-result
  [helpers worker-index expected-error start entered release-body cancelled
   clone state-machine pool]
  (let [calls (atom {:state-machine-next-rule 0
                     :pool-add 0
                     :pool-generate 0})]
    (try
      (let [ctx (hffi/context-new!)]
        (try
          (if (= ::abort @start)
            {:status :aborted :worker-index worker-index :calls @calls}
            (do
              (when-not (some? ((:state-machine-next-rule helpers)
                                ctx clone state-machine worker-index))
                (throw (ex-info "worker body probe stopped before selecting a rule"
                                {:worker-index worker-index})))
              (swap! calls update :state-machine-next-rule inc)
              ((:pool-add helpers) ctx clone pool)
              (swap! calls update :pool-add inc)
              ((:pool-generate helpers) ctx clone pool false)
              (swap! calls update :pool-generate inc)
              ;; Both workers have now entered their real host-language body.
              ;; Scheduling can still choose which native instruction ran last;
              ;; the barrier proves entry, not simultaneous lock ownership.
              (deliver entered worker-index)
              @release-body
              (if (zero? worker-index)
                (throw expected-error)
                ;; Do not take a second native selection after cancellation.
                ;; This promise is deliberately a cooperative protocol model,
                ;; not a claim that an executor cancellation primitive exists.
                ;; The error worker publishes it in its catch path.
                (let [reason @cancelled]
                  {:status :cancelled
                   :worker-index worker-index
                   :cancelled-by (:worker-index reason)
                   :calls @calls}))))
          (finally
            (hffi/context-free! ctx))))
      (catch Throwable error
        (let [failure {:worker-index worker-index
                       :type (or (:type (ex-data error))
                                 :unclassified-worker-error)
                       :origin (:hegel/origin (ex-data error))
                       :message (ex-message error)}]
          ;; A caught setup/native/body error means this worker is no longer
          ;; live. Release the coordinator's entry barrier so it can join both
          ;; completed futures, validate the unexpected result, and clean up
          ;; instead of waiting for the external watchdog.
          (deliver entered worker-index)
          ;; A promise is a first-writer-wins cancellation record. This probe
          ;; deliberately has one expected throwing worker (index zero), but
          ;; preserving an unexpected worker's identity keeps the harness
          ;; fail-closed if that invariant changes.
          (deliver cancelled failure)
          {:status :error
           :worker-index worker-index
           :failure failure
           :error error
           :calls @calls})))))

(defn- start-exception-worker!
  [helpers worker-index expected-error ready start entered release-body cancelled
   clone state-machine pool]
  (future
    (deliver ready worker-index)
    (exception-worker-result helpers worker-index expected-error start entered
                             release-body cancelled clone state-machine pool)))

(defn- run-two-worker-exception-round! [reports]
  (let [test-case (h/current-test-case!)
        root-context (:context test-case)
        root-handle (:handle test-case)
        events (atom [])
        helpers (route-helpers :collect-safe)
        {:keys [state-machine concurrency]}
        (hffi/new-state-machine-with-concurrency!
         root-context root-handle ["left" "right"] [0 0] [] 2 2)]
    (when-not (= 2 concurrency)
      (hffi/state-machine-free! root-context state-machine)
      (throw (ex-info "worker-body probe did not receive concurrency two"
                      {:concurrency concurrency})))
    ;; The first concurrent construction is an assumed-away flip case. A
    ;; successful construction must therefore be on a case that libhegel has
    ;; already stamped nondeterministic, which is the non-replayable result
    ;; path this probe expects below.
    (when-not (hffi/test-case-nondeterministic? root-context root-handle)
      (hffi/state-machine-free! root-context state-machine)
      (throw (ex-info "worker-body probe reached a concurrent machine on a deterministic case"
                      {})))
    (let [pool (atom nil)
          left-clone (atom nil)
          right-clone (atom nil)
          cleanup-safe? (atom true)
          joined? (atom false)
          completed (atom nil)]
      (try
        (reset! pool (hffi/new-pool! root-context root-handle))
        (reset! left-clone (hffi/test-case-clone! root-context root-handle))
        (reset! right-clone (hffi/test-case-clone! root-context root-handle))
        ;; Coordinator-only and ordinary. On the error path it is never called
        ;; again: a cancelled round is not a completed join point.
        (when-not (some? (hffi/state-machine-next-group!
                          root-context root-handle state-machine))
          (throw (ex-info "worker-body probe stopped before its first group" {})))
        (let [start (promise)
              left-ready (promise)
              right-ready (promise)
              left-entered (promise)
              right-entered (promise)
              release-body (promise)
              cancelled (promise)
              expected-error (worker-body-error 0)]
          (reset! cleanup-safe? false)
          (let [left (start-exception-worker! helpers 0 expected-error left-ready start
                                              left-entered release-body cancelled
                                              @left-clone state-machine @pool)
                right (start-exception-worker! helpers 1 expected-error right-ready start
                                               right-entered release-body cancelled
                                               @right-clone state-machine @pool)]
            ;; These waits are intentionally unbounded in the watched child.
            @left-ready
            @right-ready
            (deliver start :go)
            @left-entered
            @right-entered
            (deliver release-body :throw-worker-zero)
            (let [left-result (await-worker! left)
                  right-result (await-worker! right)]
              (reset! joined? true)
              (reset! cleanup-safe? true)
              (swap! events conj :workers-joined)
              (when-not (and (= :error (:status left-result))
                             (identical? expected-error (:error left-result))
                             (= worker-body-error-type
                                (get-in left-result [:failure :type]))
                             (= "hegel.collect-safe-characterization/worker-body"
                                (get-in left-result [:failure :origin]))
                             (= :cancelled (:status right-result))
                             (= 0 (:cancelled-by right-result))
                             (= {:state-machine-next-rule 1
                                 :pool-add 1
                                 :pool-generate 1}
                                (:calls left-result)
                                (:calls right-result)))
                (throw (ex-info "worker-body exception probe did not cancel and join as required"
                                {:left left-result :right right-result})))
              (reset! completed {:left (dissoc left-result :error)
                                 :right right-result
                                 :route :collect-safe
                                 :test-case-nondeterministic? true})
              ;; Throw only after every worker has returned. run-test! records
              ;; the user-shaped failure; libhegel subsequently reports its
              ;; expected non-replayable concurrent-run status.
              (throw (:error left-result)))))
        (finally
          ;; Never re-enter ordinary Hegel cleanup while a worker may still be
          ;; live. The false branch deliberately parks the child for its parent
          ;; watchdog rather than freeing the root test case/run underneath it.
          (if @cleanup-safe?
            (do
              (when-let [clone @left-clone]
                (hffi/test-case-free! root-context clone))
              (when-let [clone @right-clone]
                (hffi/test-case-free! root-context clone))
              (when @joined?
                (swap! events conj :clones-freed))
              (when-let [pool-handle @pool]
                (hffi/pool-free! root-context pool-handle))
              (when @joined?
                (swap! events conj :pool-freed))
              (hffi/state-machine-free! root-context state-machine)
              (when @joined?
                (swap! events conj :state-machine-freed)
                (when-let [report @completed]
                  (swap! reports conj (assoc report :events @events)))))
            @(promise)))))))

(defn collect-two-worker-worker-error!
  "Run the expected worker-body failure probe in an externally watched child.
  It models cooperative peer cancellation and checks exception containment and
  cleanup for that protocol; it does not claim an executor cancellation API.
  The current public run result deliberately rejects concurrent failures."
  []
  (let [reports (atom [])
        run-error
        (try
          (h/run-test!
           {;; The budget counts accepted cases. libhegel's initial
            ;; concurrency flip is assumed away as an extra invalid case, then
            ;; this one accepted case reaches the worker-error body.
            :test-cases 1
            :stateful-step-count 1
            :seed 880106
            :database ""
            :verbosity :quiet
            :report-multiple-failures? false
            :suppress-health-checks [:too-slow]}
           (fn [_] (run-two-worker-exception-round! reports)))
          nil
          (catch Throwable error error))
        expected-events [:workers-joined :clones-freed :pool-freed
                         :state-machine-freed]
        complete?
        (fn [{:keys [left right route events test-case-nondeterministic?]}]
          (and (= :collect-safe route)
               test-case-nondeterministic?
               (= expected-events events)
               (= :error (:status left))
               (= worker-body-error-type (get-in left [:failure :type]))
               (= "hegel.collect-safe-characterization/worker-body"
                  (get-in left [:failure :origin]))
               (= "expected concurrent worker-body failure"
                  (get-in left [:failure :message]))
               (= :cancelled (:status right))
               (= 0 (:cancelled-by right))
               (= {:state-machine-next-rule 1
                   :pool-add 1
                   :pool-generate 1}
                  (:calls left)
                  (:calls right))))]
    (when-not (and (= :hegel.core/unsupported-concurrent-state-machine
                      (:type (ex-data run-error)))
                   (= 1 (count @reports))
                   (every? complete? @reports))
      (throw (ex-info "worker-body exception characterization did not fail closed"
                      {:run-error (when run-error
                                    {:type (:type (ex-data run-error))
                                     :message (ex-message run-error)})
                       :reports @reports})))
    {:status :expected-nonpublic-concurrent-failure
     :run-error {:type (:type (ex-data run-error))
                 :message (ex-message run-error)}
     :completed-rounds @reports}))

(defn collect-all!
  "Run both bounded measurements and return one EDN-safe report map."
  []
  {:getpid (collect!)
   :state-machine-next-rule (collect-state-machine-next-rule!)
   :two-worker-contention (collect-two-worker-contention!)
   :two-worker-worker-error (collect-two-worker-worker-error!)})

(defn- watchdog-guidance []
  {:status :external-watchdog-required
   :timeout-ms process-watchdog-timeout-ms
   :child-arguments ["--child"]
   :reason "run the child under a parent-process timeout; do not bound native workers inside h/run-test!"
   :ordinary-negative-control
   {:status :not-run
    :timeout-ms process-watchdog-timeout-ms
    :child-arguments ["--ordinary-negative-control"]
    :reason "ordinary-route liveness is a watchdog observation; run the explicit child under the parent watchdog"}})

(defn -main [& args]
  (case (vec args)
    [] (prn (watchdog-guidance))
    ["--child"] (prn (collect-all!))
    ["--ordinary-negative-control"]
    (prn (collect-two-worker-contention! :ordinary))
    (throw (ex-info "usage: collect-safe-characterization [--child|--ordinary-negative-control]"
                    {:args args})))
  (flush))
