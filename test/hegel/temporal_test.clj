(ns hegel.temporal-test
  (:require [clojure.test :refer [deftest is]]
            [hegel.temporal :as temporal]))

(deftest public-clock-bounds-map-to-exact-native-ticks
  (doseq [microsecond [0 1 123456 999999]]
    (let [clock {:hour 23 :minute 59 :second 59}
          native (assoc clock :nanosecond (* microsecond 1000))]
      (is (= native (temporal/native-time (assoc clock :microsecond microsecond))))
      (is (= native (temporal/native-time native)))))
  (let [submicrosecond {:hour 0 :minute 0 :second 0 :nanosecond 999}]
    (is (= submicrosecond (temporal/native-time submicrosecond)))))
