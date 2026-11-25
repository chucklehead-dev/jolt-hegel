(ns target-repro (:require [augur.calibrate :as cal] [clojure.edn :as edn]))
(defn near [a b] (< (Math/abs (- a b)) 1e-10))
(defn -main [& _]
  (let [{:keys [p ys]} (edn/read-string (slurp "tied-minimum.edn"))]
    (assert (= {:p 0.0 :ys [false true]} {:p p :ys ys}))
    (doseq [p [p 0.5 1.0]]
      (let [a ((cal/isotonic-map [p p] ys) p)
            b ((cal/isotonic-map [p p] (reverse ys)) p)]
        (prn :tied {:p p :forward a :reverse b :expected 0.5})
        (assert (near a 1.0)) (assert (near b 0.5)))))
  (let [{:keys [successes failures] :as input} (edn/read-string (slurp "online-minimum.edn"))
        opts {:edges [0.5 1.0] :shrink 10.0 :forget 1.0}
        rows (concat (repeat successes {:key :k :p 0.25 :outcome true})
                     (repeat failures {:key :k :p 0.75 :outcome false}))
        table (cal/learn nil rows opts)
        lo (cal/calibrate table :k 0.5 opts) hi (cal/calibrate table :k 0.500001 opts)]
    (assert (= {:successes 0 :failures 1} input))
    (prn :online {:table table :low lo :high hi :expected "low <= high"})
    (assert (near lo 0.5)) (assert (near hi (/ 5.00001 11.0))) (assert (> lo hi)))
  (prn :known-target-defects-confirmed))
