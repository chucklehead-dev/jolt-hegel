(require '[study :as s] '[target-cases :as tc] '[augur.ledger :as l])
(def opts (assoc tc/opts :max-open 1 :max-rows 1 :max-keys 10))
(def initial {:sut (l/empty opts) :model {:open {} :resolved {} :expired {} :unknown 0} :opts opts :t 0 :history []})
(def commands [[:register 0 0.0] [:register 1 1.0] [:resolve 0 false] [:resolve 1 true]
               [:register 1 0.5] [:resolve 4 false] [:register 2 0.25] [:expire 2] [:roundtrip]])
(def final-state (reduce (fn [state op] (let [next (s/step state op)] (s/consistent! next) next)) initial commands))
(assert (= (:sut final-state) (clojure.edn/read-string (pr-str (:sut final-state)))))
(prn :boundary-witnesses commands :passed)
(let [state (s/step initial [:register 0 0.5]) broken (assoc-in state [:sut :open] {})
      e (try (s/consistent! broken) nil (catch Exception e e))]
 (assert (= "state-model" (:hegel/origin (ex-data e))))
 (assert (= :open (:details (ex-data e))))
 (prn :semantic-broken-control-detected))
