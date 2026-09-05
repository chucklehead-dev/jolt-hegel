(ns hegel.canonical-event-contract-test
  "Contract tests for the explicit versioned operation-event profile."
  (:require [clojure.test :refer [deftest is testing]]
            [hegel.event-contract :as contract]
            [hegel.trace :as trace]))

(defn- error-data [thunk]
  (try
    (thunk)
    nil
    (catch Throwable error
      (ex-data error))))

(defn- rejected-as? [type thunk]
  (= type (:type (error-data thunk))))

(def ^:private one-return
  [{:seq 1 :operation-id :read :phase :invoke :operation :read
    :parent-operation-id nil :context-id :request-1 :causal-links []
    :extra {:preserved true}}
   {:seq 2 :operation-id :read :phase :return :value :ok
    :extra :terminal-metadata}])

(deftest identity-and-minimal-complete-profile-are-accepted
  (testing "the profile identity is public and versioned"
    (is (= "hegel.operation-events" contract/contract-id))
    (is (= "1" contract/contract-revision)))
  (testing "empty and complete histories return their identical vectors"
    (let [empty-events []]
      (is (identical? empty-events (contract/check! empty-events))))
    (let [checked (contract/check! one-return)]
      (is (identical? one-return checked))
      (is (= {:preserved true} (-> checked first :extra)))
      (is (= :terminal-metadata (-> checked second :extra))))))

(deftest canonical-profile-allows-async-parentage-and-throw-terminals
  (let [async-events
        [{:seq 1 :operation-id :parent :phase :invoke :operation :start
          :parent-operation-id nil :context-id :request-2 :causal-links []}
         {:seq 2 :operation-id :parent :phase :return :value :done}
         {:seq 3 :operation-id :child :phase :invoke :operation :work
          :parent-operation-id :parent :context-id :request-2
          :causal-links [:parent]}
         {:seq 4 :operation-id :child :phase :throw :exception-class "expected"}]]
    (is (identical? async-events (contract/check! async-events)))))

(deftest canonical-profile-uses-tagged-scalar-causal-link-order
  (let [mixed-events
        [{:seq 1 :operation-id 10 :phase :invoke :operation :first
          :parent-operation-id nil :context-id :request-3 :causal-links []}
         {:seq 2 :operation-id 10 :phase :return}
         {:seq 3 :operation-id 2 :phase :invoke :operation :second
          :parent-operation-id nil :context-id :request-3 :causal-links []}
         {:seq 4 :operation-id 2 :phase :return}
         {:seq 5 :operation-id :keyword :phase :invoke :operation :third
          :parent-operation-id nil :context-id :request-3 :causal-links []}
         {:seq 6 :operation-id :keyword :phase :return}
         {:seq 7 :operation-id "string" :phase :invoke :operation :fourth
          :parent-operation-id nil :context-id :request-3 :causal-links []}
         {:seq 8 :operation-id "string" :phase :return}
         {:seq 9 :operation-id 'symbol :phase :invoke :operation :join
          :parent-operation-id nil :context-id :request-3
          :causal-links [10 2]}
         {:seq 10 :operation-id 'symbol :phase :return}]
        noncanonical (assoc-in mixed-events [8 :causal-links] [2 10])]
    (is (identical? mixed-events (contract/check! mixed-events)))
    (is (rejected-as? ::trace/rule-failed #(contract/check! noncanonical)))))

(deftest canonical-profile-rejects-legacy-and-invalid-event-shapes
  (let [legacy-enter [{:seq 1 :operation-id :legacy :phase :enter :operation :read
                       :parent-operation-id nil :context-id :request :causal-links []}
                      {:seq 2 :operation-id :legacy :phase :return}]
        boolean-id (mapv #(assoc % :operation-id false) one-return)
        composite-id (mapv #(assoc % :operation-id {:not :scalar}) one-return)
        missing-operation (dissoc (first one-return) :operation)
        missing-parent (dissoc (first one-return) :parent-operation-id)
        missing-context (dissoc (first one-return) :context-id)
        missing-links (dissoc (first one-return) :causal-links)]
    (is (rejected-as? ::trace/rule-failed #(contract/check! legacy-enter)))
    (is (rejected-as? ::trace/rule-failed #(contract/check! boolean-id)))
    (is (rejected-as? ::trace/rule-failed #(contract/check! composite-id)))
    (doseq [events [boolean-id composite-id [nil] [42]
                    [(dissoc (first one-return) :seq) (second one-return)]]]
      (is (= "hegel.trace/hegel.operation-events/shape"
             (:hegel/origin (error-data #(contract/check! events))))))
    (doseq [event [missing-operation missing-parent missing-context missing-links]]
      (is (rejected-as? ::trace/rule-failed
                        #(contract/check! [event (second one-return)]))))))

(deftest canonical-profile-rejects-sequence-lifecycle-and-metadata-violations
  (let [gapped (assoc-in one-return [1 :seq] 3)
        dangling-links (assoc-in one-return [0 :causal-links] [:absent])
        duplicate-links
        [{:seq 1 :operation-id :parent :phase :invoke :operation :start
          :parent-operation-id nil :context-id :request :causal-links []}
         {:seq 2 :operation-id :child :phase :invoke :operation :work
          :parent-operation-id :parent :context-id :request
          :causal-links [:parent :parent]}
         {:seq 3 :operation-id :parent :phase :return}
         {:seq 4 :operation-id :child :phase :return}]
        missing-parent
        [{:seq 1 :operation-id :child :phase :invoke :operation :work
          :parent-operation-id :absent :context-id :request :causal-links []}
         {:seq 2 :operation-id :child :phase :return}]
        mismatched-context
        [{:seq 1 :operation-id :parent :phase :invoke :operation :start
          :parent-operation-id nil :context-id :outer :causal-links []}
         {:seq 2 :operation-id :child :phase :invoke :operation :work
          :parent-operation-id :parent :context-id :inner :causal-links []}
         {:seq 3 :operation-id :child :phase :return}
         {:seq 4 :operation-id :parent :phase :return}]
        incomplete [(first one-return)]]
    (doseq [events [gapped dangling-links duplicate-links missing-parent
                    mismatched-context incomplete]]
      (is (rejected-as? ::trace/rule-failed #(contract/check! events))))
    (is (rejected-as? ::trace/rule-failed
                      #(contract/check! one-return {:sequence-start 0})))
    (is (identical? one-return
                    (contract/check! one-return {:max-events 2
                                                  :sequence-start 1})))))

(deftest canonical-options-and-envelope-boundaries-are-fail-closed
  (testing "bad options are usage errors"
    (doseq [opts [nil [] {:max-events 0} {:max-events -1} {:max-events 1.5}
                  {:sequence-start nil} {:sequence-start false} {:unknown true}]]
      (let [data (error-data #(contract/check! one-return opts))]
        (is (= ::contract/invalid-options (:type data)))
        (is (true? (:hegel/usage-error? data))))))
  (testing "oversized and non-vector inputs preserve the trace boundary taxonomy"
    (let [oversized (vec (repeat 257 {}))
          oversized-data (error-data #(contract/check! oversized))
          nonvector-data (error-data #(contract/check! '()))]
      (is (= ::trace/event-bound (:type oversized-data)))
      (is (not (contains? oversized-data :hegel.trace/events)))
      (is (= ::trace/invalid-trace (:type nonvector-data)))))
  (testing "a closed, correctly identified envelope returns identically"
    (let [envelope {:contract-id contract/contract-id
                    :contract-revision contract/contract-revision
                    :events one-return}]
      (is (identical? envelope (contract/check-envelope! envelope)))
      (is (identical? envelope
                      (contract/check-envelope! envelope {:max-events 2
                                                          :sequence-start 1})))))
  (testing "bad envelopes are usage errors without event evidence"
    (doseq [envelope [nil [] {:contract-id contract/contract-id
                       :contract-revision contract/contract-revision}
                      {:contract-id "other" :contract-revision "1" :events one-return}
                      {:contract-id contract/contract-id :contract-revision "2"
                       :events one-return}
                      {:contract-id contract/contract-id
                       :contract-revision contract/contract-revision
                       :events one-return :extra true}]]
      (let [data (error-data #(contract/check-envelope! envelope))]
        (is (= ::contract/invalid-envelope (:type data)))
        (is (true? (:hegel/usage-error? data)))
        (is (not (contains? data :hegel.trace/events)))))))

(deftest semantic-failures-retain-bounded-event-evidence
  (let [events (assoc-in one-return [1 :seq] 3)
        data (error-data #(contract/check! events))]
    (is (= ::trace/rule-failed (:type data)))
    (is (= "hegel.trace/hegel.operation-events/sequence"
           (:hegel/origin data)))
    (is (= events (:hegel.trace/events data)))
    (is (= 2 (:hegel.trace/event-count data)))
    (is (string? (:hegel/origin data)))))
