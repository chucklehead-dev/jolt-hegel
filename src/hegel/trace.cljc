(ns hegel.trace
  "Rules for checking bounded semantic event traces inside Hegel properties.

  The producer is deliberately abstract: compiler-aspect journals, protocol
  harnesses, and ordinary application event logs can all supply a vector of
  maps.  Rules run outside instrumentation advice so an aspect's fail-open
  safety contract cannot swallow a test failure.")

(def default-max-events 256)

(defn rule
  "Create a named trace predicate. `check` receives the complete event vector
  and must return truthy when the rule holds. Names are stable failure origins,
  so they must not contain generated data."
  [name check]
  (when-not (or (keyword? name) (symbol? name) (string? name))
    (throw (ex-info "trace rule name must be a keyword, symbol, or string"
                    {:type ::invalid-rule :name name})))
  (when-not (ifn? check)
    (throw (ex-info "trace rule check must be callable"
                    {:type ::invalid-rule :name name})))
  {::rule true ::name name ::check check})

(defn rule? [value]
  (and (map? value) (true? (::rule value))))

(defn- origin [name]
  (str "hegel.trace/"
       (cond
         (keyword? name) (subs (str name) 1)
         :else (str name))))

(defn- validate-events [events max-events]
  (when-not (vector? events)
    (throw (ex-info "trace events must be a vector"
                    {:type ::invalid-trace :value-type (str (type events))})))
  (when-not (and (integer? max-events) (pos? max-events))
    (throw (ex-info "trace max-events must be a positive integer"
                    {:type ::invalid-options :max-events max-events})))
  (when (> (count events) max-events)
    (throw (ex-info "trace exceeded its configured test bound"
                    {:hegel/origin "hegel.trace/event-bound"
                     :type ::event-bound
                     :event-count (count events)
                     :max-events max-events})))
  events)

(defn check!
  "Check every rule against one complete bounded trace, returning the events.

  A false predicate throws with a stable `:hegel/origin` and includes the
  bounded trace in ex-data, allowing Hegel to shrink the inputs or stateful
  command sequence that produced it. Predicate exceptions are wrapped with the
  same stable origin and retained as the cause."
  ([events rules] (check! events rules {}))
  ([events rules {:keys [max-events] :or {max-events default-max-events}}]
   (let [events (validate-events events max-events)
         rules (vec rules)]
     (doseq [r rules]
       (when-not (rule? r)
         (throw (ex-info "trace rules must be created with hegel.trace/rule"
                         {:type ::invalid-rule :rule r})))
       (let [name (::name r)
             passed?
             (try
               (boolean ((::check r) events))
               (catch #?(:cljr System.Exception
                         :jank cpp/jank.runtime.object_ref
                         :default Throwable) error
                 (throw (ex-info "trace rule evaluation threw"
                                 {:hegel/origin (origin name)
                                  :type ::rule-error
                                  :hegel.trace/rule name
                                  :hegel.trace/event-count (count events)
                                  :hegel.trace/events events}
                                 error))))]
         (when-not passed?
           (throw (ex-info "trace rule failed"
                           {:hegel/origin (origin name)
                            :type ::rule-failed
                            :hegel.trace/rule name
                            :hegel.trace/event-count (count events)
                            :hegel.trace/events events})))))
     events)))

(defn contiguous-sequence
  "Require integer `:seq` values to be contiguous from `start` (default 1).
  This detects a bounded ring journal that silently dropped the beginning of a
  test trace before lifecycle rules inspect it."
  ([] (contiguous-sequence :contiguous-sequence 1))
  ([name] (contiguous-sequence name 1))
  ([name start]
   (rule name
         (fn [events]
           (= (mapv :seq events)
              (vec (range start (+ start (count events)))))))))

(defn closed-lifecycles
  "Require every `:operation-id` to have exactly `:enter` followed by one
  terminal `:return` or `:throw` event. Intended for snapshots taken after the
  generated action or state-machine checkpoint has completed."
  ([] (closed-lifecycles :closed-lifecycles))
  ([name]
   (rule name
         (fn [events]
           (and (every? #(some? (:operation-id %)) events)
                (every?
                 (fn [[_ operation-events]]
                   (contains? #{[:enter :return] [:enter :throw]}
                              (mapv :phase operation-events)))
                 (group-by :operation-id events)))))))

(defn synchronous-parentage
  "Require each child lifecycle to be wholly nested inside its declared
  parent's lifecycle. Root operations have nil `:parent-operation-id`."
  ([] (synchronous-parentage :synchronous-parentage))
  ([name]
   (rule
    name
    (fn [events]
      (let [by-operation (group-by :operation-id events)
            bounds (into {}
                         (map (fn [[operation-id operation-events]]
                                [operation-id
                                 {:first (apply min (map :seq operation-events))
                                  :last (apply max (map :seq operation-events))}]))
                         by-operation)]
        (every?
         (fn [event]
           (if-let [parent-id (:parent-operation-id event)]
             (let [child (get bounds (:operation-id event))
                   parent (get bounds parent-id)]
               (and parent
                    (< (:first parent) (:first child))
                    (> (:last parent) (:last child))))
             true))
         (filter #(= :enter (:phase %)) events)))))))

(defn every-eventually
  "Require every event matching `trigger?` to have a later event matching
  `outcome?` with the same value from `correlate`."
  ([name trigger? outcome?]
   (every-eventually name trigger? outcome? :operation-id))
  ([name trigger? outcome? correlate]
   (let [correlate (if (ifn? correlate) correlate #(get % correlate))]
     (rule
      name
      (fn [events]
        (every?
         (fn [[index event]]
           (let [key (correlate event)]
             (some #(and (outcome? %) (= key (correlate %)))
                   (subvec events (inc index)))))
         (filter (fn [[_ event]] (trigger? event))
                 (map-indexed vector events))))))))
