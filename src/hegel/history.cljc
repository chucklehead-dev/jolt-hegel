(ns hegel.history
  "Portable bounded linearizability checks for complete operation histories.

  A history is a vector of events in observation order. Each event has an
  integer `:seq`, an `:operation-id`, and a `:phase` of `:invoke`, `:return`,
  or `:throw`. An invocation also has `:operation`. Every operation must have
  exactly one invocation followed by exactly one terminal event."
  (:require [hegel.host :as host]
            [hegel.trace :as trace]))

(def ^:private default-max-operations 10)

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
  (when-not (map? opts)
    (throw (ex-info "history options must be a map"
                    {:hegel/origin "hegel.history/linearizable"
                     :type ::invalid-options
                     :options opts})))
  (let [allowed #{:max-operations :name :partition-by :sequence-start}
        unknown (seq (remove allowed (keys opts)))
        opts (merge {:max-operations default-max-operations
                     :name :linearizable
                     :partition-by nil
                     :sequence-start nil}
                    opts)]
    (when unknown
      (throw (ex-info "unsupported history option"
                      {:hegel/origin (origin (:name opts))
                       :type ::invalid-options
                       :unknown-keys (vec unknown)})))
    (when-not (and (integer? (:max-operations opts))
                   (not (neg? (:max-operations opts))))
      (throw (ex-info "history :max-operations must be a non-negative integer"
                      {:hegel/origin (origin (:name opts))
                       :type ::invalid-options
                       :max-operations (:max-operations opts)})))
    (when-not (or (nil? (:partition-by opts))
                  (ifn? (:partition-by opts)))
      (throw (ex-info "history :partition-by must be nil or callable"
                      {:hegel/origin (origin (:name opts))
                       :type ::invalid-options
                       :partition-by (:partition-by opts)})))
    (when-not (or (nil? (:sequence-start opts))
                  (integer? (:sequence-start opts)))
      (throw (ex-info "history :sequence-start must be nil or an integer"
                      {:hegel/origin (origin (:name opts))
                       :type ::invalid-options
                       :sequence-start (:sequence-start opts)})))
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
         (fail! "history model transition threw"
                ::model-error events opts
                {:hegel.history/operation operation}
                error))]
    (cond
      (nil? transition) nil
      (and (map? transition) (contains? transition :state)) transition
      :else
      (fail! "legal history model transition must be a map containing :state"
             ::invalid-transition events opts
             {:hegel.history/operation operation
              :hegel.history/transition transition}))))

(defn- predecessors [ops]
  (into {}
        (map (fn [operation]
               [(:operation-id operation)
                (->> ops
                     (filter #(< (:terminal-seq %)
                                 (:invoke-seq operation)))
                     (map :operation-id)
                     set)]))
        ops))

(defn- search [initial step ops events opts]
  (let [before (predecessors ops)]
    (letfn [(visit [state remaining chosen order]
              (if (empty? remaining)
                {:order order
                 :operations (mapv (fn [operation-id]
                                     (first
                                      (filter #(= operation-id
                                                  (:operation-id %))
                                              ops)))
                                   order)
                 :final-state state}
                (some
                 (fn [operation]
                   (when (every? chosen
                                 (get before (:operation-id operation)))
                     (when-let [transition
                                (model-step step state operation events opts)]
                       (visit (:state transition)
                              (vec
                               (remove #(= (:operation-id operation)
                                           (:operation-id %))
                                       remaining))
                              (conj chosen (:operation-id operation))
                              (conj order (:operation-id operation))))))
                 remaining)))]
      (visit initial ops #{} []))))

(defn- partition-of [partition-by operation events opts]
  (host/try-catch-all
   (partition-by operation)
   error
   (fail! "history partition function threw"
          ::partition-error events opts
          {:hegel.history/operation operation}
          error)))

(defn- ordered-groups [ops partition-by events opts]
  (if-not partition-by
    [[nil ops]]
    (reduce
     (fn [groups operation]
       (let [partition (partition-of partition-by operation events opts)
             index (first
                    (keep-indexed
                     (fn [index [key _]] (when (= key partition) index))
                     groups))]
         (if (nil? index)
           (conj groups [partition [operation]])
           (update-in groups [index 1] conj operation))))
     []
     ops)))

(defn linearization
  "Return a sequential-model witness for a complete bounded history, or nil.

  `step` is a pure function of `[state operation]`. It returns nil when the
  operation/result is illegal in that state, or `{:state next-state}` when it
  is legal. The checker preserves real-time precedence and may reorder
  overlapping operations.

  Options:

  * `:max-operations` bounds the total search (default 10).
  * `:partition-by` independently checks completed operations grouped by the
    callable's result, using `initial` for every partition.
  * `:sequence-start` optionally requires an exact first sequence number.
  * `:name` supplies the stable Hegel failure origin.

  An unpartitioned witness has `:order`, `:operations`, and `:final-state`.
  A partitioned witness has `:partitions`, each containing those keys plus
  `:partition`."
  ([initial step events] (linearization initial step events {}))
  ([initial step events opts]
   (let [opts (options opts)]
     (when-not (ifn? step)
       (throw (ex-info "history model step must be callable"
                       {:hegel/origin (origin (:name opts))
                        :type ::invalid-options
                        :step step})))
     (let [ops (operations events opts)
           groups (ordered-groups ops (:partition-by opts) events opts)
           witnesses
           (loop [remaining groups
                  witnesses []]
             (if-let [[partition partition-ops] (first remaining)]
               (if-let [witness (search initial step partition-ops events opts)]
                 (recur (next remaining)
                        (conj witnesses (assoc witness :partition partition)))
                 nil)
               witnesses))]
       (when witnesses
         (if (:partition-by opts)
           {:operation-count (count ops)
            :partitions witnesses}
           (assoc (dissoc (first witnesses) :partition)
                  :operation-count (count ops))))))))

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
  (when-not (map? opts)
    (throw (ex-info "history rule options must be a map"
                    {:hegel/origin (origin name)
                     :type ::invalid-options
                     :options opts})))
  (let [allowed #{:initial :step :max-operations :partition-by :sequence-start}
        unknown (seq (remove allowed (keys opts)))
        {:keys [initial step]} opts]
    (when unknown
      (throw (ex-info "unsupported history rule option"
                      {:hegel/origin (origin name)
                       :type ::invalid-options
                       :unknown-keys (vec unknown)})))
    (when-not (ifn? step)
      (throw (ex-info "history rule :step must be callable"
                      {:hegel/origin (origin name)
                       :type ::invalid-options
                       :step step})))
    (trace/rule name
                (fn [events]
                  (linearizable?
                   initial step events
                   (assoc (dissoc opts :initial :step) :name name))))))
