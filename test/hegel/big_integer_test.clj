(ns hegel.big-integer-test
  (:require [clojure.test :refer [deftest is]]
            [hegel.core :as h]
            [hegel.generator :as g]))

(def ^:private two-to-128 340282366920938463463374607431768211456N)
(def ^:private opts
  {:test-cases 20 :database "" :seed 1 :verbosity :quiet})

(deftest singleton-and-signed-boundaries
  (doseq [value [0 127 128 -128 -129
                9223372036854775808N -9223372036854775809N
                two-to-128 (-' two-to-128)]]
    (let [result (h/run-test! opts
                   (fn [_]
                     (when-not (= value (h/draw! (g/big-integer value value)))
                       (throw (ex-info "singleton mismatch" {})))))]
      (is (:passed? result)))))

(deftest signed-arbitrary-width-range-and-shrinking
  (let [result (h/run-test! opts
                 (fn [_]
                   (let [value (h/draw! (g/big-integer
                                         {:min (-' two-to-128)
                                          :max two-to-128}))]
                     (when-not (and (integer? value)
                                    (<= (-' two-to-128) value two-to-128))
                       (throw (ex-info "out of range" {}))))))]
    (is (:passed? result)))
  (let [final-value (atom nil)
        result (h/run-test! opts
                 (fn [_]
                   (let [value (h/draw! (g/big-integer two-to-128
                                                     (+' two-to-128 1000)))]
                     (when (h/final?) (reset! final-value value))
                     (throw (ex-info "shrink positive big integer"
                                     {:hegel/origin "big-integer/shrink"})))))]
    (is (false? (:passed? result)))
    (is (not (:flaky? result)))
    (is (seq (:failures result)))
    (is (every? :reproduced? (:failures result)))
    (is (= two-to-128 @final-value))))

(deftest negative-domain-shrinks-toward-zero
  (let [upper (-' two-to-128)
        final-value (atom nil)
        result (h/run-test! opts
                 (fn [_]
                   (let [value (h/draw! (g/big-integer (-' upper 1000) upper))]
                     (when (h/final?) (reset! final-value value))
                     (throw (ex-info "shrink negative big integer"
                                     {:hegel/origin "big-integer/negative-shrink"})))))]
    (is (false? (:passed? result)))
    (is (false? (:flaky? result)))
    (is (seq (:failures result)))
    (is (every? :reproduced? (:failures result)))
    (is (= upper @final-value))))

(deftest invalid-bounds-are-usage-errors
  (doseq [thunk [#(g/big-integer {}) #(g/big-integer {:min 0})
                #(g/big-integer nil 1) #(g/big-integer 1.5 2)
                #(g/big-integer 2 1) #(g/big-integer {:min 0 :max 1 :width 128})]]
    (is (true? (try (thunk) false
                    (catch Throwable error (:hegel/usage-error? (ex-data error))))))))
