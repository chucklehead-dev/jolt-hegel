(ns hegel.temporal-test
  (:require [clojure.test :refer [deftest is]]
            [hegel.temporal :as temporal]))

(defn- time-value [microsecond]
  {:hour 23 :minute 59 :second 59 :microsecond microsecond})

(deftest inclusive-bounds-cover-whole-public-buckets
  (doseq [minimum [0 1 123456 999998 999999]
          maximum [0 1 123456 999998 999999]
          :when (<= minimum maximum)]
    (let [lo (time-value minimum)
          hi (time-value maximum)
          [native-lo native-hi] (temporal/native-time-bounds lo hi)]
      (is (= {:hour 23 :minute 59 :second 59
              :nanosecond (* minimum 1000)} native-lo))
      (is (= {:hour 23 :minute 59 :second 59
              :nanosecond (+ (* maximum 1000) 999)} native-hi))
      (is (= lo (temporal/public-time native-lo)))
      (is (= hi (temporal/public-time native-hi)))
      (is (<= 0 (:nanosecond native-lo) (:nanosecond native-hi) 999999999)))))

(deftest projection-accepts-sub-microsecond-native-results
  (doseq [[ns-value expected] [[0 0] [1 0] [999 0] [1000 1]
                               [1001 1] [123456789 123456]
                               [999999999 999999]]]
    (is (= (time-value expected)
           (temporal/public-time {:hour 23 :minute 59 :second 59
                                  :nanosecond ns-value})))))

(deftest fixed-public-bounds-stay-fixed-for-every-native-remainder
  (doseq [microsecond [0 1 123456 999999]]
    (let [expected (time-value microsecond)]
      (is (every? #(= expected
                       (temporal/public-time
                        {:hour 23 :minute 59 :second 59
                         :nanosecond (+ (* microsecond 1000) %)}))
                  (range 1000))))))

(deftest datetime-projection-preserves-endpoint-dates-and-midnight
  (doseq [[lo-date hi-date] [[{:year 1 :month 1 :day 1}
                             {:year 9999 :month 12 :day 31}]
                            [{:year 2024 :month 2 :day 29}
                             {:year 2024 :month 3 :day 1}]
                            [{:year 2024 :month 2 :day 29}
                             {:year 2024 :month 2 :day 29}]]]
    (let [lo {:date lo-date :time {:hour 0 :minute 0 :second 0 :microsecond 0}}
          hi {:date hi-date :time (time-value 999999)}
          [native-lo native-hi] (temporal/native-datetime-bounds lo hi)]
      (is (= lo-date (:date native-lo)))
      (is (= hi-date (:date native-hi)))
      (is (= 0 (get-in native-lo [:time :nanosecond])))
      (is (= 999999999 (get-in native-hi [:time :nanosecond])))
      (is (= lo (temporal/public-datetime native-lo)))
      (is (= hi (temporal/public-datetime native-hi))))))

(deftest projection-controls-distinguish-unscaled-and-round-up-mutants
  (let [native {:hour 23 :minute 59 :second 59 :nanosecond 1999}
        actual (temporal/public-time native)
        unscaled (assoc (dissoc native :nanosecond) :microsecond 1999)
        rounded-up (assoc (dissoc native :nanosecond) :microsecond 2)]
    (is (= (time-value 1) actual))
    (is (not= actual unscaled))
    (is (not= actual rounded-up))))
