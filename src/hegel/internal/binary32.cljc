(ns hegel.internal.binary32
  "Exact binary32 lattice checks without relying on host float narrowing.")

(def min-positive 1.401298464324817E-45)
(def min-normal 1.1754943508222875E-38)
(def max-finite 3.4028234663852886E38)

(defn- integer-power-two [exponent]
  (loop [n exponent result 1N]
    (if (zero? n) result (recur (dec n) (*' result 2)))))

(defn finite-exact?
  "Whether the original numeric value is a finite binary32 value.
  Exact integers, ratios and decimals must not become valid through rounding."
  [value]
  (and (number? value)
       (let [x (double value)
             a (if (neg? x) (- x) x)]
         (and (<= a max-finite)
              (if (zero? a)
                (zero? value)
                (let [[unit exponent]
                      (if (< a min-normal)
                        [min-positive -149]
                        (loop [power min-normal e -126]
                          (if (< a (* power 2.0))
                            [(/ power 8388608.0) (- e 23)]
                            (recur (* power 2.0) (inc e)))))
                      scaled (/ x unit)
                      significand (long scaled)]
                  ;; Scaling a binary64 by a power of two is exact here;
                  ;; the significand is bounded by 2^24, safely inside int64.
                  (and (== scaled (double significand))
                       (or (float? value)
                           (if (neg? exponent)
                             (== (*' value (integer-power-two (- exponent)))
                                 significand)
                             (== value (*' significand
                                           (integer-power-two exponent))))))))))))
