(ns study (:require [hegel.core :as h] [hegel.generator :as g] [hegel.stateful :as hs]
 [hegel.replay-bundle :as rb] [hegel.replay-bundle.codec :as codec]
 [clojure.edn :as edn] [clojure.string :as str] [augur.ledger :as l] [target-cases :as tc]))
(defn fail! [origin input details] (throw (ex-info origin {:hegel/origin origin :input input :details details})))
(defn provenance [id]
 {:hegel-sha (str/trim (slurp "hegel-sha.txt")) :libhegel-version "0.51.6"
  :runtime {:host :jolt :version "0.8.27" :os "Linux" :arch "amd64"}
  :property-id id :generator-revision "ledger-study-v1" :model-revision "ledger-contract-v1"})
(defn expiry-property [_]
 (let [input {:n (h/draw! (g/integer 1 8)) :cap (h/draw! (g/integer 1 3)) :overflow? (h/draw! (g/boolean))}
       s (tc/expiry input) live (dissoc (:by-key s) :augur.ledger/other)]
   (when (> (count live) (:cap input)) (fail! "expiry-key-bound" input {:live (count live) :state s}))))
(defn benchmark-property [_]
 (let [input {:n (h/draw! (g/integer 1 8)) :cap (h/draw! (g/integer 1 3)) :p (/ (h/draw! (g/integer 0 4)) 4.0)}
       s (try (tc/benchmark input) (catch Exception e (fail! "benchmark-eviction-total" input {:exception (str e)})))
       total (reduce + (map #(get-in % [:sums :n] 0) (vals (:by-key s))))]
   (when (not= (double (:n input)) total) (fail! "benchmark-eviction-total" input {:total total}))))
(defn near [a b] (< (Math/abs (- (double a) (double b))) 1e-10))
(defn expire-model [m ids]
 (reduce (fn [m id] (let [k (get-in m [:open id :key])]
                     (-> m (update :open dissoc id) (update-in [:expired k] (fnil inc 0))))) m ids))
(defn step [s op]
 (let [{:keys [sut model opts t]} s
       [sut model] (case (first op)
        :register (let [[_ k p] op f {:id t :key k :p p :at t :x {:ordinal t}}
                        m (assoc-in model [:open t] f)
                        ids (take (max 0 (- (count (:open m)) (:max-open opts))) (sort (keys (:open m))))]
                    [(l/register sut f) (expire-model m ids)])
        :resolve (let [[_ id y] op f (get-in model [:open id])]
                   [(l/resolve sut id y {:at t})
                    (if f (-> model (update :open dissoc id)
                              (update-in [:resolved (:key f)] (fnil conj [])
                               {:id id :p (:p f) :y y :at (:at f) :resolved t :x (:x f)}))
                        (update model :unknown inc))])
        :expire (let [[_ k] op ids (for [[id f] (:open model) :when (= k (:key f))] id)]
                  [(l/expire sut #(= k (:key %)) {:at t}) (expire-model model ids)])
        :roundtrip [(edn/read-string (pr-str sut)) model])]
  (assoc s :sut sut :model model :t (inc t) :history (conj (:history s) op))))
(defn consistent! [{:keys [sut model opts history]}]
 (let [input {:opts opts :history history}
       check #(when-not %1 (fail! "state-model" input %2))]
  (check (= (:open model) (into {} (map (fn [[id f]] [id (assoc f :id id)]) (:open sut)))) :open)
  (check (= (:unknown model) (:unknown sut)) :unknown)
  (doseq [k (range 4)]
   (let [rows (get-in model [:resolved k] []) r (l/record sut k)
         brier (reduce + (map #(let [d (- (:p %) (if (:y %) 1.0 0.0))] (* d d)) rows))]
    (check (= (vec (take-last (:max-rows opts) rows)) (l/rows sut k)) [:rows k])
    (check (= (double (count rows)) (:n r)) [:n k])
    (check (= (get-in model [:expired k] 0) (:expired r)) [:expired k])
    (when (seq rows) (check (near (/ brier (count rows)) (:brier r)) [:brier k])))) true))
(defn model-property [_]
 (let [opts (assoc tc/opts :max-keys 10 :max-open (h/draw! (g/integer 1 4)) :max-rows (h/draw! (g/integer 1 3)))]
  (hs/run! {:initial-state {:sut (l/empty opts) :model {:open {} :resolved {} :expired {} :unknown 0} :opts opts :t 0 :history []}
   :rules [(hs/rule :register (fn [s] (step s [:register (h/draw! (g/integer 0 3)) (/ (h/draw! (g/integer 0 4)) 4.0)])))
           (hs/rule :resolve (fn [s] (step s [:resolve (h/draw! (g/integer 0 (:t s))) (h/draw! (g/boolean))])))
           (hs/rule :expire (fn [s] (step s [:expire (h/draw! (g/integer 0 3))])))
           (hs/rule :roundtrip #(step % [:roundtrip]))]
   :invariants [(hs/invariant :state-agreement consistent!)]})))
(def properties {"expiry" expiry-property "benchmark" benchmark-property "model" model-property})
(defn run-one [id]
 (let [r (h/run-test! {:test-cases 200 :stateful-step-count 35 :seed 414 :database "" :verbosity :normal}
                    (get properties id))
       summary (select-keys r [:passed? :status :seed :test-cases :valid-test-cases :invalid-test-cases :overrun-test-cases :interesting-test-cases :n-failures :flaky?])]
  (prn id summary) (spit (str id "-summary.edn") (pr-str summary))
  (if (= id "model") (assert (:passed? r))
   (do (assert (and (false? (:passed? r)) (not (:flaky? r))))
       (let [e (-> r :final first :exception ex-data)]
        (prn :minimum e) (spit (str id "-input.edn") (pr-str (:input e)))
        (spit (str id "-details.edn") (pr-str e)))
       (spit (str id "-bundle.edn") (codec/encode (rb/from-result (provenance id) r)))))))
(defn replay [id]
 (let [b (codec/decode (slurp (str id "-bundle.edn")))
       r (h/replay-bundle! (provenance id) b (get properties id))
       calls (atom 0)
       bad (h/replay-bundle! (assoc (provenance id) :generator-revision "mismatch") b #(swap! calls inc))]
  (prn id :replay (select-keys r [:status :reproduced? :flaky?]))
  (assert (and (= :reproduced (:status r)) (:reproduced? r) (not (:flaky? r))))
  (assert (and (= :incompatible (:status bad)) (zero? @calls)))))
(defn -main [& args] (case (first args) "replay" (doseq [id ["expiry" "benchmark"]] (replay id))
                                        "run" (doseq [id ["expiry" "benchmark" "model"]] (run-one id))))
