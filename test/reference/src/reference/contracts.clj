(ns reference.contracts)

(def opts {:name "reference/contracts" :database "" :seed 7
           :test-cases 64 :verbosity :quiet})

(defn check! [{:keys [integer set-gen draw assume run invalid-count replay]}]
  (let [wide (integer 2147483648 2147483655)
        small (integer 0 2)
        pairs (set-gen {:min-size 2 :max-size 2} small)
        accepted (atom 0)
        result (run (fn [_]
                      (let [n (draw wide)
                            pair (draw pairs)]
                        (assume (even? n))
                        (assert (even? n))
                        (assert (and (integer? n) (<= 2147483648 n 2147483655)))
                        (assert (and (set? pair) (= 2 (count pair))
                                     (every? #{0 1 2} pair)))
                        (swap! accepted inc))))]
    (assert (= :passed (:status result)) (pr-str result))
    (assert (pos? @accepted))
    (assert (pos? (invalid-count result))))
  (let [generator (integer 0 12)
        property (fn [_]
                   (let [n (draw generator)]
                     (when (>= n 5)
                       (throw (ex-info "threshold" {:hegel/origin "reference/threshold" :input n})))))
        result (run property)
        witness #(-> % :exception ex-data :input)
        calls (atom 0)]
    (assert (= :failed (:status result)))
    (assert (= [5] (mapv witness (:failures result))))
    (assert (= [5] (mapv witness (replay result (fn [tc] (swap! calls inc) (property tc))))))
    (assert (= 1 @calls)))
  (println "PASS wide integers, bounded sets, assumptions, minimal witness and replay"))
