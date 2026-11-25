(ns target-cases (:require [augur.ledger :as l] [augur.calibrate :as cal]))
(def opts {:max-open 4 :max-rows 3 :max-keys 3 :forget 1.0 :calibration (assoc cal/defaults :forget 1.0)})
(defn expiry [{:keys [n cap overflow?]}]
  (reduce (fn [s i]
            (let [s (l/register s {:id i :key i :p 0.5 :at i})]
              (if overflow? s (l/expire s (constantly true) {:at i}))))
          (l/empty (assoc opts :max-keys cap :max-open (if overflow? 1 4))) (range n)))
(defn benchmark [{:keys [n cap p]}]
  (reduce (fn [s i] (-> s (l/register {:id i :key i :p p :at i :bench {:baseline 0.5}})
                        (l/resolve i true {:at i})))
          (l/empty (assoc opts :max-keys cap)) (range n)))
