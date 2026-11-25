(require '[target-cases :as tc] '[augur.ledger :as l] '[clojure.edn :as edn])
(let [i (edn/read-string (slurp "../expiry-input.edn")) s (tc/expiry i)]
 (prn :expiry-input i :state s)
 (assert (= 2 (count (:by-key s))))
 (assert (= 0 (:dropped-keys s))))
(let [i (edn/read-string (slurp "../benchmark-input.edn"))
      e (try (tc/benchmark i) nil (catch IllegalArgumentException e e))]
 (prn :benchmark-input i :exception (str e))
 (assert e)
 (assert (.contains (str e) "ISeq")))
(assert (= 2.0 (reduce + (map #(get-in % [:sums :n] 0.0) (vals (:by-key (tc/benchmark {:n 2 :cap 1 :p 0.0})))))))
(let [s (tc/expiry {:n 3 :cap 1 :overflow? true})]
 (prn :automatic-expiry (:by-key s)) (assert (= 2 (count (:by-key s)))))
(let [s (tc/expiry {:n 100 :cap 1 :overflow? false})]
 (prn :expiry-growth (count (:by-key s))) (assert (= 100 (count (:by-key s)))))
(assert (nil? (find-ns 'hegel.core)))
(prn :target-only-reproduced)
