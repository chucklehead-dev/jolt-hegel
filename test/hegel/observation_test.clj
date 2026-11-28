(ns hegel.observation-test
  (:require [clojure.test :as t :refer [deftest is]]
            [hegel.clojure-test :as hc]
            [hegel.core :as h]
            [hegel.ffi :as hffi]
            [hegel.generator :as g]
            [hegel.host :as host]
            [hegel.internal.observation-policy :as policy]
            [hegel.internal.observations :as observations]
            [hegel.report :as report]
            [hegel.stateful :as stateful]
            [hegel.stateful.journal :as journal]))

(def run-opts {:test-cases 1 :seed 42 :database "" :verbosity :quiet
               :phases [:generate]})

(defn- error-of [thunk]
  (try (thunk) nil (catch Throwable error error)))

(deftest observation-options-reject-usage-errors-before-native-work
  (doseq [opts [{:observations? :yes} {:show-statistics? 1}
               {:coverage nil} {:coverage {}}
               {:coverage {:scope :exploration :requirements {}}}
               {:coverage {:scope :generation-only :requirements {"branch" {}}}}
               {:coverage {:scope :exploration :requirements {"branch" {:min-count 0}}}}
               {:coverage {:scope :exploration :requirements {"branch" {:min-fraction ##NaN}}}}
               {:coverage {:scope :exploration :requirements {"branch" {:min-fraction 1.1}}}}
               {:coverage {:scope :exploration :requirements {"branch" {:unknown true}}}}
               {:coverage {:scope :exploration :requirements {"" {}}}}]]
    (let [entered (atom false)
          error (with-redefs [hffi/ensure-compatible-version! #(reset! entered true)
                              hffi/context-new! #(reset! entered true)]
                  (error-of #(h/run-test! opts (fn [_] nil))))]
      (is (true? (:hegel/usage-error? (ex-data error))))
      (is (false? @entered)))))

(deftest public-observations-validate-before-native-dispatch
  (let [calls (atom [])]
    (binding [h/*test-case* {:context :ctx :handle :case}]
      (with-redefs [hffi/event! (fn [& args] (swap! calls conj args))
                    hffi/event-value! (fn [& args] (swap! calls conj args))]
        (doseq [label (concat
                      [nil :keyword "" "  " "x\u0000y" (apply str (repeat 257 "x"))
                       (apply str (repeat 129 "😀"))]
                      ;; Jolt rejects surrogate codepoints at construction;
                      ;; UTF-16 hosts can supply malformed strings to the API.
                      (when-not (= :jolt (host/runtime))
                        [(str (char 55296)) (str (char 56320))]))]
          (is (:hegel/usage-error? (ex-data (error-of #(h/event! label))))))
        (when (= :jolt (host/runtime))
          (doseq [codepoint [55296 56320]]
            (let [result (try (str (char codepoint)) (catch IllegalArgumentException _ nil))]
              (is (or (nil? result) (= "�" result))))))
        (doseq [value [nil "2" ##NaN ##Inf ##-Inf]]
          (is (:hegel/usage-error? (ex-data (error-of #(h/observe! value "size"))))))
        (is (empty? @calls))
        (is (nil? (h/event! "branch/😀")))
        (is (nil? (h/observe! 2 "size")))
        (is (= [[:ctx :case "branch/😀"] [:ctx :case 2.0 "size"]]
               (mapv vec @calls)))))))

(deftest native-events-and-numeric-observations-have-distinct-counts
  (let [result
        (h/run-test!
         (assoc run-opts :coverage {:scope :generation-only
                                   :requirements {"branch" {:min-count 1 :min-fraction 1}}})
         (fn [_]
           (h/event! "branch") (h/event! "branch")
           (h/observe! 2.0 "size") (h/observe! -3.5 "size")))
        summary (get-in result [:observations :exploration])]
    (is (:passed? result))
    (is (= :generation-only (get-in result [:observations :scope])))
    (is (= {:valid 1} (get-in summary [:events "branch"])))
    (is (= {:count 2 :min -3.5 :max 2.0} (get-in summary [:numeric "size" :valid])))
    (is (= 1 (get-in result [:coverage :checks 0 :hits])))
    (is (= (observations/empty-summary) (get-in result [:observations :final-replay])))))

(deftest native-dispatch-errors-do-not-publish-frontend-observations
  (let [data (atom (observations/empty-case))]
    (binding [h/*test-case* {:context :ctx :handle :case :observations data}]
      (with-redefs [hffi/event! (fn [& _] (throw (ex-info "native failed" {})))
                    hffi/event-value! (fn [& _] (throw (ex-info "native failed" {})))]
        (is (some? (error-of #(h/event! "not-recorded"))))
        (is (some? (error-of #(h/observe! 2 "not-recorded"))))
        (is (= (observations/empty-case) @data))))))

(deftest statistics-setting-reaches-native-configuration
  (let [original hffi/settings-set-show-statistics!
        settings (atom [])]
    (with-redefs [hffi/settings-set-show-statistics!
                  (fn [ctx handle enabled?]
                    (swap! settings conj enabled?)
                    (original ctx handle enabled?))]
      (doseq [enabled? [true false]]
        (is (:passed? (h/run-test! (assoc run-opts :show-statistics? enabled?)
                                  (fn [_]
                                    (h/event! "statistics/branch")
                                    (h/observe! 2 "statistics/value")))))))
    (is (= [true false] @settings))))

(deftest missed-coverage-is-not-a-native-counterexample
  (let [calls (atom 0)
        result (h/run-test!
                (assoc run-opts :coverage {:scope :generation-only
                                          :requirements {"missing" {}}})
                (fn [_] (swap! calls inc) (h/observe! 99 "missing")))]
    (is (= 1 @calls))
    (is (false? (:passed? result)))
    (is (= :coverage-failed (:status result)))
    (is (= 0 (:n-failures result)))
    (is (empty? (:failures result)))
    (is (empty? (:final result)))
    (is (= 0 (get-in result [:coverage :checks 0 :hits])))))

(deftest final-replay-does-not-satisfy-coverage-or-hide-property-failure
  (let [result
        (h/run-test!
         (assoc run-opts :coverage {:scope :exploration :requirements {"replay-only" {}}})
         (fn [_]
           (when (h/final?) (h/event! "replay-only"))
           (throw (ex-info "intentional failure" {:hegel/origin "observation/replay"}))))]
    (is (= :failed (:status result)))
    (is (false? (:passed? result)))
    (is (= 1 (:n-failures result)))
    (is (true? (get-in result [:failures 0 :reproduced?])))
    (is (false? (get-in result [:coverage :passed?])))
    (is (= 0 (get-in result [:coverage :checks 0 :hits])))
    (is (pos? (get-in result [:observations :final-replay :events "replay-only" :interesting])))))

(deftest zero-valid-and-rejected-observations-cannot-satisfy-requirements
  (let [case-data (observations/event (observations/empty-case) "rejected")
        opts {:coverage {:scope :exploration :requirements {"rejected" {:min-fraction 0}}}}
        initial (policy/initial opts)
        initial (update initial :exploration observations/record-case :invalid case-data)
        result (policy/finish {:passed? true :status :passed} opts initial)]
    (is (= :exploration (:scope initial)))
    (is (= :all (:phases initial)))
    (is (= :coverage-failed (:status result)))
    (is (= 0 (get-in result [:coverage :valid-cases])))
    (is (nil? (get-in result [:coverage :checks 0 :fraction])))
    (is (= 0 (get-in result [:coverage :checks 0 :hits])))))

(deftest native-rejected-cases-are-counted-but-do-not-satisfy-coverage
  (let [result
        (h/run-test!
         (assoc run-opts :test-cases 10
                :coverage {:scope :generation-only
                           :requirements {"rejected-only" {}}})
         (fn [_]
           (let [n (h/draw! (g/integer 0 1))]
             (when (zero? n) (h/event! "rejected-only"))
             (h/assume! (pos? n))
             (h/event! "accepted"))))]
    (is (pos? (:invalid-test-cases result)))
    (is (= (:invalid-test-cases result)
           (get-in result [:observations :exploration :events "rejected-only" :invalid])))
    (is (= (:valid-test-cases result)
           (get-in result [:observations :exploration :events "accepted" :valid])))
    (is (= :coverage-failed (:status result)))
    (is (= 0 (get-in result [:coverage :checks 0 :hits])))))

(deftest rounded-display-fractions-cannot-satisfy-exact-coverage
  (let [opts {:coverage {:scope :exploration
                         :requirements {"all" {:min-fraction 1.0}}}}
        observed (-> (policy/initial opts)
                     (assoc-in [:exploration :cases :valid] 9007199254740993N)
                     (assoc-in [:exploration :events "all" :valid] 9007199254740992N))
        failed (policy/finish {:passed? true :status :passed} opts observed)
        passed (policy/finish {:passed? true :status :passed} opts
                              (assoc-in observed [:exploration :events "all" :valid]
                                        9007199254740993N))]
    (is (= :coverage-failed (:status failed)))
    (is (:passed? passed))))

(defn- visit-depth! [depth]
  (if (zero? depth)
    (h/event! "depth/leaf")
    (visit-depth! (dec depth))))

(deftest mandatory-boundary-depth-and-state-witnesses-detect-omitted-branch
  (let [opts (assoc run-opts :coverage
                    {:scope :generation-only
                     :requirements {"boundary" {} "depth/leaf" {} "state/advanced" {}}})
        property (fn [emit-state?]
                   (fn [_]
                     (let [n (h/draw! (g/integer 8 8))]
                       (when (= n 8) (h/event! "boundary"))
                       (visit-depth! n)
                       (stateful/run!
                        {:initial-state 0
                         :rules [(stateful/rule :advance
                                                (fn [state]
                                                  (when emit-state? (h/event! "state/advanced"))
                                                  (inc state)))]}))))
        good (h/run-test! opts (property true))
        mutant (h/run-test! opts (property false))]
    (is (:passed? good))
    (is (= :coverage-failed (:status mutant)))
    (is (= ["state/advanced"]
           (mapv :label (remove :passed? (get-in mutant [:coverage :checks])))))))

(deftest coverage-failure-is-visible-to-both-reporting-integrations
  (let [opts (assoc run-opts :coverage {:scope :generation-only :requirements {"missing" {}}})
        events (atom [])
        result (binding [t/report #(swap! events conj %)]
                 (hc/run-with-reports! opts "coverage-test" (fn [] nil)))
        runner (report/counting-runner {:reporter (fn [_] nil)})]
    (is (= :coverage-failed (:status result)))
    (is (= [:fail] (mapv :type @events)))
    (is (= (:coverage result) (:actual (first @events))))
    (report/run! runner "coverage" (fn [] result))
    (is (= 1 (report/failure-count runner)))))

(deftest unchanged-runs-do-not-acquire-observation-results
  (let [result (h/run-test! run-opts (fn [_] (h/event! "uncollected")))]
    (is (:passed? result))
    (is (not (contains? result :observations)))
    (is (not (contains? result :coverage)))))

(defn -main [& _]
  (let [{:keys [fail error]} (t/run-tests 'hegel.observation-test)]
    (when (pos? (+ fail error))
      (throw (ex-info "observation tests failed" {:fail fail :error error})))))

(deftest rule-coverage-counts-steps-without-rechecking-the-selected-guard
  (doseq [census? [false true]]
    (let [guard-calls (atom 0)
          result (h/run-test!
                  (assoc run-opts :test-cases 6 :stateful-coverage
                         {:census? census? :rules {:advance {:min-applied 10} :blocked {:min-applied 1}}})
                  (fn [_]
                    (stateful/run!
                     {:initial-state 0
                      :rules [(stateful/rule :advance {:precondition (fn [_] (swap! guard-calls inc) true)} inc)
                              (stateful/rule :blocked {:precondition (constantly false)} inc)]})))
          {:keys [rules checks]} (:stateful-coverage result)]
      (is (= :coverage-failed (:status result)))
      (is (= (if census? (:eligible (:advance rules)) (:selected (:advance rules))) @guard-calls))
      (is (= (get-in checks [:advance :applied]) (get-in rules [:advance :applied])))
      (is (true? (get-in checks [:advance :passed?])))
      (is (false? (get-in checks [:blocked :passed?])))
      (is (= (when census? 0) (get-in rules [:blocked :eligible]))))))

(deftest confirmed-journal-redacts-and-counts-utf8-bytes
  (let [run (fn [opts]
              (h/run-test! (assoc run-opts :stateful-coverage {:rules {:absent {:min-applied 1}}})
                (fn [_]
                  (journal/run! opts
                    {:initial-state nil
                     :rules [(stateful/rule :write
                              (fn [_]
                                (journal/entry! {:input "private" :outcome "😀"})
                                (throw (ex-info "stop" {:hegel/origin "write"}))))]}))))
        opts {:redact #(dissoc % :input)}
        result (run opts)
        failure (first (:failures result))
        capture (::journal/journal (ex-data (:exception failure)))
        replay-size (:bytes capture)]
    (is (= :failed (:status result)))
    (is (= 1 (get-in result [:stateful-coverage :rules :write :failed])))
    (is (= [{:rule "write" :outcome "😀"}] (journal/fixture failure)))
    (is (= (journal/fixture failure)
           (journal/fixture (first (:failures (run (assoc opts :max-bytes replay-size)))))))
    (let [f (first (:failures (run (assoc opts :max-bytes (dec replay-size)))))]
      (is (= :incomplete-journal (:reason (ex-data (error-of #(journal/fixture f)))))))))
