(ns hegel.materialize-test
  (:require [clojure.test :refer [deftest is]]
            [hegel.core :as core]
            [hegel.corpus :as corpus]
            [hegel.generator :as g]
            [hegel.host :as host]
            [hegel.materialize :as materialize]
            [hegel.version :as version]))

(def provenance
  {:hegel-sha "0123456789abcdef0123456789abcdef01234567"
   :libhegel-version version/libhegel-version
   :runtime {:host (host/runtime) :version "test-runtime"
             :os "test-os" :arch "test-arch"}
   :property-id "example/materialize"
   :generator-revision "generator-v1"
   :model-revision nil
   :seam-revision "seam-v1"})

(def options {:seed "18446744073709551615" :count 2 :provenance provenance})

(def passing-run
  {:passed? true :status :passed :flaky? false :n-failures 0
   :valid-test-cases 1})

(defn- error [thunk]
  (try (thunk) nil (catch clojure.lang.ExceptionInfo error error)))

(defn- expected-for [envelope]
  {:sha256 (:sha256 envelope)
   :provenance provenance
   :count 2
   :valid-case-policy :exact-valid-count})

(deftest mock-runs-positions-sequentially-with-exact-engine-settings
  (let [runs (atom [])
        checks (atom [])
        values (atom [[:first] [:second]])
        envelope (with-redefs [core/draw! (fn [generator] (generator :case))
                               core/run-test! (fn [run-options case-fn]
                                                (swap! runs conj run-options)
                                                (case-fn :case)
                                                passing-run)]
                   (materialize/materialize!
                    options
                    (fn [_]
                      (let [value (first @values)]
                        (swap! values subvec 1)
                        value))
                    #(swap! checks conj %)))]
    (is (= [[:first] [:second]] @checks))
    (is (= [{:seed 18446744073709551615N :test-cases 1 :backend :default
             :phases [:generate] :database "" :verbosity :quiet
             :report-multiple-failures? false}
            {:seed 0N :test-cases 1 :backend :default
             :phases [:generate] :database "" :verbosity :quiet
             :report-multiple-failures? false}]
           @runs))
    (is (= {:provenance provenance :seed "18446744073709551615" :count 2
            :valid-case-policy :exact-valid-count :values [[:first] [:second]]}
           (corpus/consume! (expected-for envelope) envelope)))))

(deftest mock-verdict-contradictions-never-return-a-partial-envelope
  (doseq [[run invoke-times expected]
          [[(assoc passing-run :passed? false :status :failed :n-failures 1)
            1 :passed?]
           [passing-run 0 :callback-count]
           [passing-run 2 :callback-count]
           [(assoc passing-run :flaky? true) 1 :flaky?]
           [(dissoc passing-run :n-failures) 1 :n-failures]
           [(assoc passing-run :valid-test-cases 0) 1 :valid-test-cases]]]
    (let [seen (atom 0)
          failure
          (with-redefs [core/draw! (fn [generator] (generator :case))
                        core/run-test! (fn [_ case-fn]
                                         (dotimes [_ invoke-times] (case-fn :case))
                                         run)]
            (error #(materialize/materialize!
                     (assoc options :count 1) (fn [_] (swap! seen inc)))))]
      (is (= :hegel.materialize/materialization-failed
             (:type (ex-data failure))))
      (is (= (if (= expected :callback-count) invoke-times (get run expected))
             (get (ex-data failure) expected)))
      (is (not (contains? (ex-data failure) :values)))
      (is (= invoke-times @seen)))))

(deftest validation-and-run-errors-do-not-start-or-reclassify-a-run
  (let [calls (atom 0)
        boom (ex-info "native/setup identity" {:type ::same})]
    (with-redefs [core/run-test! (fn [& _] (swap! calls inc) (throw boom))]
      (doseq [[bad generator] [[(assoc options :unknown true) (fn [_] 1)]
                               [(assoc options :seed "01") (fn [_] 1)]
                               [(assoc options :provenance
                                       (assoc provenance :libhegel-version "wrong"))
                                (fn [_] 1)]
                               [options {}]]]
        (is (true? (:hegel/usage-error?
                    (ex-data (error #(materialize/materialize! bad generator))))))
        (is (zero? @calls)))
      (is (true? (:hegel/usage-error?
                  (ex-data (error #(materialize/materialize!
                                    options (fn [_] 1) {}))))))
      (is (zero? @calls))
      (let [observed (error #(materialize/materialize! options (fn [_] 1)))]
        (is (identical? boom observed))
        (is (= 1 @calls))))))

(deftest ^:native native-materialization-control
  ;; Run the actual generator path with the selected compatible libhegel asset.
  (let [envelope (materialize/materialize!
                  (assoc options :count 1 :seed "7")
                  (g/integer 0 0) (fn [_] false))
        expected {:sha256 (:sha256 envelope)
                  :provenance provenance
                  :count 1
                  :valid-case-policy :exact-valid-count}]
    (is (= [0] (:values (corpus/consume! expected envelope))))))

(defn- values-of [envelope count]
  (:values (corpus/consume! {:sha256 (:sha256 envelope)
                            :provenance provenance :count count
                            :valid-case-policy :exact-valid-count}
                           envelope)))

(deftest ^:native constant-values-are-neither-sentinels-nor-deduplicated
  (doseq [value [nil :hegel.materialize/not-captured "equal"]]
    (let [envelope (materialize/materialize! (assoc options :count 3)
                                            (g/just value))]
      (is (= [value value value] (values-of envelope 3))))))

(deftest ^:native seeded-generation-repeats-and-wraps-uint64
  (let [generator (g/integer -1000000 1000000)
        opts (assoc options :count 12)
        first-run (materialize/materialize! opts generator)
        second-run (materialize/materialize! opts generator)
        first-value (materialize/materialize! (assoc opts :count 1) generator)
        zero-value (materialize/materialize! (assoc opts :count 1 :seed "0") generator)
        values (values-of first-run 12)]
    (is (= first-run second-run))
    (is (> (count (distinct values)) 1))
    (is (= (first values) (first (values-of first-value 1))))
    (is (= (second values) (first (values-of zero-value 1))))))

(deftest ^:native rejected-candidates-are-not-collected
  (let [attempts (atom 0)
        envelope (materialize/materialize!
                  (assoc options :count 3 :seed "10")
                  (g/integer 0 100)
                  (fn [_]
                    ;; Reject one candidate per position, before collection.
                    (core/assume! (even? (swap! attempts inc)))))]
    (is (= 3 (count (values-of envelope 3))))
    (is (= 6 @attempts))))

(deftest ^:native property-failures-and-aborts-cannot-publish-a-corpus
  (let [failure (error #(materialize/materialize!
                        (assoc options :count 1) (g/just "private")
                        (fn [_] (throw (ex-info "private-failure" {})))))]
    (is (= :hegel.materialize/materialization-failed (:type (ex-data failure))))
    (is (not (.contains (pr-str (ex-data failure)) "private"))))
  (doseq [data [{:hegel/usage-error? true}
               {:hegel/inconclusive? true}]]
    (let [abort (ex-info "abort identity" data)]
      (is (identical? abort
                      (error #(materialize/materialize!
                               (assoc options :count 1) (g/just 0)
                               (fn [_] (throw abort)))))))))

(deftest prefix-bounds-stop-before-the-next-native-position
  (let [runs (atom 0)]
    (with-redefs [core/draw! (fn [_] (apply str (repeat 8193 "x")))
                  core/run-test! (fn [_ case-fn]
                                   (swap! runs inc)
                                   (case-fn :case)
                                   passing-run)]
      (let [failure (error #(materialize/materialize! options (fn [_] nil)))]
        (is (= :max-string-chars (:reason (ex-data failure))))
        (is (true? (:hegel/usage-error? (ex-data failure))))
        (is (= 1 @runs))))))

(defn -main [& _]
  (let [{:keys [fail error]} (clojure.test/run-tests 'hegel.materialize-test)]
    (when (pos? (+ fail error))
      (throw (ex-info "materialize tests failed" {:fail fail :error error})))))
