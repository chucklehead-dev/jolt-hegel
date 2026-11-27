(ns reference.contracts)

(def opts {:name "reference/contracts" :database "" :seed 7
           :test-cases 64 :verbosity :quiet})

(defn- check-replay! [{:keys [run replay]} id property valid-input?]
  (let [result (run property)
        inputs (fn [failures] (mapv #(-> % :exception ex-data :input) failures))
        witnesses (inputs (:failures result))
        calls (atom 0)]
    (assert (= :failed (:status result)) id)
    (assert (and (= 1 (count witnesses)) (valid-input? (first witnesses))) (pr-str witnesses))
    (let [failures (replay id result (fn [tc] (swap! calls inc) (property tc)))]
      (assert (= witnesses (inputs failures)) id)
      (assert (= 1 @calls) id))))

(defn- fifo-witness? [ops]
  ;; Replay just the public operations against a LIFO stack and a FIFO queue.
  ;; The first disagreement must be the final pop in the recorded witness.
  (loop [stack [] queue [] [op & more] ops]
    (case (first op)
      :push (recur (conj stack (second op)) (conj queue (second op)) more)
      :pop (and (seq stack)
                (if (= (peek stack) (first queue))
                  (recur (pop stack) (subvec queue 1) more)
                  (empty? more)))
      false)))

(defn- check-stack! [{:keys [machine run draw integer assume] :as adapter}]
  (let [values (integer 0 3)
        rejected (atom 0)
        checked-pops (atom 0)
        property (fn [fifo?]
                   (fn [_]
                     (machine
                      {:model [] :stack () :ops []}
                      (fn [s]
                        (let [x (draw values)]
                          (-> s (update :model conj x) (update :stack conj x)
                              (update :ops conj [:push x]))))
                      (fn [{:keys [model stack] :as s}]
                        (when (empty? model)
                          (swap! rejected inc)
                          (assume false))
                        (-> s (assoc :model (pop model)
                                     :stack (if fifo? (butlast stack) (rest stack))
                                     :expected (peek model)
                                     :actual (if fifo? (last stack) (first stack)))
                            (update :ops conj [:pop])))
                      (fn [{:keys [ops expected actual]}]
                        (when (= [:pop] (peek ops))
                          (swap! checked-pops inc)
                          (when-not (= expected actual)
                            (throw (ex-info "pop returned the wrong item"
                                            {:hegel/origin "reference/stack" :input ops}))))
                        true))))]
    (let [control (run (property false))]
      (assert (= :passed (:status control)) (pr-str control))
      (assert (pos? @rejected) "empty pop never rejected")
      (assert (pos? @checked-pops) "no successful pop checked"))
    (check-replay! adapter "reference/stack" (property true) fifo-witness?)))

(defn check! [{:keys [integer set-gen vector-gen map-gen fmap draw assume run invalid-count] :as adapter}]
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
  (let [key-gen (vector-gen {:min-size 1 :max-size 1} (integer 0 2))
        values (fmap (fn [n] (when (pos? n) false)) (integer 0 1))
        maps (map-gen {:min-size 2 :max-size 2} key-gen values)
        seen (atom #{})
        result (run (fn [_]
                      (let [m (draw maps)]
                        (assert (and (map? m) (= 2 (count m))
                                     (every? #{[0] [1] [2]} (keys m))
                                     (every? #(or (nil? %) (false? %)) (vals m))))
                        (swap! seen into (vals m)))))]
    (assert (= :passed (:status result)) (pr-str result))
    (assert (= #{nil false} @seen)))
  (let [generator (integer 0 12)]
    (check-replay! adapter "reference/threshold"
                   (fn [_]
                     (let [n (draw generator)]
                       (when (>= n 5)
                         (throw (ex-info "threshold" {:hegel/origin "reference/threshold" :input n})))))
                   #(= 5 %)))
  (let [vectors (vector-gen {:min-size 1 :max-size 8} (integer -4 4))]
    (check-replay! adapter "reference/index"
                   (fn [_]
                     (let [xs (draw vectors)
                           i (draw (integer 0 (dec (count xs))))]
                       (when (neg? (nth xs i))
                         (throw (ex-info "negative element" {:hegel/origin "reference/index"
                                                             :input [xs i]})))))
                   (fn [[xs i]]
                     (and (vector? xs) (integer? i) (<= 0 i (dec (count xs)))
                          (neg? (nth xs i))))))
  (check-stack! adapter)
  (println "PASS stack, integer, collection, assumption, shrinking and dependent replay contracts"))
