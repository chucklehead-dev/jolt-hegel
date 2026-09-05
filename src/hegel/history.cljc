(ns hegel.history
  "Portable bounded linearizability checks for complete operation histories.

  A history is a vector of events in observation order. Each event has an
  integer `:seq`, an `:operation-id`, and a `:phase` of `:invoke`, `:return`,
  or `:throw`. An invocation also has `:operation`. Every operation must have
  exactly one invocation followed by exactly one terminal event."
  (:require [clojure.set :as set]
            [hegel.host :as host]
            [hegel.trace :as trace]
            [hegel.validation :as validation]))

(def ^:private default-max-operations 10)
(def ^:private default-max-search-steps 100000)

(defn- origin [name]
  (str "hegel.history/"
       (if (keyword? name) (subs (str name) 1) (str name))))

(defn- evidence [events max-operations]
  (let [limit (* 2 max-operations)
        event-count (count events)]
    {:hegel.history/event-count event-count
     :hegel.history/events (if (<= event-count limit)
                             events
                             (subvec events 0 limit))
     :hegel.history/evidence-truncated? (> event-count limit)}))

(defn- fail!
  ([message type events opts data]
   (throw (ex-info message
                   (merge {:hegel/origin (origin (:name opts))
                           :type type}
                          (evidence events (:max-operations opts))
                          data))))
  ([message type events opts data cause]
   (throw (ex-info message
                   (merge {:hegel/origin (origin (:name opts))
                           :type type}
                          (evidence events (:max-operations opts))
                          data)
                   cause))))

(defn- options [opts]
  (validation/require-map! ::invalid-options "history options" opts
                           {:hegel/origin "hegel.history/linearizable"
                            :options opts})
  (let [opts (merge {:max-operations default-max-operations
                     :max-search-steps default-max-search-steps
                     :name :linearizable
                     :partition-by nil
                     :sequence-start nil}
                    opts)]
    (validation/reject-unknown-keys!
     ::invalid-options "history options"
     #{:max-operations :max-search-steps :name :partition-by :sequence-start} opts
     {:hegel/origin (origin (:name opts))})
    (when-not (and (integer? (:max-operations opts))
                   (not (neg? (:max-operations opts))))
      (validation/usage-error!
       ::invalid-options "history :max-operations must be a non-negative integer"
       {:hegel/origin (origin (:name opts))
        :max-operations (:max-operations opts)}))
    (when-not (and (integer? (:max-search-steps opts))
                   (not (neg? (:max-search-steps opts))))
      (validation/usage-error!
       ::invalid-options "history :max-search-steps must be a non-negative integer"
       {:hegel/origin (origin (:name opts))
        :max-search-steps (:max-search-steps opts)}))
    (when-not (or (nil? (:partition-by opts))
                  (ifn? (:partition-by opts)))
      (validation/usage-error!
       ::invalid-options "history :partition-by must be nil or callable"
       {:hegel/origin (origin (:name opts))
        :partition-by (:partition-by opts)}))
    (when-not (or (nil? (:sequence-start opts))
                  (integer? (:sequence-start opts)))
      (validation/usage-error!
       ::invalid-options "history :sequence-start must be nil or an integer"
       {:hegel/origin (origin (:name opts))
        :sequence-start (:sequence-start opts)}))
    opts))

(defn- validate-events! [events opts]
  (when-not (vector? events)
    (throw (ex-info "history events must be a vector"
                    {:hegel/origin (origin (:name opts))
                     :type ::malformed-history
                     :value-type (str (type events))})))
  (when (> (count events) (* 2 (:max-operations opts)))
    (fail! "history exceeds the bounded checker limit"
           ::operation-bound events opts
           {:hegel.history/max-operations (:max-operations opts)}))
  (doseq [event events]
    (when-not (map? event)
      (fail! "history event must be a map"
             ::malformed-history events opts {:event event}))
    (when-not (and (contains? event :operation-id)
                   (some? (:operation-id event)))
      (fail! "history event needs a non-nil :operation-id"
             ::malformed-history events opts {:event event}))
    (when-not (contains? #{:invoke :return :throw} (:phase event))
      (fail! "history event phase must be :invoke, :return, or :throw"
             ::malformed-history events opts {:event event}))
    (when-not (integer? (:seq event))
      (fail! "history event needs an integer :seq"
             ::malformed-history events opts {:event event}))
    (when (and (= :invoke (:phase event))
               (not (contains? event :operation)))
      (fail! "history invocation needs :operation"
             ::malformed-history events opts {:event event})))
  (when (seq events)
    (let [start (or (:sequence-start opts) (:seq (first events)))
          expected (range start (+ start (count events)))
          observed (map :seq events)]
      (when-not (= expected observed)
        (fail! "history :seq values must be unique, contiguous, and in vector order"
               ::malformed-history events opts
               {:hegel.history/sequence-start start
                :hegel.history/sequences (vec observed)}))))
  events)

(defn operations
  "Validate and pair a complete event history into normalized operations.

  The normalized operation retains `:invoke` and `:terminal`, and exposes
  `:operation-id`, `:operation`, `:input`, `:outcome`, `:value`,
  `:invoke-seq`, and `:terminal-seq`. Options are the same as `linearization`."
  ([events] (operations events {}))
  ([events opts]
   (let [opts (options opts)
         events (validate-events! events opts)
         by-id (group-by :operation-id events)
         result
         (->> by-id
              (map
               (fn [[operation-id operation-events]]
                 (let [invocations (filter #(= :invoke (:phase %))
                                           operation-events)
                       terminals (filter #(contains? #{:return :throw}
                                                      (:phase %))
                                         operation-events)]
                   (when-not (= 1 (count invocations))
                     (fail! "operation needs exactly one invocation"
                            ::malformed-history events opts
                            {:operation-id operation-id
                             :operation-events (vec operation-events)}))
                   (when-not (= 1 (count terminals))
                     (fail! "operation needs exactly one terminal event"
                            ::malformed-history events opts
                            {:operation-id operation-id
                             :operation-events (vec operation-events)}))
                   (let [invoke (first invocations)
                         terminal (first terminals)]
                     (when-not (< (:seq invoke) (:seq terminal))
                       (fail! "operation terminated before it was invoked"
                              ::malformed-history events opts
                              {:operation-id operation-id
                               :invoke invoke
                               :terminal terminal}))
                     {:operation-id operation-id
                      :operation (:operation invoke)
                      :input (:input invoke)
                      :outcome (:phase terminal)
                      :value (:value terminal)
                      :invoke-seq (:seq invoke)
                      :terminal-seq (:seq terminal)
                      :invoke invoke
                      :terminal terminal}))))
              (sort-by :invoke-seq)
              vec)]
     (when (> (count result) (:max-operations opts))
       (fail! "history exceeds the bounded checker limit"
              ::operation-bound events opts
              {:hegel.history/operation-count (count result)
               :hegel.history/max-operations (:max-operations opts)}))
     result)))

(defn- model-step [step state operation events opts]
  (let [transition
        (host/try-catch-all
         (step state operation)
         error
         (if (:hegel/inconclusive? (ex-data error))
           (throw error)
           (fail! "history model transition threw"
                  ::model-error events opts
                  {:hegel.history/operation operation}
                  error)))]
    (cond
      (nil? transition) nil
      (and (map? transition) (contains? transition :state)) transition
      :else
      (fail! "legal history model transition must be a map containing :state"
             ::invalid-transition events opts
             {:hegel.history/operation operation
              :hegel.history/transition transition}))))

(defn- predecessor-indexes [ops]
  (mapv (fn [operation]
          (into #{}
                (keep-indexed
                 (fn [index predecessor]
                   (when (< (:terminal-seq predecessor)
                            (:invoke-seq operation))
                     index)))
                ops))
        ops))

(defn- search [initial step ops predecessors indices events opts budget]
  (let [allowed (set indices)
        predecessors (mapv #(set/intersection allowed %) predecessors)]
    (letfn [(visit [state remaining chosen order budget]
              (if (empty? remaining)
                {:status :linearizable
                 :witness {:order (mapv #(:operation-id (nth ops %)) order)
                           :operations (mapv #(nth ops %) order)
                           :final-state state}
                 :remaining-budget budget}
                (loop [candidates remaining
                       budget budget]
                  (if-let [index (first candidates)]
                    (if (zero? budget)
                      {:status :inconclusive :remaining-budget budget}
                      (let [budget (dec budget)
                            eligible? (every? #(contains? chosen %)
                                              (nth predecessors index))]
                        (if-not eligible?
                          (recur (next candidates) budget)
                          (if-let [transition
                                   (model-step step state (nth ops index) events opts)]
                            (let [result (visit (:state transition)
                                                (disj remaining index)
                                                (conj chosen index)
                                                (conj order index)
                                                budget)]
                              (if (= :not-linearizable (:status result))
                                (recur (next candidates) (:remaining-budget result))
                                result))
                            (recur (next candidates) budget)))))
                    {:status :not-linearizable :remaining-budget budget}))))]
      (visit initial (into (sorted-set) indices) #{} [] budget))))

(defn- partition-of [partition-by operation events opts]
  (host/try-catch-all
   (partition-by operation)
   error
   (if (:hegel/inconclusive? (ex-data error))
     (throw error)
     (fail! "history partition function threw"
            ::partition-error events opts
            {:hegel.history/operation operation}
            error))))

(defn- ordered-groups [ops partition-by events opts]
  (if-not partition-by
    [[nil (vec (range (count ops)))]]
    (reduce
     (fn [groups [operation-index operation]]
       (let [partition (partition-of partition-by operation events opts)
             index (first
                    (keep-indexed
                     (fn [index [key _]] (when (= key partition) index))
                     groups))]
         (if (nil? index)
           (conj groups [partition [operation-index]])
           (update-in groups [index 1] conj operation-index))))
     []
     (map-indexed vector ops))))

(defn- search-exhausted! [analysis events opts]
  (fail! "history search budget exhausted" ::search-exhausted events opts
         {:hegel/inconclusive? true
          :search (:search analysis)}))

(defn analyze
  "Analyze a bounded history as :linearizable, :not-linearizable, or :inconclusive.

  :max-search-steps defaults to 100000 and counts every candidate considered,
  including one blocked by real-time predecessors. It is global across
  partitions; preprocessing and model callback wall time are not budgeted."
  ([initial step events] (analyze initial step events {}))
  ([initial step events opts]
   (let [opts (options opts)]
     (when-not (ifn? step)
       (validation/usage-error! ::invalid-options
                                "history model step must be callable"
                                {:hegel/origin (origin (:name opts)) :step step}))
     (let [ops (operations events opts)
           predecessors (predecessor-indexes ops)
           groups (ordered-groups ops (:partition-by opts) events opts)
           result (loop [remaining groups witnesses [] budget (:max-search-steps opts)]
                    (if-let [[partition partition-ops] (first remaining)]
                      (let [outcome (search initial step ops predecessors partition-ops events opts budget)]
                        (case (:status outcome)
                          :inconclusive outcome
                          :not-linearizable outcome
                          :linearizable
                          (recur (next remaining)
                                 (conj witnesses (assoc (:witness outcome) :partition partition))
                                 (:remaining-budget outcome))))
                      {:status :linearizable :witnesses witnesses :remaining-budget budget}))
           search-data {:max-search-steps (:max-search-steps opts)
                        :search-steps (- (:max-search-steps opts) (:remaining-budget result))
                        :operation-count (count ops)
                        :partition-count (count groups)}]
       (case (:status result)
         :inconclusive {:status :inconclusive :reason :search-budget :search search-data}
         :not-linearizable {:status :not-linearizable :search search-data}
         :linearizable
         (let [witnesses (:witnesses result)
               witness (if (:partition-by opts)
                         {:operation-count (count ops) :partitions witnesses}
                         (assoc (dissoc (first witnesses) :partition)
                                :operation-count (count ops)))]
           {:status :linearizable :witness witness :search search-data}))))))

(defn linearization
  "Return a sequential-model witness for a complete bounded history, or nil.

  `step` is a pure function of `[state operation]`. It returns nil when the
  operation/result is illegal in that state, or `{:state next-state}` when it
  is legal. The checker preserves real-time precedence and may reorder
  overlapping operations.

  Options:

  * `:max-operations` bounds the total search (default 10).
  * `:max-search-steps` bounds candidate consideration globally (default 100000).
  * `:partition-by` independently checks completed operations grouped by the
    callable's result, using `initial` for every partition.
  * `:sequence-start` optionally requires an exact first sequence number.
  * `:name` supplies the stable Hegel failure origin.

  An unpartitioned witness has `:order`, `:operations`, and `:final-state`.
  A partitioned witness has `:partitions`, each containing those keys plus
  `:partition`. When the search budget is exhausted, this legacy API throws a
  marked inconclusive exception rather than returning nil."
  ([initial step events] (linearization initial step events {}))
  ([initial step events opts]
   (let [opts (options opts)
         analysis (analyze initial step events opts)]
     (case (:status analysis)
       :linearizable (:witness analysis)
       :not-linearizable nil
       :inconclusive (search-exhausted! analysis events opts)))))

(defn linearizable?
  "True when `linearization` finds a sequential-model witness."
  ([initial step events] (linearizable? initial step events {}))
  ([initial step events opts]
   (some? (linearization initial step events opts))))

(defn check!
  "Return a linearization witness or throw with stable bounded Hegel evidence."
  ([initial step events] (check! initial step events {}))
  ([initial step events opts]
   (let [opts (options opts)]
     (or (linearization initial step events opts)
         (fail! "history is not linearizable"
                ::not-linearizable events opts
                {:hegel.history/max-operations (:max-operations opts)})))))

(defn rule
  "Create a `hegel.trace`-compatible rule for bounded linearizability.

  `opts` may contain `:initial`, `:step`, and any `linearization` option except
  `:name`, which is supplied as the first argument."
  [name opts]
  (validation/require-map! ::invalid-options "history rule options" opts
                           {:hegel/origin (origin name) :options opts})
  (validation/reject-unknown-keys!
   ::invalid-options "history rule options"
   #{:initial :step :max-operations :max-search-steps :partition-by :sequence-start} opts
   {:hegel/origin (origin name)})
  (let [{:keys [initial step]} opts
        linear-options
        (options (assoc (dissoc opts :initial :step) :name name))]
    (when-not (ifn? step)
      (validation/usage-error! ::invalid-options
                               "history rule :step must be callable"
                               {:hegel/origin (origin name) :step step}))
    (trace/rule name
                (fn [events]
                  (linearizable? initial step events linear-options)))))
