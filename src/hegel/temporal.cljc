(ns hegel.temporal
  "Internal pure precision adapters between validated public microsecond maps
  and the pinned native nanosecond ABI. These functions do not draw values,
  validate caller options, or promise unchanged engine choice streams.")

(defn- native-time [value remainder]
  (-> value
      (dissoc :microsecond)
      (assoc :nanosecond (+ (* 1000 (:microsecond value)) remainder))))

(defn native-time-bounds
  "Return the full native preimage of inclusive public microsecond bounds."
  [minimum maximum]
  [(native-time minimum 0) (native-time maximum 999)])

(defn public-time
  "Project a valid native time onto the public microsecond domain, rounding
  down within its second. Never reject a valid sub-microsecond native draw."
  [value]
  (-> value
      (dissoc :nanosecond)
      (assoc :microsecond (quot (:nanosecond value) 1000))))

(defn native-datetime-bounds [minimum maximum]
  (let [[lo hi] (native-time-bounds (:time minimum) (:time maximum))]
    [(assoc minimum :time lo) (assoc maximum :time hi)]))

(defn public-datetime [value]
  (update value :time public-time))
