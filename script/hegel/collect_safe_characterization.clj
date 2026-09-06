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
  (:require [hegel.abi :as abi]
            [hegel.core :as h]
            [hegel.ffi :as hffi]
            [hegel.ffi.jolt :as hffi-jolt]
            [hegel.stateful :as stateful]
            [jolt.ffi :as ffi]))

;; Requiring jolt.ffi directly makes this script fail closed on any runtime
;; where the namespace does not exist (e.g. plain JVM Clojure).
(ffi/load-library)

(def ^:private ordinary-fn (ffi/foreign-fn "getpid" [] :int))
(def ^:private blocking-fn (ffi/foreign-fn "getpid" [] :int :blocking))

(def ^:private warmup-calls 2000)
(def ^:private call-count 20000)
(def ^:private sample-count 5)

(defn- warm! [f]
  (dotimes [_ warmup-calls] (f)))

(defn- call-n!
  "Call f exactly n times, returning elapsed nanoseconds, the last return
  value observed, and the number of calls actually made."
  [f n]
  (let [return* (volatile! nil)
        calls* (volatile! 0)
        start (System/nanoTime)]
    (dotimes [_ n]
      (vreset! return* (f))
      (vswap! calls* inc))
    {:elapsed-ns (- (System/nanoTime) start)
     :return @return*
     :calls @calls*}))

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
      (/ (+ (nth sorted (dec mid)) (nth sorted mid)) 2))))

(defn- check-samples! [samples]
  (when-not (every? #(= call-count (get-in % [:ordinary :calls])) samples)
    (throw (ex-info "ordinary binding did not complete the fixed call count"
                    {:expected call-count})))
  (when-not (every? #(= call-count (get-in % [:blocking :calls])) samples)
    (throw (ex-info "blocking binding did not complete the fixed call count"
                    {:expected call-count})))
  (let [returns (into #{}
                       (mapcat (fn [s] [(get-in s [:ordinary :return])
                                        (get-in s [:blocking :return])]))
                       samples)]
    (when-not (= 1 (count returns))
      (throw (ex-info "ordinary and blocking bindings returned different values"
                      {:returns returns})))))

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
       :ordinary-ns-median ordinary-median
       :blocking-ns-median blocking-median
       :blocking-ordinary-ratio (double (/ blocking-median ordinary-median))})))

;; --- libhegel state-machine-next-rule comparison -------------------------
;;
;; This is the only section that touches libhegel. It routes exactly one
;; canonical function -- :state-machine-next-rule -- through an ordinary
;; versus a :blocking binding, both derived from the canonical hegel.abi
;; descriptor via hegel.ffi.jolt's own native-type constructor rather than a
;; hand-copied argument/return signature. The workload driving both routes is
;; hegel.core/run-test! wrapping a single hegel.stateful/run! state machine
;; with one always-applicable rule, run to a fixed :stateful-step-count with a
;; fixed seed so both routes see the identical deterministic, successful run.

(def ^:private state-machine-next-rule-descriptor
  (get (abi/functions) :state-machine-next-rule))

(def ^:private state-machine-next-rule-arg-types
  (mapv #(hffi-jolt/native-type % (abi/descriptor))
        (:args state-machine-next-rule-descriptor)))

(def ^:private state-machine-next-rule-return-type
  (hffi-jolt/native-type (:return state-machine-next-rule-descriptor)
                          (abi/descriptor)))

;; The library is already loaded (requiring hegel.ffi loads it), so this is
;; the same underlying symbol as hffi/c-state-machine-next-rule, bound a
;; second time with :blocking. Built the same way hegel.ffi.jolt's own
;; constructor builds every binding: jolt.ffi/foreign-fn needs literal
;; argument-type/return forms, so the derived signature is spliced into a
;; quoted form and evaluated once.
(def ^:private ordinary-state-machine-next-rule hffi/c-state-machine-next-rule)

(def ^:private blocking-state-machine-next-rule
  (eval (list 'jolt.ffi/foreign-fn
              (:symbol state-machine-next-rule-descriptor)
              state-machine-next-rule-arg-types
              state-machine-next-rule-return-type
              :blocking)))

(def ^:private state-machine-test-cases 5)
(def ^:private state-machine-step-count 1000)
(def ^:private state-machine-seed 424242)
(def ^:private state-machine-sample-count 5)

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
                        :n-failures :final]))

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
                      {:route route :samples samples}))))
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
       :ordinary-ns-median ordinary-median
       :blocking-ns-median blocking-median
       :blocking-ordinary-ratio (double (/ blocking-median ordinary-median))})))

(defn collect-all!
  "Run both bounded measurements and return one EDN-safe report map."
  []
  {:getpid (collect!)
   :state-machine-next-rule (collect-state-machine-next-rule!)})

(defn -main [& _]
  (prn (collect-all!))
  (flush))
