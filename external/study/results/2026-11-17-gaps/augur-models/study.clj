(ns study
  (:require [augur.calibrate :as cal] [augur.beta :as beta] [augur.gamma :as gamma]
            [hegel.core :as h] [hegel.generator :as g]
            [hegel.replay-bundle :as bundle] [hegel.replay-bundle.codec :as codec]
            [clojure.edn :as edn]))
(defn close? [x y] (<= (Math/abs (- (double x) (double y))) 1e-10))
(defn check! [id input expected actual]
  (when-not (close? expected actual)
    (throw (ex-info "Model disagreement" {:hegel/origin id :input input :expected expected :actual actual}))))
(defn tied-input []
  {:p (/ (h/draw! (g/integer 0 100) "forecast-percent") 100.0)
   :ys (h/draw! (g/vector {:min-size 1 :max-size 20} (g/boolean)) "outcomes")})
(defn tied-check [{:keys [p ys] :as input}]
  (let [expected (/ (count (filter true? ys)) (double (count ys)))
        actual ((cal/isotonic-map (repeat (count ys) p) ys) p)]
    (check! "tied-fit-empirical-rate" input expected actual)))
(defn tied-property [_] (tied-check (tied-input)))
(def online-opts {:edges [0.5 1.0] :shrink 10.0 :forget 1.0})
(defn online-check [{:keys [successes failures] :as input}]
  (let [rows (concat (repeat successes {:key :k :p 0.25 :outcome true})
                     (repeat failures {:key :k :p 0.75 :outcome false}))
        table (cal/learn nil rows online-opts)
        low (cal/calibrate table :k 0.5 online-opts)
        high (cal/calibrate table :k 0.500001 online-opts)]
    (when (> low (+ high 1e-10))
      (throw (ex-info "Higher forecast calibrated lower" {:hegel/origin "online-monotonicity" :input input :low low :high high})))))
(defn online-property [_]
  (online-check {:successes (h/draw! (g/integer 0 100) "low-bin-successes")
                 :failures (h/draw! (g/integer 0 100) "high-bin-failures")}))
;; Independent max-min characterization of weighted isotonic regression.
(defn iso-model [ys ws]
  (let [n (count ys)
        avg (fn [a b] (/ (reduce + (map * (subvec ys a (inc b)) (subvec ws a (inc b))))
                          (double (reduce + (subvec ws a (inc b))))))]
    (mapv (fn [i] (apply max (for [a (range (inc i))]
                              (apply min (for [b (range i n)] (avg a b)))))) (range n))))
(defn isotonic-property [_]
  (let [n (h/draw! (g/integer 1 12))
        ys (h/draw! (g/vector {:size n} (g/integer -20 20)))
        ws (h/draw! (g/vector {:size n} (g/integer 1 20)))
        expected (iso-model ys ws) actual (cal/isotonic ys ws)]
    (doseq [[x y] (map vector expected actual)] (check! "weighted-isotonic" {:ys ys :ws ws} x y))))
(defn posterior-property [_]
  (let [a (h/draw! (g/integer 1 20)) b (h/draw! (g/integer 1 20))
        n (h/draw! (g/integer 0 100)) k (h/draw! (g/integer 0 n))
        p (beta/prior a b) q (beta/from-counts k n p)
        exposure (h/draw! (g/integer 1 100)) rate (gamma/prior a b)
        gp (gamma/observe rate {:count k :exposure exposure})]
    (check! "beta-mean" [a b n k] (/ (+ a k) (double (+ a b n))) (beta/mean q))
    (check! "beta-evidence" [a b n k] n (beta/evidence q))
    (check! "gamma-mean" [a b k exposure] (/ (+ a k) (double (+ b exposure))) (gamma/mean gp))
    (check! "gamma-evidence" [a b k exposure] exposure (gamma/evidence gp))))
(def props {"tied" tied-property "online" online-property "isotonic" isotonic-property "posterior" posterior-property})
(defn provenance [id] (assoc (edn/read-string (slurp "provenance.edn")) :property-id (str "augur-models/" id)))
(defn summary [r] (select-keys r [:passed? :status :seed :test-cases :valid-test-cases :invalid-test-cases :interesting-test-cases :n-failures :flaky?]))
(defn run-one [id expected-pass?]
  (let [r (h/run-test! {:name (str "augur-models/" id) :test-cases 500 :seed 98271 :database "" :verbosity :normal} (props id))]
    (prn id (summary r))
    (assert (= expected-pass? (:passed? r)) (str "Unexpected verdict: " id))
    (assert (not (:flaky? r)))
    (spit (str id "-summary.edn") (pr-str (summary r)))
    (when-not expected-pass?
      (let [data (-> r :final first :exception ex-data)]
        (assert (:input data))
        (prn :minimum id data)
        (spit (str id "-minimum.edn") (pr-str (:input data)))
      (spit (str id "-bundle.edn") (codec/encode (bundle/from-result (provenance id) r)))))))
(defn -main [& [mode]]
  (case mode
    "generate" (do (run-one "tied" false) (run-one "online" false) (run-one "isotonic" true) (run-one "posterior" true))
    "replay" (doseq [id ["tied" "online"]]
               (let [b (codec/decode (slurp (str id "-bundle.edn")))
                     r (h/replay-bundle! (provenance id) b (props id))
                     called (atom false)
                     incompatible (h/replay-bundle! (assoc (provenance id) :generator-revision "mismatch") b (fn [_] (reset! called true)))]
                 (prn :replay id (select-keys r [:status :reproduced? :flaky?]))
                 (assert (= :reproduced (:status r))) (assert (:reproduced? r)) (assert (not (:flaky? r)))
                 (assert (= :incompatible (:status incompatible))) (assert (not @called))
                 (prn :provenance-mismatch-rejected id)))
    (throw (ex-info "Expected generate or replay" {}))))
