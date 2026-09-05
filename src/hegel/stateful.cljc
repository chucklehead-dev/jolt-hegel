(ns hegel.stateful
  "Engine-managed state-machine and value-pool testing."
  (:refer-clojure :exclude [run!])
  (:require [clojure.string :as str]
            [hegel.core :as h]
            [hegel.ffi :as hffi]
            [hegel.generator :as g]
            [hegel.host :as host]
            [hegel.validation :as validation]))

(defn- invalid-argument [message data]
  (validation/usage-error! ::invalid-argument message data))

(defn- normalized-name [kind value]
  (let [value (cond
                (string? value) value
                (keyword? value) (subs (str value) 1)
                (symbol? value) (str value)
                :else nil)]
    (when (or (nil? value)
              (str/blank? value)
              (str/includes? value "\u0000"))
      (invalid-argument
       (str (name kind) " name must be a nonblank string, keyword, or symbol")
       {:kind kind :name value}))
    value))

(defn rule?
  "True when value was created by rule."
  [value]
  (= ::rule (::kind value)))

(defn invariant?
  "True when value was created by invariant."
  [value]
  (= ::invariant (::kind value)))

(defn rule
  "Declare a named state-machine rule.

  step receives the current state and must return the next state. The optional
  :precondition predicate is checked before step; a false result skips that
  attempted rule without running invariants."
  ([name step]
   (rule name {} step))
  ([name opts step]
   (when-not (map? opts)
     (invalid-argument "rule options must be a map"
                       {:name name :options opts}))
   (let [unknown (seq (remove #{:precondition} (keys opts)))
         precondition (if (contains? opts :precondition)
                        (:precondition opts)
                        (constantly true))]
     (when unknown
       (invalid-argument "unknown rule options"
                         {:name name :options (vec unknown)}))
     (when-not (fn? precondition)
       (invalid-argument "rule precondition must be a function"
                         {:name name :precondition precondition}))
     (when-not (fn? step)
       (invalid-argument "rule step must be a function"
                         {:name name :step step}))
     {::kind ::rule
      :name name
      ::native-name (normalized-name :rule name)
      ::precondition precondition
      ::step step})))

(defn invariant
  "Declare a named invariant predicate over state.

  Invariants must return truthy for valid state. They run before the first rule
  and after every successfully applied rule."
  [name pred]
  (when-not (fn? pred)
    (invalid-argument "invariant predicate must be a function"
                      {:name name :predicate pred}))
  {::kind ::invariant
   :name name
   ::native-name (normalized-name :invariant name)
   ::predicate pred})

(defn- require-pool! [value]
  (when-not (= ::pool (::kind value))
    (invalid-argument "expected a pool created by hegel.stateful/pool"
                      {:value value}))
  value)

(defn pool?
  "True when value is a stateful value pool."
  [value]
  (= ::pool (::kind value)))

(defn pool
  "Create an empty engine-managed value pool for the current test case."
  ([]
   (pool (h/current-test-case!)))
  ([test-case]
   (when-not test-case
     (invalid-argument "pool requires a Hegel test case" {}))
   (let [handle (hffi/new-pool! (:context test-case) (:handle test-case))]
     (h/register-native-cleanup!
      test-case
      #(hffi/pool-free! (:context test-case) handle))
     {::kind ::pool
      ::test-case test-case
      ::pool-handle handle
      ::values (atom {})})))

(defn- ensure-current-pool! [pool test-case]
  (require-pool! pool)
  (when-not (identical? (::test-case pool) test-case)
    (invalid-argument "a stateful pool cannot be reused across test cases"
                      {:pool-handle (::pool-handle pool)}))
  pool)

(defn add!
  "Add value to pool and return the pool."
  [pool value]
  (let [test-case (h/current-test-case!)]
    (ensure-current-pool! pool test-case)
    (let [variable-id
          (hffi/pool-add! (:context test-case)
                          (:handle test-case)
                          (::pool-handle pool))]
      (when (contains? @(::values pool) variable-id)
        (throw
         (ex-info "libhegel returned a duplicate stateful pool variable id"
                  {:type ::pool-diverged
                   :hegel/origin "hegel.stateful/pool-diverged"
                   :pool-handle (::pool-handle pool)
                   :variable-id variable-id})))
      (swap! (::values pool) assoc variable-id value)
      pool)))

(defn pool-size
  "Return the number of active values in pool."
  [pool]
  (count @(::values (require-pool! pool))))

(defn pool-empty?
  "True when pool has no active values."
  [pool]
  (zero? (pool-size pool)))

(defn- pool-generator [pool consume?]
  (require-pool! pool)
  (g/composite-fn
   (fn [test-case]
     (ensure-current-pool! pool test-case)
     (let [variable-id
           (hffi/pool-generate! (:context test-case)
                                (:handle test-case)
                                (::pool-handle pool)
                                consume?)
           entry (find @(::values pool) variable-id)]
       (when-not entry
         (throw
          (ex-info "stateful pool state diverged from libhegel"
                   {:type ::pool-diverged
                    :hegel/origin "hegel.stateful/pool-diverged"
                    :pool-handle (::pool-handle pool)
                    :variable-id variable-id})))
       (let [value (val entry)]
         (when consume?
           (swap! (::values pool) dissoc variable-id))
         value)))))

(defn values-reusable
  "Return a generator that draws a pool value without removing it."
  [pool]
  (pool-generator pool false))

(defn values-consumed
  "Return a generator that draws and removes a pool value."
  [pool]
  (pool-generator pool true))

(defn- control-flow? [error]
  (or (hffi/stop-test? error)
      (hffi/assumption-rejected? error)
      (:hegel/inconclusive? (ex-data error))
      (= :hegel.core/assumption-rejected (:type (ex-data error)))))

(defn- stateful-error [phase item trace error]
  (let [native-name (::native-name item)
        data (or (ex-data error) {})]
    (ex-info
     (or (ex-message error)
         (str (name phase) " " native-name " failed"))
     (assoc data
            :hegel/origin
            (or (:hegel/origin data)
                (str "hegel.stateful/" (name phase) ":" native-name))
            ::phase phase
            ::name (:name item)
            ::trace trace)
     error)))

(defn- check-invariant! [item state trace]
  (let [valid?
        (host/try-catch-all
         ((::predicate item) state)
         error
         (if (control-flow? error)
           (throw error)
           (throw (stateful-error :invariant item trace error))))]
    (when-not valid?
      (throw
       (ex-info
        (str "stateful invariant " (::native-name item) " failed")
        {:type ::invariant-failed
         :hegel/origin
         (str "hegel.stateful/invariant:" (::native-name item))
         ::phase :invariant
         ::name (:name item)
         ::trace trace}))))
  nil)

(defn- check-invariants! [invariants state trace]
  (doseq [item invariants]
    (check-invariant! item state trace))
  nil)

(defn- apply-rule [item state trace]
  (host/try-catch-all
   (if ((::precondition item) state)
     {:state ((::step item) state)
      :applied? true}
     {:state state
      :applied? false})
   error
   (cond
     (hffi/assumption-rejected? error)
     {:state state
      :applied? false}

     (= :hegel.core/assumption-rejected (:type (ex-data error)))
     {:state state
      :applied? false}

     :else
     (if (or (hffi/stop-test? error)
             (:hegel/inconclusive? (ex-data error)))
       (throw error)
       (throw (stateful-error :rule item trace error))))))

(defn- validate-items! [kind pred items]
  (doseq [item items]
    (when-not (pred item)
      (invalid-argument
       (str "state machine contains a value not created by " (name kind))
       {:kind kind :value item})))
  (let [names (mapv ::native-name items)]
    (when-not (= (count names) (count (distinct names)))
      (invalid-argument (str "state machine has duplicate " (name kind) " names")
                        {:kind kind :names names})))
  items)

(defn- run-group!
  [test-case machine rules invariants initial-state initial-trace initial-step]
  (loop [state initial-state
         trace initial-trace
         step-number initial-step]
    (if-some [index
              (hffi/state-machine-next-rule!
               (:context test-case) (:handle test-case) machine)]
      (do
        (when-not (and (integer? index)
                       (<= 0 index)
                       (< index (count rules)))
          (throw
           (ex-info
            (str "libhegel returned out-of-range "
                 "state-machine rule index " index)
            {:type ::invalid-rule-index
             :hegel/origin "hegel.stateful/engine"
             :index index
             :rule-count (count rules)})))
        (let [item (nth rules index)
              next-trace (conj trace (:name item))]
          (h/note! (str "Step " step-number ": " (::native-name item)))
          (let [{next-state :state applied? :applied?}
                (apply-rule item state next-trace)]
            (if applied?
              (do
                (check-invariants! invariants next-state next-trace)
                (recur next-state next-trace (inc step-number)))
              (do
                (hffi/state-machine-rule-rejected!
                 (:context test-case) (:handle test-case) machine)
                (h/note! "Rule stopped early due to violated assumption.")
                (recur state trace step-number))))))
      {:state state
       :trace trace
       :step-number step-number})))

(defn ^{:jolt.aspects/id :hegel.stateful/run
        :jolt.aspects/role :test/state-machine-run}
  run!
  "Run an engine-managed state machine and return its final state.

  config requires :initial-state and a non-empty :rules collection created by
  rule. :invariants is optional. Rule order and names must remain stable across
  generation, shrinking, and final replay. libhegel performs swarm selection
  automatically for each test case."
  [config]
  (when-not (map? config)
    (invalid-argument "state machine config must be a map" {:config config}))
  (let [unknown (seq (remove #{:initial-state :rules :invariants}
                             (keys config)))]
    (when unknown
      (invalid-argument "unknown state machine options"
                        {:options (vec unknown)})))
  (when-not (contains? config :initial-state)
    (invalid-argument "state machine config requires :initial-state" {}))
  (let [rules (vec (or (:rules config) []))
        invariants (vec (or (:invariants config) []))
        _ (validate-items! :rule rule? rules)
        _ (validate-items! :invariant invariant? invariants)]
    (when (empty? rules)
      (invalid-argument "cannot run a state machine with no rules" {}))
    (let [test-case (h/current-test-case!)
          machine
          (hffi/new-state-machine!
           (:context test-case)
           (:handle test-case)
           (mapv ::native-name rules)
           (mapv ::native-name invariants))]
      (try
        (h/note! "Initial invariant check.")
        (check-invariants! invariants (:initial-state config) [])
        (loop [state (:initial-state config)
               trace []
               step-number 1]
          (if-some [_group
                    (hffi/state-machine-next-group!
                     (:context test-case) (:handle test-case) machine)]
            (let [group-result
                  (run-group! test-case machine rules invariants
                              state trace step-number)]
              (recur (:state group-result)
                     (:trace group-result)
                     (:step-number group-result)))
            state))
        (finally
          (hffi/state-machine-free! (:context test-case) machine))))))
