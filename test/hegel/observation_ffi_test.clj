(ns hegel.observation-ffi-test
  (:require [clojure.test :refer [deftest is]]
            [hegel.core :as h]
            [hegel.ffi :as hffi]
            [hegel.ffi.backend :as backend]))

(deftest observation-wrappers-use-canonical-order-and-scoped-labels
  (let [calls (atom [])
        frees (atom [])]
    (with-redefs [backend/with-native-scope (fn [call] (call))
                  backend/string->native (fn [value]
                                           (swap! calls conj [:string value])
                                           [:native value])
                  backend/free #(swap! frees conj %)
                  hffi/c-settings-set-show-statistics
                  (fn [ctx settings enabled]
                    (swap! calls conj [:settings ctx settings enabled])
                    0)
                  hffi/c-event (fn [ctx test-case label]
                                 (swap! calls conj [:event ctx test-case label])
                                 0)
                  hffi/c-event-value (fn [ctx test-case value label]
                                       (swap! calls conj
                                              [:event-value ctx test-case value label])
                                       0)]
      (hffi/settings-set-show-statistics! :ctx :settings true)
      (hffi/settings-set-show-statistics! :ctx :settings false)
      (hffi/event! :ctx :case "coverage/branch")
      (hffi/event-value! :ctx :case 42.5 "coverage/size")
      (is (= [[:settings :ctx :settings 1]
              [:settings :ctx :settings 0]
              [:string "coverage/branch"]
              [:event :ctx :case [:native "coverage/branch"]]
              [:string "coverage/size"]
              [:event-value :ctx :case 42.5 [:native "coverage/size"]]]
             @calls))
      (is (= [[:native "coverage/branch"] [:native "coverage/size"]]
             @frees)))))

(deftest observation-wrapper-errors-retain-operation-and-release-label
  (doseq [[operation call expected-frees]
          [[:event #(hffi/event! nil :case "coverage/event") [:label]]
           [:event-value #(hffi/event-value! nil :case 1.0 "coverage/value")
            [:label]]
           [:settings-set-show-statistics
            #(hffi/settings-set-show-statistics! nil :settings true) []]]]
    (let [free (atom [])
        error
        (with-redefs [backend/with-native-scope (fn [call] (call))
                      backend/string->native (constantly :label)
                      backend/free #(swap! free conj %)
                      hffi/c-event (fn [& _] 7)
                      hffi/c-settings-set-show-statistics (fn [& _] 7)
                      hffi/c-event-value (fn [& _] 7)]
          (try
            ;; A nil context keeps this pure mock from reaching the private
            ;; native diagnostic accessor while still exercising check!.
            (call)
            nil
            (catch clojure.lang.ExceptionInfo error error)))]
    (is (= :hegel.ffi/error (:type (ex-data error))))
    (is (= operation (:operation (ex-data error))))
    (is (= "no libhegel diagnostic" (:diagnostic (ex-data error))))
    (is (= expected-frees @free)))))

(deftest native-observations-allow-repeated-labels
  ;; Exercise actual descriptor-backed native calls as well as the mocks.
  ;; This is not a test of printed statistics or structured coverage semantics.
  (let [result
        (h/run-test!
         {:test-cases 1 :seed 42 :database "" :verbosity :quiet
          :phases [:generate]}
         (fn [test-case]
           (let [ctx (:context test-case)
                 handle (:handle test-case)]
             (hffi/event! ctx handle "coverage/branch")
             (hffi/event! ctx handle "coverage/branch")
             (hffi/event-value! ctx handle 1.25 "coverage/size")
             (hffi/event-value! ctx handle -2.5 "coverage/size"))))]
    (is (:passed? result))
    (is (= 1 (:valid-test-cases result)))))

(defn -main [& _]
  (let [{:keys [fail error]} (clojure.test/run-tests 'hegel.observation-ffi-test)]
    (when (pos? (+ fail error))
      (throw (ex-info "observation FFI tests failed" {:fail fail :error error})))))
