(ns controls (:require [study :as s] [hegel.core :as h] [hegel.generator :as g] [augur.calibrate :as cal]))
(defn -main [& _]
  ;; Explicit witnesses complement random generation; these are good target behavior.
  (doseq [[ys ws] [[[] []] [[3 2 1] [1 1 1]] [[1 2 3] [2 3 4]] [[1 1 1] [1 8 2]] [[3 -2 7 0] [20 1 2 4]]]]
    (let [a (cal/isotonic ys ws) b (if (empty? ys) [] (s/iso-model ys ws))]
      (assert (= (count a) (count b)))
      (doseq [[x y] (map vector a b)] (assert (s/close? x y)))))
  ;; Semantic negative control: an unpooled identity fit violates the independent model.
  ;; This is pack-local and is not a target mutation or alleged defect.
  (let [r (h/run-test! {:test-cases 100 :seed 98271 :database "" :verbosity :normal}
                      (fn [_]
                        (let [a (h/draw! (g/integer 1 20)) ys [a 0] expected (s/iso-model ys [1 1])]
                          (s/check! "control/unpooled" ys (first expected) (first ys)))))]
    (prn :semantic-negative-control (s/summary r))
    (assert (false? (:passed? r))) (assert (= :failed (:status r))) (assert (not (:flaky? r)))
    (assert (= "control/unpooled" (-> r :final first :exception ex-data :hegel/origin))))
  (doseq [id ["isotonic" "posterior"]]
    (let [r (h/run-test! {:test-cases 500 :seed 98271 :database "" :verbosity :normal} (s/props id))]
      (prn :positive-property id (s/summary r))
      (assert (true? (:passed? r))) (assert (not (:flaky? r)))))
  (prn :explicit-witnesses-passed))
