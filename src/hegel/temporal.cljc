(ns hegel.temporal
  "Internal conversion between public clock fields and the native nanosecond layout.")

(def precision-values
  {:nanoseconds 0 :microseconds 1 :milliseconds 2 :seconds 3 :minutes 4 :hours 5})

(defn native-time [value]
  (if (contains? value :microsecond)
    (-> value (dissoc :microsecond) (assoc :nanosecond (* 1000 (:microsecond value))))
    value))
