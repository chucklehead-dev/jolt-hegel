(ns hegel.validation-test
  "Focused public validation contract tests."
  (:require [clojure.test :refer [deftest is testing]]
            [hegel.core :as h]
            [hegel.ffi :as hffi]
            [hegel.generator :as g]
            [hegel.history :as history]
            [hegel.report :as report]
            [hegel.stateful :as stateful]
            [hegel.trace :as trace]))

(def ^:private max-uint64 18446744073709551615N)
(def ^:private max-int64 9223372036854775807N)
(def ^:private run-opts
  {:test-cases 1 :seed 1 :database "" :verbosity :quiet})

(defn- error-of [thunk]
  (try
    (thunk)
    nil
    (catch Throwable error error)))

(defn- usage-error? [error]
  (true? (:hegel/usage-error? (ex-data error))))

(defn- assert-error! [expected-type thunk]
  (let [error (error-of thunk)]
    (is (some? error))
    (is (= expected-type (:type (ex-data error))))
    (is (usage-error? error))))

(def ^:private changed-constructor-errors
  [{:label "integer" :type ::g/invalid-option :thunk #(g/integer {:unknown true})}
   {:label "double boolean" :type ::g/invalid-option :thunk #(g/double {:nan? :yes})}
   {:label "bytes uint64" :type ::g/invalid-option :thunk #(g/bytes {:min-size -1})}
   {:label "date" :type ::g/invalid-option :thunk #(g/date {:unknown true})}
   {:label "time" :type ::g/invalid-option :thunk #(g/time {:unknown true})}
   {:label "datetime" :type ::g/invalid-option :thunk #(g/datetime {:unknown true})}
   {:label "string" :type ::g/invalid-option :thunk #(g/string {:alphabet ["a"]})}
   {:label "character" :type ::g/invalid-option :thunk #(g/character {:unknown true})}
   {:label "character fixed-size contract" :type ::g/invalid-option
    :thunk #(g/character {:min-size 1})}
   {:label "regex" :type ::g/invalid-option :thunk #(g/regex-str "x" {:full-match? 1})}
   {:label "domain" :type ::g/invalid-option :thunk #(g/domain {:max-length 3})}
   {:label "recursive" :type ::g/invalid-option
    :thunk #(g/recursive {:max-depth -1} (g/integer) identity)}
   {:label "vector boolean" :type ::g/invalid-option
    :thunk #(g/vector {:unique? :yes} (g/integer))}
   {:label "list bounds" :type ::g/invalid-option
    :thunk #(g/list {:size -1} (g/integer))}
   {:label "set key" :type ::g/invalid-option
    :thunk #(g/set {:unique? true} (g/integer))}
   {:label "sorted set" :type ::g/invalid-option
    :thunk #(g/sorted-set {:unknown true} (g/integer))}
   {:label "map" :type ::g/invalid-option
    :thunk #(g/map {:size -1} (g/integer) (g/integer))}
   {:label "sorted map" :type ::g/invalid-option
    :thunk #(g/sorted-map {:unknown true} (g/integer) (g/integer))}
   {:label "trace check" :type ::trace/invalid-options
    :thunk #(trace/check! [] [] {:unknown true})}
   {:label "trace sequence callable" :type ::trace/invalid-rule
    :thunk #(trace/ordered-sequence :x {:scope 42})}
   {:label "trace model callable" :type ::trace/invalid-rule
    :thunk #(trace/event-model :x {:step 42})}
   {:label "history options" :type ::history/invalid-options
    :thunk #(history/linearization nil identity [] {:unknown true})}
   {:label "history step" :type ::history/invalid-options
    :thunk #(history/linearization nil 42 [] {})}
   {:label "history rule" :type ::history/invalid-options
    :thunk #(history/rule :x {:step 42})}
   {:label "stateful callable" :type ::stateful/invalid-argument
    :thunk #(stateful/rule :x {:precondition :no} identity)}
   {:label "report runner" :type ::report/invalid-option
    :thunk #(report/counting-runner {:reporter :no})}
   {:label "report false reporter" :type ::report/invalid-option
    :thunk #(report/counting-runner {:reporter false})}
   {:label "report run" :type ::report/invalid-run
    :thunk #(report/run! (report/counting-runner) "x" :not-a-fn)}])

(deftest core-options-are-closed-and-preflight-native-work
  (doseq [[label opts]
          [["non-map" []] ["unknown key" {:unknown true}]
           ["mode" {:mode :missing}] ["backend" {:backend :missing}]
           ["verbosity" {:verbosity :missing}] ["zero test cases" {:test-cases 0}]
           ["test cases below uint64" {:test-cases -1}]
           ["test cases above uint64" {:test-cases (inc max-uint64)}]
           ["stateful steps below one" {:stateful-step-count 0}]
           ["stateful steps above int64" {:stateful-step-count (inc max-int64)}]
           ["seed below uint64" {:seed -1}] ["seed above uint64" {:seed (inc max-uint64)}]
           ["boolean" {:derandomize? :yes}] ["string" {:database 1}]
           ["phase collection" {:phases :generate}] ["phase member" {:phases [:missing]}]
           ["health member" {:suppress-health-checks [:missing]}]]]
    (let [native-entered? (atom false)]
      (testing label
        (with-redefs [hffi/ensure-compatible-version! #(reset! native-entered? true)
                      hffi/context-new! #(reset! native-entered? true)]
          (assert-error! ::h/invalid-option #(h/run-test! opts (fn [_] nil))))
        (is (false? @native-entered?))))))

(deftest core-numeric-boundaries-match-the-abi-contract
  (let [validate-options (ns-resolve 'hegel.core 'validate-run-options!)]
    (is (= {:test-cases 1 :seed max-uint64}
           (validate-options {:test-cases 1 :seed max-uint64} (fn [_] nil))))
    (is (= {:stateful-step-count 1}
           (validate-options {:stateful-step-count 1} (fn [_] nil))))
    (is (= {:stateful-step-count max-int64}
           (validate-options {:stateful-step-count max-int64} (fn [_] nil))))))

(deftest uint64-boundaries-cross-the-native-setting-bridge
  ;; A one-case property keeps the native run bounded while exercising the
  ;; largest legal u64 seed through the host's ABI conversion.
  (let [result (h/run-test! {:test-cases 1
                             :seed max-uint64
                             :database ""
                             :verbosity :quiet}
                            (fn [_] nil))]
    (is (:passed? result))
    (is (= 1 (:test-cases result)))
    (is (= (str max-uint64) (:seed result)))))

(deftest changed-constructors-preserve-their-namespace-error-types
  (doseq [{:keys [label type thunk]} changed-constructor-errors]
    (testing label (assert-error! type thunk)))
  (doseq [[type thunk]
          [[::g/invalid-option #(g/integer [])]
           [::trace/invalid-options #(trace/check! [] [] [])]
           [::history/invalid-options #(history/linearization nil identity [] [])]
           [::history/invalid-options
            (fn []
              ;; Deliberately violate the map contract to test public validation.
              #_{:clj-kondo/ignore [:type-mismatch]}
              (history/rule :x []))]
           [::report/invalid-option #(report/counting-runner [])]]]
    (assert-error! type thunk)))

(deftest history-validation-keeps-its-established-origin-and-data
  (let [non-map (error-of #(history/linearization nil identity [] []))
        unknown (error-of #(history/linearization nil identity []
                                                {:name :named :unknown true}))
        rule-non-map (error-of (fn []
                                ;; Deliberate invalid-input regression.
                                #_{:clj-kondo/ignore [:type-mismatch]}
                                (history/rule :rule [])))
        rule-unknown (error-of #(history/rule :rule {:unknown true}))]
    (is (= "hegel.history/linearizable" (:hegel/origin (ex-data non-map))))
    (is (= [] (:options (ex-data non-map))))
    (is (= "hegel.history/named" (:hegel/origin (ex-data unknown))))
    (is (= [:unknown] (:unknown-keys (ex-data unknown))))
    (is (= "hegel.history/rule" (:hegel/origin (ex-data rule-non-map))))
    (is (= [] (:options (ex-data rule-non-map))))
    (is (= "hegel.history/rule" (:hegel/origin (ex-data rule-unknown))))
    (is (= [:unknown] (:unknown-keys (ex-data rule-unknown))))))

(deftest history-rules-preflight-all-linearization-options
  (doseq [[label option]
          [["max operations" {:max-operations -1}]
           ["partition selector" {:partition-by 42}]
           ["sequence start" {:sequence-start :not-an-integer}]]]
    (testing (str label " outside a property")
      (assert-error! ::history/invalid-options
                     #(history/rule :history-rule (assoc option :step identity))))
    (testing (str label " inside a property")
      (assert-error! ::history/invalid-options
                     #(h/run-test! run-opts
                                   (fn [_]
                                     (history/rule :history-rule
                                                   (assoc option :step identity))))))))

(deftest trace-model-validation-keeps-its-established-name-and-options
  (let [non-map (error-of #(trace/event-model :model []))
        unknown (error-of #(trace/event-model :model {:step identity :unknown true}))]
    (is (= :model (:name (ex-data non-map))))
    (is (= [] (:options (ex-data non-map))))
    (is (= :model (:name (ex-data unknown))))
    (is (= [:unknown] (:unknown-keys (ex-data unknown))))))

(deftest trace-rule-callbacks-preserve-usage-errors-but-wrap-ordinary-errors
  (let [usage-error (ex-info "configured incorrectly"
                             {:type ::callback-usage :hegel/usage-error? true})
        usage-rule (trace/rule :usage (fn [_] (throw usage-error)))
        ordinary-rule (trace/rule :ordinary (fn [_] (throw (ex-info "boom" {}))))
        preserved (error-of #(trace/check! [] [usage-rule]))
        wrapped (error-of #(trace/check! [] [ordinary-rule]))
        live (error-of #(h/run-test! run-opts
                                     (fn [_] (trace/check! [] [usage-rule]))))]
    (is (identical? usage-error preserved))
    (is (= ::callback-usage (:type (ex-data live))))
    (is (usage-error? live))
    (is (= ::trace/rule-error (:type (ex-data wrapped))))
    (is (not (usage-error? wrapped)))))

(deftest explicit-collection-size-is-validated-even-when-min-max-win
  (let [outside #(g/vector {:size -1 :min-size 0 :max-size 1} (g/integer))]
    (assert-error! ::g/invalid-option outside)
    (assert-error! ::g/invalid-option
                   #(h/run-test! run-opts (fn [_] (outside))))))

(deftest core-draw-and-sample-reject-invalid-inputs-before-a-run
  (doseq [[type thunk]
          [[::h/invalid-generator #(h/draw! :not-a-generator)]
           [::h/invalid-sample #(h/sample 0 (g/integer))]
           [::h/invalid-sample #(h/sample 1 :not-a-generator)]]]
    (assert-error! type thunk)))

(deftest float-infinite-bounds-remain-a-native-supported-domain
  ;; The pinned C ABI documents +/- infinity as the unbounded endpoint values.
  (is (fn? (g/double {:min ##-Inf :max 0.0})))
  (is (fn? (g/double {:min 0.0 :max ##Inf})))
  (assert-error! ::g/invalid-option #(g/double {:min ##NaN})))

(deftest construction-errors-escape-every-changed-family-inside-a-property
  ;; Each public constructor above is exercised through the live run path, so a
  ;; setup error cannot be marked interesting and sent to shrinking/replay.
  (doseq [{:keys [label type thunk]} changed-constructor-errors]
    (testing label
      (assert-error! type #(h/run-test! run-opts (fn [_] (thunk)))))))

(deftest reporting-classifies-construction-errors-as-errors-not-property-failures
  (let [events (atom [])
        runner (report/counting-runner {:reporter #(swap! events conj %)})]
    (is (nil? (report/run! runner "invalid construction"
                            #(h/run-test! run-opts
                                          (fn [_] (g/integer {:unknown true}))))))
    (is (= 1 (report/run-count runner)))
    (is (= 1 (report/failure-count runner)))
    (is (= :error (:type (first @events))))
    (is (= ::g/invalid-option (-> @events first :exception ex-data :type)))))

(deftest property-semantics-remain-distinct-from-usage-errors
  (testing "assumptions remain rejected cases"
    (let [first-case? (atom true)
          result (h/run-test! (assoc run-opts :suppress-health-checks [:filter-too-much])
                              (fn [_]
                                (when (compare-and-set! first-case? true false)
                                  (h/assume! false))))]
      (is (:passed? result))
      (is (= 1 (:invalid-test-cases result)))
      (is (zero? (:interesting-test-cases result)))))
  (testing "trace bounds and rule failures remain semantic failures"
    (let [bound-error (error-of #(trace/check! [{} {}] [] {:max-events 1}))
          rule-error (error-of #(trace/check! [] [(trace/rule :false (constantly false))]))]
      (is (= ::trace/event-bound (:type (ex-data bound-error))))
      (is (not (usage-error? bound-error)))
      (is (= ::trace/rule-failed (:type (ex-data rule-error))))
      (is (not (usage-error? rule-error)))))
  (testing "non-linearizable histories remain semantic failures"
    (let [events [{:seq 0 :operation-id :write :phase :invoke :operation :write :input 1}
                  {:seq 1 :operation-id :write :phase :return :value :ok}
                  {:seq 2 :operation-id :read :phase :invoke :operation :read}
                  {:seq 3 :operation-id :read :phase :return :value 0}]
          step (fn [state operation]
                 (case (:operation operation)
                   :write {:state (:input operation)}
                   :read (when (= state (:value operation)) {:state state})))
          error (error-of #(history/check! 0 step events {:name :register}))]
      (is (= ::history/not-linearizable (:type (ex-data error))))
      (is (not (usage-error? error))))))
