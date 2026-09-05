(ns hegel.trace
  "Rules for checking bounded semantic event traces inside Hegel properties.

  The producer is deliberately abstract: compiler-aspect journals, protocol
  harnesses, and ordinary application event logs can all supply a vector of
  maps.  Rules run outside instrumentation advice so an aspect's fail-open
  safety contract cannot swallow a test failure."
  (:require [hegel.host :as host]
            [hegel.validation :as validation]))

(def default-max-events 256)

(defn rule
  "Create a named trace predicate. `check` receives the complete event vector
  and must return truthy when the rule holds. Names are stable failure origins,
  so they must not contain generated data."
  [name check]
  (when-not (or (keyword? name) (symbol? name) (string? name))
    (validation/usage-error!
     ::invalid-rule "trace rule name must be a keyword, symbol, or string"
     {:name name}))
  (when-not (ifn? check)
    (validation/usage-error! ::invalid-rule "trace rule check must be callable"
                             {:name name}))
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
    (validation/usage-error! ::invalid-options
                             "trace max-events must be a positive integer"
                             {:max-events max-events}))
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
  ([events rules opts]
   (validation/reject-unknown-keys! ::invalid-options "trace check options"
                                    #{:max-events} opts)
   (let [max-events (if (contains? opts :max-events)
                      (:max-events opts)
                      default-max-events)
         events (validate-events events max-events)
         rules (vec rules)]
     (doseq [r rules]
       (when-not (rule? r)
         (validation/usage-error!
          ::invalid-rule "trace rules must be created with hegel.trace/rule"
          {:rule r}))
       (let [name (::name r)
             passed?
             (host/try-catch-all
              (boolean ((::check r) events))
              error
              (if (or (:hegel/usage-error? (ex-data error))
                      (:hegel/inconclusive? (ex-data error)))
                (throw error)
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

(defn ordered-sequence
  "Require integer event values to follow one ordering contract.

  Options are `:value` (callable, default `:seq`), optional callable `:scope`,
  `:order` as `:nondecreasing`, `:strictly-increasing`, or `:contiguous`, and an
  optional integer `:start`. With `:scope`, ordering is checked independently
  for each scope in its original observation order."
  ([name] (ordered-sequence name {}))
  ([name opts]
   (validation/reject-unknown-keys! ::invalid-rule "trace sequence options"
                                    #{:value :scope :order :start}
                                    opts)
   (let [{:keys [value scope order start]
          :or {value :seq order :strictly-increasing}} opts]
   (when-not (ifn? value)
     (validation/usage-error! ::invalid-rule
                              "trace sequence :value must be callable"
                              {:name name :value value}))
   (when-not (or (nil? scope) (ifn? scope))
     (validation/usage-error! ::invalid-rule
                              "trace sequence :scope must be nil or callable"
                              {:name name :scope scope}))
   (when-not (contains? #{:nondecreasing :strictly-increasing :contiguous}
                        order)
     (validation/usage-error! ::invalid-rule "unsupported trace sequence order"
                              {:name name :order order}))
   (when-not (or (nil? start) (integer? start))
     (validation/usage-error! ::invalid-rule
                              "trace sequence :start must be an integer"
                              {:name name :start start}))
   (let [ordered?
         (case order
           :nondecreasing <=
           :strictly-increasing <
           :contiguous #(= (inc %1) %2))]
     (rule
      name
      (fn [events]
        (let [groups (if scope
                       (vals (group-by scope events))
                       [events])]
          (every?
           (fn [group]
             (let [values (mapv value group)]
               (and (every? integer? values)
                    (or (nil? start) (empty? values) (= start (first values)))
                    (every? true?
                            (map ordered? values (rest values))))))
           groups))))))))

(defn event-model
  "Fold events through a small pure state model and check every transition.

  Options require `:step`, a function of `[state event]`. `:initial` defaults
  to nil, `:invariant` checks `[next-state event]` after every transition, and
  `:final` checks the state after a complete scope. Optional callable `:scope`
  runs one independent model per scope while preserving each scope's observed
  order."
  [name opts]
  (validation/require-map! ::invalid-rule "trace event-model options" opts
                           {:name name :options opts})
  (validation/reject-unknown-keys! ::invalid-rule "trace event-model options"
                                   #{:initial :step :invariant :final :scope}
                                   opts {:name name})
  (let [{:keys [initial step invariant final scope]
         :or {invariant (fn [_ _] true)
              final (fn [_] true)}} opts]
    (when-not (ifn? step)
      (validation/usage-error! ::invalid-rule
                               "trace event-model :step must be callable"
                               {:name name :step step}))
    (when-not (ifn? invariant)
      (validation/usage-error! ::invalid-rule
                               "trace event-model :invariant must be callable"
                               {:name name :invariant invariant}))
    (when-not (ifn? final)
      (validation/usage-error! ::invalid-rule
                               "trace event-model :final must be callable"
                               {:name name :final final}))
    (when-not (or (nil? scope) (ifn? scope))
      (validation/usage-error! ::invalid-rule
                               "trace event-model :scope must be nil or callable"
                               {:name name :scope scope}))
    (rule
     name
     (fn [events]
       (let [groups (if scope
                      (vals (group-by scope events))
                      [events])]
         (every?
          (fn [group]
            (loop [state initial
                   remaining (seq group)]
              (if-let [event (first remaining)]
                (let [next-state (step state event)]
                  (if (invariant next-state event)
                    (recur next-state (next remaining))
                    false))
                (final state))))
          groups))))))

(defn contiguous-sequence
  "Require integer `:seq` values to be contiguous from `start` (default 1).
  This detects a bounded ring journal that silently dropped the beginning of a
  test trace before lifecycle rules inspect it."
  ([] (contiguous-sequence :contiguous-sequence 1))
  ([name] (contiguous-sequence name 1))
  ([name start]
   (ordered-sequence name {:value :seq :order :contiguous :start start})))

(defn closed-lifecycles
  "Require every `:operation-id` to have exactly one invocation followed by
  one terminal `:return` or `:throw` event. The canonical invocation phase is
  `:invoke`; legacy semantic journals using `:enter` remain accepted. Intended
  for snapshots taken after the generated action or state-machine checkpoint
  has completed."
  ([] (closed-lifecycles :closed-lifecycles))
  ([name]
   (rule name
         (fn [events]
           (and (every? #(some? (:operation-id %)) events)
                (every?
                 (fn [[_ operation-events]]
                   (contains? #{[:invoke :return] [:invoke :throw]
                                [:enter :return] [:enter :throw]}
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
         (filter #(contains? #{:invoke :enter} (:phase %)) events)))))))

(defn causal-parentage
  "Require each declared parent to have been invoked before its child.

  Root operations have nil `:parent-operation-id`. Unlike
  `synchronous-parentage`, this rule deliberately does not constrain terminal
  order: an async parent may return or throw before its child terminates. Both
  canonical `:invoke` events and legacy semantic-journal `:enter` events are
  treated as invocations."
  ([] (causal-parentage :causal-parentage))
  ([name]
   (rule
    name
    (fn [events]
      (let [invocations (filterv #(contains? #{:invoke :enter} (:phase %))
                                 events)
            by-operation (group-by :operation-id invocations)]
        (every?
         (fn [event]
           (and
            (contains? event :parent-operation-id)
            (let [parent-id (:parent-operation-id event)]
              (if (nil? parent-id)
                true
                (let [parents (get by-operation parent-id)
                      parent (first parents)]
                  (and (= 1 (count parents))
                       (integer? (:seq parent))
                       (integer? (:seq event))
                       (< (:seq parent) (:seq event))))))))
         invocations))))))

(defn- portable-operation-id-key [operation-id]
  ;; A tagged scalar key is total and byte-stable across the supported hosts.
  ;; Composite EDN and host objects have no portable printed iteration/identity
  ;; contract and therefore cannot participate in canonical fan-in ordering.
  (cond
    (integer? operation-id) (str "0:" operation-id)
    (string? operation-id) (str "1:" (pr-str operation-id))
    (keyword? operation-id) (str "2:" (pr-str operation-id))
    (symbol? operation-id) (str "3:" (pr-str operation-id))
    :else nil))

(defn causal-links
  "Require every invocation to carry canonical causal fan-in links.

  `:causal-links` is a vector of portable scalar operation ids (integer,
  string, keyword, or symbol), sorted by a tagged scalar key and unique. Each id
  must name exactly one invocation whose integer `:seq` is earlier than the
  linking invocation. An empty vector is the canonical no-fan-in value.
  Terminal events need not repeat the links. This complements the single
  structural `:parent-operation-id` checked by `causal-parentage`."
  ([] (causal-links :causal-links))
  ([name]
   (rule
    name
    (fn [events]
      (let [invocations (filterv #(contains? #{:invoke :enter} (:phase %))
                                 events)
            by-operation (group-by :operation-id invocations)]
        (every?
         (fn [event]
           (let [links (:causal-links event)
                 link-keys (when (vector? links)
                             (mapv portable-operation-id-key links))]
             (and (contains? event :causal-links)
                  (some? (portable-operation-id-key (:operation-id event)))
                  (vector? links)
                  (every? some? link-keys)
                  (= (count links) (count (distinct links)))
                  (= link-keys (vec (sort link-keys)))
                  (integer? (:seq event))
                  (every?
                   (fn [operation-id]
                     (let [linked (get by-operation operation-id)
                           invocation (first linked)]
                       (and (= 1 (count linked))
                            (integer? (:seq invocation))
                            (< (:seq invocation) (:seq event)))))
                   links))))
         invocations))))))

(defn context-coherence
  "Require each invocation to declare a context coherent with its parent.

  Canonical jolt-aspect-packs invocation events always contain `:context-id`,
  including nil for an unscoped root. A child must name an existing unique
  parent invocation and carry the same context. Terminal events need not
  repeat carrier metadata and are therefore ignored by this rule."
  ([] (context-coherence :context-coherence))
  ([name]
   (rule
    name
    (fn [events]
      (let [invocations (filterv #(contains? #{:invoke :enter} (:phase %))
                                 events)
            by-operation (group-by :operation-id invocations)]
        (every?
         (fn [event]
           (and (contains? event :parent-operation-id)
                (contains? event :context-id)
                (let [parent-id (:parent-operation-id event)]
                  (if (nil? parent-id)
                    true
                    (let [parents (get by-operation parent-id)
                          parent (first parents)]
                      (and (= 1 (count parents))
                           (contains? parent :context-id)
                           (= (:context-id parent) (:context-id event))))))))
         invocations))))))

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
