(ns hegel.suites.clojure-test
  "clojure.test integration contract scenario, loaded only when selected."
  (:require [clojure.string :as str]
            [clojure.test :as t]
            [hegel.clojure-test :as ht]
            [hegel.core :as h]
            [hegel.ffi :as hffi]
            [hegel.generator :as g]
            [hegel.test-support :as support]))

(t/deftest embedded-hegel-property
  (ht/with {:test-cases 20
            :seed 20260727
            :database ""
            :verbosity :quiet}
    [xs (g/vector {:max-size 8} (g/integer -10 10))]
    (t/is (= xs (vec xs)))))

(defn clojure-test-integration [context]
  (let [events (atom [])]
    (with-redefs [t/report #(swap! events conj %)]
      (t/test-var #'embedded-hegel-property))
    (support/check! context "a real clojure.test deftest can host a passing Hegel property"
           (= [:pass]
              (into []
                    (comp (map :type)
                          (filter #{:pass :fail :error}))
                    @events))))
  (let [events (atom [])
        error
        (with-redefs [t/report #(swap! events conj %)
                      hffi/generate-integer!
                      (fn [& _]
                        (throw
                         (ex-info "native harness failed"
                                  {:type ::hffi/error
                                   :operation :generate-integer
                                   :result 3})))]
          (try
            (ht/with {:test-cases 1
                      :seed 20260818
                      :database ""
                      :verbosity :quiet}
              [x (g/integer 0 1)]
              (t/is (<= 0 x 1)))
            nil
            (catch Throwable error
              error)))]
    (support/check! context "clojure.test properties preserve native harness errors"
           (and (= ::hffi/error (:type (ex-data error)))
                (empty? @events))))
  (let [events (atom [])
        calls (atom 0)
        result
        (with-redefs [t/report #(swap! events conj %)]
          (ht/with {:test-cases 1
                    :seed 20260819
                    :database ""
                    :verbosity :quiet}
            []
            (h/assume! (> (swap! calls inc) 1))))]
    (support/check! context "clojure.test properties preserve assumption control flow"
           (and (:passed? result)
                (= 1 (:invalid-test-cases result))
                (= [:pass] (mapv :type @events)))))
  (let [events (atom [])
        final-values (atom [])
        result
        (with-redefs [t/report #(swap! events conj %)]
          (ht/with {:test-cases 100
                    :seed 1
                    :database ""
                    :report-multiple-failures? false
                    :verbosity :quiet}
            [x (g/integer 0 100)]
            (when (h/final?)
              (swap! final-values conj x))
            (t/is (< x 10))))
        failure (first (:failures result))]
    (support/check! context "a failing clojure.test assertion is shrunk and reproduced"
           (and (not (:passed? result))
                (:reproduced? failure)
                (= [10] @final-values)))
    (support/check! context "only the final minimal clojure.test failure is reported"
           (and (= [:fail] (mapv :type @events))
                (str/includes? (pr-str (:actual (first @events))) "10")
                (str/includes? (:message (first @events))
                               "Hegel seed: 1")))
    (support/check! context "clojure.test origins are stable and independent of drawn values"
           (and (str/includes? (:origin failure) "hegel/suites/clojure_test.clj:")
                (str/ends-with? (:origin failure) ":(< x 10)"))))
  (let [events (atom [])
        result
        (with-redefs [t/report #(swap! events conj %)]
          (ht/with {:test-cases 1
                    :seed 4242
                    :database ""
                    :verbosity :quiet
                    :suppress-health-checks [:large-initial-test-case]}
            []
            (aget (byte-array 0) 0)))
        event (first @events)]
    (support/check! context "blank native exception messages retain an identifiable cause"
           (and (not (:passed? result))
                (= [:fail] (mapv :type @events))
                (str/includes? (:actual event) "out of bounds")
                (str/includes? (:message event) "Hegel seed: 4242"))))
  (let [events (atom [])
        result
        (with-redefs [t/report #(swap! events conj %)]
          (ht/with {:test-cases 1
                    :seed 4244
                    :database ""
                    :verbosity :quiet
                    :suppress-health-checks [:large-initial-test-case]}
            []
            (throw (ex-info "" {:detail :present}))))
        event (first @events)
        failure-data (some-> result :failures first :exception ex-data)]
    (support/check! context "blank ex-info messages retain exception data"
           (and (not (:passed? result))
                (str/includes? (:actual event)
                               "ex-data: {:detail :present}")
                (= {:detail :present}
                   (::ht/cause-data failure-data)))))
  (let [events (atom [])
        calls (atom 0)
        result
        (with-redefs [t/report #(swap! events conj %)]
          (ht/with {:test-cases 1
                    :seed 17
                    :database ""
                    :verbosity :quiet
                    :suppress-health-checks [:large-initial-test-case]}
            []
            (h/draw! (g/integer 0 10))
            (when (= 1 (swap! calls inc))
              (throw (ex-info "transient" {})))))
        event (first @events)]
    (support/check! context "clojure.test reports engine flakiness without aborting the suite"
           (and (= :error (:status result))
                (true? (:flaky? result))
                (= [:fail] (mapv :type @events))
                (str/starts-with? (:actual event) "Flaky test detected:")
                (str/includes? (:message event) "Hegel seed: 17")))))
