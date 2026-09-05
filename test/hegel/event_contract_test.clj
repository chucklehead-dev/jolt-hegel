(ns hegel.event-contract-test
  "Characterizes the deliberately separate trace and history event contracts."
  (:require [clojure.test :refer [deftest is testing]]
            [hegel.history :as history]
            [hegel.trace :as trace]))

(defn- rejected-with-type?
  [type thunk]
  (try
    (thunk)
    false
    (catch Throwable error
      (= type (:type (ex-data error))))))

(defn- history-events
  ([start]
   [{:seq start :operation-id :read :phase :invoke :operation :read}
    {:seq (inc start) :operation-id :read :phase :return :value :ok}])
  ([] (history-events 0)))

(deftest legacy-traces-and-complete-histories-have-different-lifecycles
  (let [legacy-enter [{:seq 0 :operation-id :legacy :phase :enter :operation :read}
                      {:seq 1 :operation-id :legacy :phase :return :value :ok}]
        missing-seq [{:operation-id :no-seq :phase :invoke :operation :read}
                     {:operation-id :no-seq :phase :return :value :ok}]
        missing-operation [{:seq 0 :operation-id :no-operation :phase :invoke}
                           {:seq 1 :operation-id :no-operation :phase :return
                            :value :ok}]]
    (testing "trace accepts the legacy :enter lifecycle even with otherwise history-shaped events"
      (is (= legacy-enter
             (trace/check! legacy-enter [(trace/closed-lifecycles)])))
      (is (rejected-with-type? ::history/malformed-history
                               #(history/operations legacy-enter))))
    (testing "closed trace lifecycles do not require a sequence number"
      (is (= missing-seq
             (trace/check! missing-seq [(trace/closed-lifecycles)])))
      (is (rejected-with-type? ::history/malformed-history
                               #(history/operations missing-seq))))
    (testing "closed trace lifecycles do not require an invocation operation"
      (is (= missing-operation
             (trace/check! missing-operation [(trace/closed-lifecycles)])))
      (is (rejected-with-type? ::history/malformed-history
                               #(history/operations missing-operation))))))

(deftest history-sequences-are-contiguous-but-may-have-an-implicit-offset
  (let [shifted (history-events 7)]
    (testing "the first observed sequence is the default start"
      (is (= [:read] (mapv :operation-id (history/operations shifted)))))
    (testing "an explicit start makes that offset part of the history contract"
      (is (= [:read] (mapv :operation-id
                           (history/operations shifted {:sequence-start 7}))))
      (is (rejected-with-type? ::history/malformed-history
                               #(history/operations shifted {:sequence-start 0}))))
    (testing "gaps and duplicates are invalid histories"
      (is (rejected-with-type?
           ::history/malformed-history
           #(history/operations [{:seq 0 :operation-id :read :phase :invoke
                                  :operation :read}
                                 {:seq 2 :operation-id :read :phase :return
                                  :value :ok}])))
      (is (rejected-with-type?
           ::history/malformed-history
           #(history/operations [{:seq 0 :operation-id :read :phase :invoke
                                  :operation :read}
                                 {:seq 0 :operation-id :read :phase :return
                                  :value :ok}]))))))

(deftest trace-sequence-policy-is-selected-by-the-rule
  (let [gapped-and-duplicated [{:seq 4} {:seq 4} {:seq 9}]]
    (is (= gapped-and-duplicated
           (trace/check! gapped-and-duplicated
                         [(trace/ordered-sequence :watermark
                                                  {:order :nondecreasing})])))
    (is (rejected-with-type?
         ::trace/rule-failed
         #(trace/check! gapped-and-duplicated
                        [(trace/ordered-sequence :strict
                                                 {:order :strictly-increasing})])))
    (is (rejected-with-type?
         ::trace/rule-failed
         #(trace/check! gapped-and-duplicated
                        [(trace/contiguous-sequence :contiguous 4)])))))

(deftest trace-metadata-rules-are-opt-in-and-history-preserves-extra-data
  (let [history-with-extra
        [{:seq 0 :operation-id :read :phase :invoke :operation :read
          :arbitrary {:opaque [1 2 3]}
          :parent-operation-id :missing-parent
          :context-id :wrong-context
          :causal-links [:missing-link]}
         {:seq 1 :operation-id :read :phase :return :value :ok
          :arbitrary :retained
          :parent-operation-id :also-wrong
          :context-id :different-context
          :causal-links [:still-missing]}]
        bare-invocation [{:seq 1 :operation-id :root :phase :invoke}]]
    (testing "history neither requires nor validates trace parent/context/causal metadata"
      (is (= history-with-extra
             ((juxt :invoke :terminal) (first (history/operations history-with-extra)))))
      (is (= :read
             (-> (history/operations history-with-extra) first :operation)))
      (is (= :retained
             (-> (history/operations history-with-extra) first :terminal :arbitrary))))
    (testing "those metadata contracts are imposed only when their trace rules are chosen"
      (is (= bare-invocation (trace/check! bare-invocation [])))
      (doseq [rule [(trace/causal-parentage :parents)
                    (trace/context-coherence :contexts)
                    (trace/causal-links :links)]]
        (is (rejected-with-type? ::trace/rule-failed
                                 #(trace/check! bare-invocation [rule])))))
    (testing "causal-link canonical order is tagged scalar spelling, not numeric order"
      (let [prefix [{:seq 1 :operation-id 10 :phase :invoke :causal-links []}
                    {:seq 2 :operation-id 2 :phase :invoke :causal-links []}]
            canonical (conj prefix {:seq 3 :operation-id :join :phase :invoke
                                    :causal-links [10 2]})
            noncanonical (conj prefix {:seq 3 :operation-id :join :phase :invoke
                                       :causal-links [2 10]})]
        (is (= canonical
               (trace/check! canonical [(trace/causal-links :fan-in)])))
        (is (rejected-with-type? ::trace/rule-failed
                                 #(trace/check! noncanonical
                                                [(trace/causal-links :fan-in)])))))
    (testing "valid parent and context metadata satisfy their selected rules"
      (let [contextual [{:seq 1 :operation-id :parent :phase :invoke
                         :parent-operation-id nil :context-id :request-7}
                        {:seq 2 :operation-id :child :phase :invoke
                         :parent-operation-id :parent :context-id :request-7}]]
        (is (= contextual
               (trace/check! contextual [(trace/causal-parentage :parents)
                                         (trace/context-coherence :contexts)])))))))

(deftest causal-parentage-allows-async-children-while-synchronous-parentage-does-not
  (let [async-events [{:seq 1 :operation-id :parent :phase :invoke
                       :parent-operation-id nil}
                      {:seq 2 :operation-id :parent :phase :return}
                      {:seq 3 :operation-id :child :phase :invoke
                       :parent-operation-id :parent}
                      {:seq 4 :operation-id :child :phase :return}]]
    (is (= async-events
           (trace/check! async-events [(trace/causal-parentage :async-ok)])))
    (is (rejected-with-type?
         ::trace/rule-failed
         #(trace/check! async-events [(trace/synchronous-parentage :sync-only)])))))

(deftest synchronous-parentage-accepts-a-wholly-nested-child
  (let [nested-events [{:seq 1 :operation-id :parent :phase :invoke
                        :parent-operation-id nil}
                       {:seq 2 :operation-id :child :phase :invoke
                        :parent-operation-id :parent}
                       {:seq 3 :operation-id :child :phase :return}
                       {:seq 4 :operation-id :parent :phase :return}]]
    (is (= nested-events
           (trace/check! nested-events [(trace/synchronous-parentage :nested)])))))

(deftest empty-history-is-valid-but-an-incomplete-lifecycle-is-not
  (is (= [] (history/operations [])))
  (is (rejected-with-type?
       ::history/malformed-history
       #(history/operations [{:seq 0 :operation-id :read :phase :invoke
                             :operation :read}])))
  (is (rejected-with-type?
       ::trace/rule-failed
       #(trace/check! [{:operation-id :open :phase :enter}]
                      [(trace/closed-lifecycles :complete)]))))
