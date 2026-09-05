(ns hegel.float32-test
  (:require [clojure.test :refer [deftest is]]
            [hegel.core :as h]
            [hegel.ffi :as hffi]
            [hegel.generator :as g]
            [hegel.internal.binary32 :as binary32]))

(deftest exact-lattice-and-original-value-validation
  (doseq [value [0.0 -0.0 1 1/2 0.5M 16777216N
                1.401298464324817E-45 -1.401298464324817E-45
                1.1754943508222875E-38 1.0000001192092896
                3.4028234663852886E38
                1267650600228229401496703205376N]]
    (is (binary32/finite-exact? value) (str value)))
  (doseq [value [nil :x ##NaN ##Inf ##-Inf 0.1 1/10 0.1M
                1.0000000596046448 7.006492321624085E-46
                1.1754942807573643E-38 3.4028235E38
                16777217N 1267650600228229401496703205377N
                1.000000000000000000000000000001M
                1E-400M]]
    (is (not (binary32/finite-exact? value)) (str value))))

(deftest native-width-defaults-and-explicit-infinities
  (let [calls (atom [])]
    (with-redefs [hffi/generate-float! (fn [& args]
                                      (swap! calls conj (vec args)) 0.0)]
      ((g/float32) {:context :ctx :handle :tc})
      ((g/float32 {:nan? false :infinity? false}) {:context :ctx :handle :tc})
      ((g/float32 {:min ##-Inf :max ##Inf :infinity? true})
       {:context :ctx :handle :tc}))
    (is (= [:ctx :tc 32 ##-Inf ##Inf true true nil nil binary32/min-positive]
           (first @calls)))
    (is (= [:ctx :tc 32 (- binary32/max-finite) binary32/max-finite
            false false nil nil binary32/min-positive] (second @calls)))
    (is (= [:ctx :tc 32 ##-Inf ##Inf false true nil nil binary32/min-positive]
           (nth @calls 2)))))

(deftest invalid-options-fail-before-native-entry
  (let [calls (atom 0)]
    (with-redefs [hffi/generate-float! (fn [& _] (swap! calls inc))]
      (doseq [opts [{:min 0.1} {:max 0.1} {:min ##NaN}
                    {:min 1267650600228229401496703205377N}
                    {:min 1 :max 0} {:min 0 :nan? true}
                    {:min 0 :max 1 :infinity? true} {:nan? 1}
                    {:unknown true}]]
        (is (true? (try ((g/float32 opts) {}) false
                        (catch Throwable error
                          (:hegel/usage-error? (ex-data error))))))))
    (is (zero? @calls))))

(def ^:private opts {:test-cases 40 :seed 42 :database "" :verbosity :quiet})

(deftest native-boundaries-and-finite-domain
  (doseq [value [0.0 -0.0 binary32/min-positive (- binary32/min-positive)
                binary32/min-normal binary32/max-finite (- binary32/max-finite)]]
    (let [result (h/run-test! opts
                   (fn [_]
                     (let [draw (h/draw! (g/float32 value value))]
                       (when-not (== value draw)
                         (throw (ex-info "float32 singleton" {}))))))]
      (is (:passed? result))))
  (let [counted (atom 0)
        result (h/run-test! opts
                 (fn [_]
                   (let [draw (h/draw! (g/float32 {:nan? false :infinity? false}))]
                     (swap! counted inc)
                     (when-not (binary32/finite-exact? draw)
                       (throw (ex-info "not binary32" {}))))))]
    (is (:passed? result))
    (is (pos? @counted))))

(deftest native-exclusive-subnormal-boundary
  (let [result (h/run-test! opts
                 (fn [_]
                   (let [draw (h/draw! (g/float32
                                        {:min 0.0 :max binary32/min-positive
                                         :exclude-min? true}))]
                     (when-not (== binary32/min-positive draw)
                       (throw (ex-info "wrong successor of zero" {}))))))]
    (is (:passed? result))))

(deftest native-infinity-nan-and-shrinking-controls
  (doseq [value [##Inf ##-Inf]]
    (let [result (h/run-test! opts
                   (fn [_]
                     (let [draw (h/draw! (g/float32
                                          {:min value :max value :infinity? true}))]
                       (when-not (= value draw)
                         (throw (ex-info "infinity singleton" {}))))))]
      (is (:passed? result))))
  (let [seen (atom #{})
        result (h/run-test! (assoc opts :test-cases 200)
                 (fn [_]
                   (let [draw (h/draw! (g/float32))]
                     (swap! seen conj
                            (cond (not (== draw draw)) :nan
                                  (= draw ##Inf) :positive-infinity
                                  (= draw ##-Inf) :negative-infinity
                                  :else :finite)))))]
    (is (:passed? result))
    (is (= #{:nan :positive-infinity :negative-infinity :finite} @seen)))
  (let [final-value (atom nil)
        result (h/run-test! opts
                 (fn [_]
                   (let [draw (h/draw! (g/float32 {:min 1.0 :max 2.0
                                                 :exclude-min? true}))]
                     (when (h/final?) (reset! final-value draw))
                     (throw (ex-info "float32 shrink"
                                     {:hegel/origin "float32/shrink"})))))]
    (is (false? (:passed? result)))
    (is (false? (:flaky? result)))
    (is (seq (:failures result)))
    (is (every? :reproduced? (:failures result)))
    ;; Native float simplicity prefers an integral witness, not necessarily
    ;; the numerically smallest member of the interval.
    (is (== 2.0 @final-value))))
