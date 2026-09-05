(ns hegel.suites.trace-history
  "Trace and bounded-history contract scenarios, loaded only when selected."
  (:require [clojure.test :as t]
            [hegel.core :as h]
            [hegel.generator :as g]
            [hegel.event-contract-test]
            [hegel.canonical-event-contract-test]
            [hegel.history :as hhistory]
            [hegel.history-budget-test]
            [hegel.history-oracle :as horacle]
            [hegel.test-support :as support]
            [hegel.trace :as htrace]))

(defn semantic-trace-rules [context]
  (let [events [{:seq 1 :operation-id 1 :parent-operation-id nil
                 :phase :enter :role :agent/run}
                {:seq 2 :operation-id 2 :parent-operation-id 1
                 :phase :enter :role :agent/model}
                {:seq 3 :operation-id 2 :parent-operation-id 1
                 :phase :return :role :agent/model}
                {:seq 4 :operation-id 1 :parent-operation-id nil
                 :phase :return :role :agent/run}]
        checked (htrace/check!
                 events
                 [(htrace/contiguous-sequence)
                  (htrace/closed-lifecycles)
                  (htrace/synchronous-parentage)
                  (htrace/every-eventually
                   :model-terminates
                   #(and (= :agent/model (:role %))
                         (= :enter (:phase %)))
                   #(contains? #{:return :throw} (:phase %)))])]
    (support/check! context "semantic trace rules accept a complete nested aspect trace"
           (= events checked)))
  (let [events [{:seq 1 :operation-id 1 :parent-operation-id nil
                 :phase :invoke :operation :agent/run}
                {:seq 2 :operation-id 2 :parent-operation-id 1
                 :phase :invoke :operation :agent/model}
                {:seq 3 :operation-id 2 :phase :return}
                {:seq 4 :operation-id 1 :phase :return}]]
    (support/check! context "trace rules accept the canonical history invocation phase"
           (= events
              (htrace/check! events
                             [(htrace/contiguous-sequence)
                              (htrace/closed-lifecycles)
                              (htrace/synchronous-parentage)]))))
  (let [events [{:seq 1 :operation-id :parent :parent-operation-id nil
                 :context-id :request-9 :phase :invoke
                 :operation :agent/run}
                ;; An explicit carrier can outlive the parent's dynamic extent.
                {:seq 2 :operation-id :parent :phase :return}
                {:seq 3 :operation-id :child :parent-operation-id :parent
                 :context-id :request-9 :phase :invoke
                 :operation :agent/model}
                {:seq 4 :operation-id :child :phase :return}]
        rules [(htrace/contiguous-sequence :async-journal-contiguous)
               (htrace/closed-lifecycles :async-lifecycles-close)
               (htrace/causal-parentage :async-parent-invoked-first)
               (htrace/context-coherence :async-context-coherent)]]
    (support/check! context "canonical async histories allow a parent to return before its child"
           (= events (htrace/check! events rules))))
  (let [fixtures
        [{:rule (htrace/causal-parentage :async-parent-invoked-first)
          :events [{:seq 1 :operation-id :child
                    :parent-operation-id :parent
                    :context-id :request-9 :phase :invoke}
                   {:seq 2 :operation-id :parent
                    :parent-operation-id nil
                    :context-id :request-9 :phase :invoke}]}
         {:rule (htrace/context-coherence :async-context-coherent)
          :events [{:seq 1 :operation-id :parent
                    :parent-operation-id nil
                    :context-id :request-9 :phase :invoke}
                   {:seq 2 :operation-id :child
                    :parent-operation-id :parent
                    :context-id :request-10 :phase :invoke}]}]
        failures
        (mapv (fn [{:keys [rule events]}]
                (try
                  (htrace/check! events [rule])
                  nil
                  (catch Throwable error error)))
              fixtures)]
    (support/check! context "async causal and context failures retain their stable rule origins"
           (= ["hegel.trace/async-parent-invoked-first"
               "hegel.trace/async-context-coherent"]
              (mapv #(-> % ex-data :hegel/origin) failures))))
  (let [events [{:seq 1 :operation-id :fetch-a :phase :invoke
                 :causal-links []}
                {:seq 2 :operation-id :fetch-b :phase :invoke
                 :causal-links []}
                {:seq 3 :operation-id :join :phase :invoke
                 :causal-links [:fetch-a :fetch-b]}]]
    (support/check! context "causal links accept canonical fan-in from earlier invocations"
           (= events
              (htrace/check! events
                             [(htrace/causal-links :fan-in-links-valid)]))))
  (let [events [{:seq 1 :operation-id :legacy-parent :phase :enter
                 :causal-links []}
                {:seq 2 :operation-id :legacy-child :phase :enter
                 :causal-links [:legacy-parent]}]]
    (support/check! context "causal links accept legacy enter-phase journal invocations"
           (= events (htrace/check! events [(htrace/causal-links)]))))
  (let [events [{:seq 1 :operation-id 2 :phase :invoke :causal-links []}
                {:seq 2 :operation-id "a" :phase :invoke :causal-links []}
                {:seq 3 :operation-id :b :phase :invoke :causal-links []}
                {:seq 4 :operation-id 'c :phase :invoke :causal-links []}
                {:seq 5 :operation-id :join :phase :invoke
                 :causal-links [2 "a" :b 'c]}]]
    (support/check! context "causal links have one tagged order across portable scalar id types"
           (= events (htrace/check! events [(htrace/causal-links)]))))
  (let [fixtures
        [[{:seq 1 :operation-id :missing :phase :invoke}]
         [{:seq 1 :operation-id :child :phase :invoke
           :causal-links [:later]}
          {:seq 2 :operation-id :later :phase :invoke :causal-links []}]
         [{:seq 1 :operation-id :parent :phase :invoke :causal-links []}
          {:seq 2 :operation-id :child :phase :invoke
           :causal-links [:parent :parent]}]
         [{:seq 1 :operation-id :a :phase :invoke :causal-links []}
          {:seq 2 :operation-id :b :phase :invoke :causal-links []}
          {:seq 3 :operation-id :child :phase :invoke
           :causal-links [:b :a]}]
         [{:seq 1 :operation-id :child :phase :invoke
           :causal-links [:absent]}]
         [{:seq 1 :operation-id :same :phase :invoke :causal-links []}
          {:seq 2 :operation-id :same :phase :invoke :causal-links []}
          {:seq 3 :operation-id :child :phase :invoke
           :causal-links [:same]}]
         [{:seq 1 :operation-id {:host :composite} :phase :invoke
           :causal-links []}]
         [{:seq 1 :operation-id 2 :phase :invoke :causal-links []}
          {:seq 2 :operation-id "a" :phase :invoke :causal-links []}
          {:seq 3 :operation-id :child :phase :invoke
           :causal-links ["a" 2]}]]
        failures
        (mapv (fn [events]
                (try
                  (htrace/check! events
                                 [(htrace/causal-links :canonical-fan-in)])
                  nil
                  (catch Throwable error error)))
              fixtures)]
    (support/check! context "causal links reject malformed, dangling, ambiguous, and noncanonical links"
           (and (every? some? failures)
                (= (repeat 8 "hegel.trace/canonical-fan-in")
                   (map #(-> % ex-data :hegel/origin) failures)))))
  (let [failure
        (try
          (htrace/check!
           [{:seq 2 :operation-id 1 :phase :enter}
            {:seq 3 :operation-id 1 :phase :return}]
           [(htrace/contiguous-sequence :journal-not-truncated)])
          nil
          (catch Throwable error error))]
    (support/check! context "trace-rule failures expose a stable Hegel origin and bounded evidence"
           (and (= "hegel.trace/journal-not-truncated"
                   (:hegel/origin (ex-data failure)))
                (= 2 (:hegel.trace/event-count (ex-data failure)))
                (= [2 3]
                   (mapv :seq (:hegel.trace/events (ex-data failure)))))))
  (let [events [{:partition :a :cursor 1}
                {:partition :b :cursor 1}
                {:partition :a :cursor 3}
                {:partition :b :cursor 2}]
        checked (htrace/check!
                 events
                 [(htrace/ordered-sequence
                   :partition-cursors-increase
                   {:value :cursor :scope :partition
                    :order :strictly-increasing :start 1})])
        gap-failure
        (try
          (htrace/check!
           events
           [(htrace/ordered-sequence
             :partition-cursors-contiguous
             {:value :cursor :scope :partition
              :order :contiguous :start 1})])
          nil
          (catch Throwable error error))]
    (support/check! context "sequence rules distinguish scoped strict increase from continuity"
           (and (= events checked)
                (= "hegel.trace/partition-cursors-contiguous"
                   (:hegel/origin (ex-data gap-failure))))))
  (let [events [{:seq 1} {:seq 1} {:seq 4}]
        checked (htrace/check!
                 events
                 [(htrace/ordered-sequence
                   :delivery-watermark-does-not-decrease
                   {:order :nondecreasing})])]
    (support/check! context "nondecreasing sequence rules permit duplicates and gaps"
           (= events checked)))
  (let [transition (fn [state event]
                     (case [state (:event event)]
                       [nil :open] :open
                       [:open :use] :open
                       [:open :close] :closed
                       :invalid))
        fd-rule (htrace/event-model
                 :fd-linear-lifecycle
                 {:scope :fd
                  :initial nil
                  :step transition
                  :invariant (fn [state _event] (not= :invalid state))
                  :final #(= :closed %)})
        valid [{:fd 3 :event :open}
               {:fd 4 :event :open}
               {:fd 3 :event :use}
               {:fd 4 :event :close}
               {:fd 3 :event :close}]
        invalid (conj valid {:fd 3 :event :use})
        failure (try
                  (htrace/check! invalid [fd-rule])
                  nil
                  (catch Throwable error error))]
    (support/check! context "scoped event models express linear resource lifecycles"
           (and (= valid (htrace/check! valid [fd-rule]))
                (= "hegel.trace/fd-linear-lifecycle"
                   (:hegel/origin (ex-data failure))))))
  (let [final-values (atom [])
        result
        (h/run-test!
         {:test-cases 100
          :seed 20260828
          :database ""
          :verbosity :quiet
          :report-multiple-failures? false}
         (fn [_]
           (let [duplicate-at (h/draw! (g/integer 0 20))
                 events (cond->
                         [{:seq 1 :operation-id 1 :phase :enter}
                          {:seq 2 :operation-id 1 :phase :return}]
                          (>= duplicate-at 5)
                          (conj {:seq 3 :operation-id 1 :phase :return}))]
             (when (h/final?)
               (swap! final-values conj duplicate-at))
             (htrace/check! events
                            [(htrace/closed-lifecycles
                              :operation-has-one-terminal)]))))
        failure (first (:failures result))]
    (support/check! context "Hegel shrinks the input which produced an invalid semantic trace"
           (and (not (:passed? result))
                (:reproduced? failure)
                (= "hegel.trace/operation-has-one-terminal" (:origin failure))
                (= [5] @final-values)
                (= [:enter :return :return]
                   (mapv :phase
                         (-> result :final first :exception ex-data
                             :hegel.trace/events)))))))

(defn- register-step [state operation]
  (case (:operation operation)
    :write
    (when (and (= :return (:outcome operation))
               (= :ok (:value operation)))
      {:state (:input operation)})

    :read
    (when (and (= :return (:outcome operation))
               (= state (:value operation)))
      {:state state})

    :fail
    (when (= :throw (:outcome operation))
      {:state state})

    nil))

(defn bounded-linearizability [context]
  ;; These classic single-register fixtures have the same shape used by
  ;; Knossos and Porcupine examples, but this portable suite has no dependency
  ;; on either implementation.
  (let [always-legal (fn [state _] {:state state})
        events-for (fn [first-id second-id]
                     [{:seq 0 :operation-id first-id :phase :invoke
                       :operation :first}
                      {:seq 1 :operation-id first-id :phase :return
                       :value :ok}
                      {:seq 2 :operation-id second-id :phase :invoke
                       :operation :second}
                      {:seq 3 :operation-id second-id :phase :return
                       :value :ok}])
        original (hhistory/linearization
                  nil always-legal (events-for false :second))
        renamed (hhistory/linearization
                 nil always-legal (events-for :first :second))]
    (support/check! context "false operation IDs preserve non-overlapping precedence"
           (and (= [false :second] (:order original))
                (= [:first :second] (:order renamed))))
    (support/check! context "history membership survives a bijective ID renaming"
           (and (hhistory/linearizable? nil always-legal
                                        (events-for false :second))
                (hhistory/linearizable? nil always-legal
                                        (events-for :first :second)))))
  (let [overlap [{:seq 0 :operation-id :write :phase :invoke
                  :operation :write :input 1}
                 {:seq 1 :operation-id :read :phase :invoke
                  :operation :read}
                 {:seq 2 :operation-id :write :phase :return :value :ok}
                 {:seq 3 :operation-id :read :phase :return :value 0}]
        witness (hhistory/linearization 0 register-step overlap)]
    (support/check! context "overlapping operations may linearize outside invocation order"
           (and (= [:read :write] (:order witness))
                (= 1 (:final-state witness))
                (= 2 (:operation-count witness))
                (= [:read :write]
                   (mapv :operation (:operations witness))))))
  (let [real-time-violation
        [{:seq 0 :operation-id :write :phase :invoke
          :operation :write :input 1}
         {:seq 1 :operation-id :write :phase :return :value :ok}
         {:seq 2 :operation-id :read :phase :invoke :operation :read}
         {:seq 3 :operation-id :read :phase :return :value 0}]
        failure (try
                  (hhistory/check! 0 register-step real-time-violation
                                   {:name :register-agrees})
                  nil
                  (catch Throwable error error))]
    (support/check! context "completed-before-invoked precedence cannot be reordered"
           (and (not (hhistory/linearizable?
                      0 register-step real-time-violation))
                (= ::hhistory/not-linearizable (:type (ex-data failure)))
                (= "hegel.history/register-agrees"
                   (:hegel/origin (ex-data failure)))
                (= real-time-violation
                   (:hegel.history/events (ex-data failure)))
                (false?
                 (:hegel.history/evidence-truncated? (ex-data failure))))))
  (let [thrown [{:seq 9 :operation-id :failure :phase :invoke
                 :operation :fail}
                {:seq 10 :operation-id :failure :phase :throw
                 :exception-class "expected"}]
        witness (hhistory/check! :open register-step thrown)]
    (support/check! context "throw terminals participate in the sequential model"
           (and (= [:failure] (:order witness))
                (= :throw (-> witness :operations first :outcome))
                (= "expected"
                   (-> witness :operations first :terminal
                       :exception-class)))))
  (let [partitioned
        [{:seq 0 :operation-id :a-write :phase :invoke
          :operation :write :input 1 :account :a}
         {:seq 1 :operation-id :b-read :phase :invoke
          :operation :read :account :b}
         {:seq 2 :operation-id :a-write :phase :return
          :value :ok :account :a}
         {:seq 3 :operation-id :b-read :phase :return
          :value 0 :account :b}]
        witness
        (hhistory/check!
         0 register-step partitioned
         {:partition-by #(-> % :invoke :account)})]
    (support/check! context "partitioned histories use one model state per partition"
           (and (= 2 (:operation-count witness))
                (= [:a :b] (mapv :partition (:partitions witness)))
                (= [[:a-write] [:b-read]]
                   (mapv :order (:partitions witness)))
                (= [1 0] (mapv :final-state (:partitions witness))))))
  (let [malformed
        [[{:seq 0 :operation-id :x :phase :invoke :operation :read}]
         [{:seq 0 :operation-id :x :phase :invoke :operation :read}
          {:seq 1 :operation-id :x :phase :invoke :operation :read}]
         [{:seq 0 :operation-id :x :phase :invoke :operation :read}
          {:seq 2 :operation-id :x :phase :return :value 0}]
         [{:seq 0 :operation-id nil :phase :invoke :operation :read}
          {:seq 1 :operation-id nil :phase :return :value 0}]]
        failures
        (mapv (fn [events]
                (try
                  (hhistory/operations events)
                  nil
                  (catch Throwable error error)))
              malformed)]
    (support/check! context "incomplete, duplicate, and non-contiguous histories are rejected"
           (every? #(= ::hhistory/malformed-history
                       (:type (ex-data %)))
                   failures)))
  (let [events [{:seq 0 :operation-id :a :phase :invoke
                 :operation :read}
                {:seq 1 :operation-id :a :phase :return :value 0}
                {:seq 2 :operation-id :b :phase :invoke
                 :operation :read}
                {:seq 3 :operation-id :b :phase :return :value 0}]
        failure (try
                  (hhistory/check! 0 register-step events
                                   {:max-operations 1})
                  nil
                  (catch Throwable error error))]
    (support/check! context "the operation bound fails before exponential search"
           (and (= ::hhistory/operation-bound (:type (ex-data failure)))
                (= 1 (:hegel.history/max-operations (ex-data failure)))
                (= 2 (count (:hegel.history/events (ex-data failure))))
                (:hegel.history/evidence-truncated? (ex-data failure)))))
  (let [events [{:seq 1 :operation-id :read :phase :invoke
                 :operation :read}
                {:seq 2 :operation-id :read :phase :return :value 1}]
        failure (try
                  (htrace/check!
                   events
                   [(hhistory/rule
                     :woven-register-linearizable
                     {:initial 0 :step register-step :sequence-start 1})])
                  nil
                  (catch Throwable error error))]
    (support/check! context "history rules compose with hegel.trace bounded evidence"
           (and (= "hegel.trace/woven-register-linearizable"
                   (:hegel/origin (ex-data failure)))
                (= 2 (:hegel.trace/event-count (ex-data failure)))
                (= events (:hegel.trace/events (ex-data failure)))))))

(defn exhaustive-history-oracle [context]
  (doseq [[description passed?] (horacle/checks)]
    (support/check! context description passed?)))

(defn history-budget-contract [context]
  (let [result (t/run-tests 'hegel.history-budget-test)]
    (support/check! context "history budget contract suite"
                    (zero? (+ (:fail result) (:error result))))))

(defn event-contract-characterization [context]
  (let [result (t/run-tests 'hegel.event-contract-test)]
    (support/check! context "trace/history event domain characterization"
                    (zero? (+ (:fail result) (:error result))))))

(defn canonical-event-contract [context]
  (let [result (t/run-tests 'hegel.canonical-event-contract-test)]
    (support/check! context "canonical operation-event profile"
                    (zero? (+ (:fail result) (:error result))))))
