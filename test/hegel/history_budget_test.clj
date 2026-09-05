(ns hegel.history-budget-test
  (:require [clojure.test :refer [deftest is]]
            [hegel.core :as h]
            [hegel.history :as history]
            [hegel.history-oracle :as oracle]
            [hegel.stateful :as stateful]
            [hegel.trace :as trace]))

(defn- error-of [thunk]
  (try (thunk) nil (catch Throwable error error)))

(defn- register-step [state operation]
  (case (:operation operation)
    :write (when (and (= :return (:outcome operation)) (= :ok (:value operation)))
             {:state (:input operation)})
    :read (when (= state (:value operation)) {:state state})
    nil))

(defn- one-write [id partition start]
  [{:seq start :operation-id id :phase :invoke :operation :write :input 1 :partition partition}
   {:seq (inc start) :operation-id id :phase :return :value :ok :partition partition}])

(def ^:private exhausted-inner-events (one-write :inner nil 0))

(defn- exhaust-inner! []
  (history/check! 0 register-step exhausted-inner-events {:max-search-steps 0}))

(deftest analyze-has-a-decisive-empty-zero-boundary
  (let [analysis (history/analyze 0 register-step [] {:max-search-steps 0})]
    (is (= :linearizable (:status analysis)))
    (is (= [] (-> analysis :witness :order)))
    (is (zero? (-> analysis :search :search-steps)))))

(deftest single-legal-operation-has-exact-zero-one-budget-boundaries
  (let [events (one-write :write nil 0)
        exhausted (history/analyze 0 register-step events {:max-search-steps 0})
        decisive (history/analyze 0 register-step events {:max-search-steps 1})]
    (is (= :inconclusive (:status exhausted)))
    (is (zero? (-> exhausted :search :search-steps)))
    (is (= :linearizable (:status decisive)))
    (is (= 1 (-> decisive :search :search-steps)))
    (is (= [:write] (-> decisive :witness :order)))))

(deftest global-candidate-budget-is-deterministic
  (let [events (vec (concat (one-write :left :left 0)
                            (one-write :right :right 2)))
        opts {:partition-by #(-> % :invoke :partition) :max-search-steps 1}
        first-result (history/analyze 0 register-step events opts)
        second-result (history/analyze 0 register-step events opts)]
    (is (= :inconclusive (:status first-result)))
    (is (= first-result second-result))
    (is (= 1 (-> first-result :search :search-steps)))
    (is (= 2 (-> first-result :search :partition-count)))))

(deftest blocked-candidates-consume-budget-before-model-evaluation
  (let [calls (atom 0)
        step (fn [state operation]
               (swap! calls inc)
               (when (= :right (:operation-id operation)) {:state state}))
        events (vec (concat (one-write :left nil 0)
                            (one-write :right nil 2)))
        analysis (history/analyze nil step events {:max-search-steps 2})]
    (is (= :not-linearizable (:status analysis)))
    (is (= 2 (-> analysis :search :search-steps)))
    (is (= 1 @calls))))

(deftest overlap-backtracking-has-an-exact-sufficient-budget
  (let [events [{:seq 0 :operation-id :write :phase :invoke :operation :write :input 1}
                {:seq 1 :operation-id :read :phase :invoke :operation :read}
                {:seq 2 :operation-id :write :phase :return :value :ok}
                {:seq 3 :operation-id :read :phase :return :value 0}]
        exhausted (history/analyze 0 register-step events {:max-search-steps 3})
        analysis (history/analyze 0 register-step events {:max-search-steps 4})]
    (is (= :inconclusive (:status exhausted)))
    (is (= 3 (-> exhausted :search :search-steps)))
    (is (= :linearizable (:status analysis)))
    (is (= 4 (-> analysis :search :search-steps)))
    (is (= [:read :write] (-> analysis :witness :order)))))

(deftest budgeted-search-agrees-with-the-independent-canonical-oracle
  (is (every? second (oracle/checks))))

(deftest partitions-ignore-cross-partition-precedence
  (let [events (vec (concat (one-write :left :left 0)
                            (one-write :right :right 2)))
        analysis (history/analyze nil
                                  (fn [state _] {:state state})
                                  events
                                  {:partition-by #(-> % :invoke :partition)
                                   :max-search-steps 2})]
    (is (= :linearizable (:status analysis)))
    (is (= 2 (-> analysis :search :search-steps)))))

(deftest search-indexes-do-not-leak-through-the-public-operation-shape
  (let [events (one-write :write :key 0)
        expected-operations (history/operations events)
        seen-model (atom nil)
        seen-partition (atom nil)
        analysis (history/analyze 0
                                  (fn [state operation]
                                    (reset! seen-model operation)
                                    {:state state})
                                  events
                                  {:partition-by (fn [operation]
                                                   (reset! seen-partition operation)
                                                   :key)})]
    (is (= :linearizable (:status analysis)))
    (is (= (first expected-operations) @seen-model))
    (is (= (first expected-operations) @seen-partition))
    (is (= expected-operations
           (-> analysis :witness :partitions first :operations)))))

(deftest exhaustion-never-becomes-a-legacy-negative-verdict
  (let [events (one-write :write nil 0)
        opts {:max-search-steps 0}
        linearization-error (error-of #(history/linearization 0 register-step events opts))
        boolean-error (error-of #(history/linearizable? 0 register-step events opts))
        check-error (error-of #(history/check! 0 register-step events opts))
        named-check-error
        (error-of #(history/check! 0 register-step events
                                  {:name :budgeted-rule :max-search-steps 0}))]
    (doseq [error [linearization-error boolean-error check-error]]
      (is (= ::history/search-exhausted (:type (ex-data error))))
      (is (true? (:hegel/inconclusive? (ex-data error))))
      (is (not= ::history/not-linearizable (:type (ex-data error)))))
    (is (= "hegel.history/budgeted-rule" (:hegel/origin (ex-data named-check-error))))
    (is (= events (:hegel.history/events (ex-data named-check-error))))
    (is (= 2 (:hegel.history/event-count (ex-data named-check-error))))
    (is (false? (:hegel.history/evidence-truncated? (ex-data named-check-error))))))

(deftest trace-and-core-propagate-inconclusive-history-unchanged
  (let [events (one-write :write nil 0)
        rule (history/rule :budgeted
                           {:initial 0 :step register-step :max-search-steps 0})
        trace-error (error-of #(trace/check! events [rule]))
        run-error (error-of #(h/run-test! {:test-cases 1 :seed 1 :database "" :verbosity :quiet}
                                           (fn [_] (trace/check! events [rule]))))]
    (doseq [error [trace-error run-error]]
      (is (= ::history/search-exhausted (:type (ex-data error))))
      (is (true? (:hegel/inconclusive? (ex-data error)))))
    (let [ordinary (error-of #(trace/check! [] [(trace/rule :false (constantly false))]))]
      (is (= ::trace/rule-failed (:type (ex-data ordinary)))))))

(deftest nested-model-and-partition-callbacks-preserve-inconclusive-errors
  (let [events (one-write :outer :key 0)
        model-error (error-of #(history/analyze 0
                                                (fn [_ _] (exhaust-inner!))
                                                events
                                                {:max-search-steps 1}))
        partition-error (error-of #(history/analyze 0 register-step events
                                                    {:partition-by (fn [_] (exhaust-inner!))
                                                     :max-search-steps 1}))]
    (doseq [error [model-error partition-error]]
      (is (= ::history/search-exhausted (:type (ex-data error))))
      (is (true? (:hegel/inconclusive? (ex-data error))))
      (is (not (contains? #{::history/model-error ::history/partition-error}
                          (:type (ex-data error))))))))

(deftest stateful-rules-preserve-nested-inconclusive-history-errors
  (let [error (error-of #(h/run-test!
                           {:test-cases 1 :seed 1 :database "" :verbosity :quiet}
                           (fn [_]
                             (stateful/run!
                              {:initial-state 0
                               :rules [(stateful/rule :nested-history
                                                      (fn [_] (exhaust-inner!)))]}))))]
    (is (= ::history/search-exhausted (:type (ex-data error))))
    (is (true? (:hegel/inconclusive? (ex-data error))))))

(deftest stateful-preconditions-and-invariants-preserve-the-identical-marker
  (let [marker (ex-info "bounded nested history" {:hegel/inconclusive? true})
        opts {:test-cases 1 :seed 1 :database "" :verbosity :quiet}
        precondition-error
        (error-of #(h/run-test!
                    opts
                    (fn [_]
                      (stateful/run!
                       {:initial-state 0
                        :rules [(stateful/rule :precondition
                                               {:precondition (fn [_] (throw marker))}
                                               identity)]}))))
        invariant-error
        (error-of #(h/run-test!
                    opts
                    (fn [_]
                      (stateful/run!
                       {:initial-state 0
                        :rules [(stateful/rule :no-op identity)]
                        :invariants [(stateful/invariant :initial
                                                        (fn [_] (throw marker)))]}))))]
    (is (identical? marker precondition-error))
    (is (identical? marker invariant-error))))
