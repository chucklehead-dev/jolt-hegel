(ns hegel.stateful.concurrent
  "Declarations, validation, and capability preflight for concurrent state machines.

  The Jolt capability check may load and version-check libhegel, but it never
  allocates a run or state machine.  The executor will consume the validated
  declaration data in a later slice."
  (:require [clojure.string :as str]
            [hegel.host :as host]
            [hegel.validation :as validation]))

(def ^:private capability-contract-revision 1)

(def ^:private max-int64 9223372036854775807)
(def ^:private max-uint64 18446744073709551615N)

(def ^:private verbosity-values
  #{:quiet :normal :verbose :debug})

(def ^:private health-check-values
  #{:filter-too-much :too-slow :test-cases-too-large
    :large-initial-test-case})

(def ^:private allowed-option-keys
  #{:workers :test-cases :stateful-step-count :verbosity :seed
    :derandomize? :suppress-health-checks :max-diagnostic-events})

(def ^:private default-options
  {:max-diagnostic-events 1024})

(def ^:private worker-context-keys
  "Keys supplied to a concurrent worker callback.

  The callback receives the caller-owned `:shared` value unchanged.  Hegel
  supplies scheduling identity and a cooperative cancellation predicate; it
  does not provide a mutable state transition value or a native handle."
  #{:shared :worker-index :round :group :cancelled?})

(def ^:private worker-result-values
  "The only values a concurrent worker callback may return."
  #{:applied :rejected})

(defn- invalid-argument [message data]
  (validation/usage-error! ::invalid-argument message data))

(defn- invalid-option [message data]
  (validation/usage-error! ::invalid-option message data))

(defn- normalized-name [kind value]
  (let [normalized (cond
                     (string? value) value
                     (keyword? value) (subs (str value) 1)
                     (symbol? value) (str value)
                     :else nil)]
    (when (or (nil? normalized)
              (str/blank? normalized)
              (str/includes? normalized "\u0000"))
      (invalid-argument
       (str (name kind) " name must be a nonblank string, keyword, or symbol")
       {:kind kind :name value}))
    normalized))

(defn rule?
  "True when VALUE is a declaration made by `rule`."
  [value]
  (= ::rule (::kind value)))

(defn invariant?
  "True when VALUE is a declaration made by `invariant`."
  [value]
  (= ::invariant (::kind value)))

(defn rule
  "Declare a named concurrent worker rule.

  A worker callback receives the keys in `worker-context-keys` and must return
  either `:applied` or `:rejected`.  Rules do not have sequential
  `:precondition` options: checking and acting on shared state are separate
  operations in a concurrent callback and must be synchronized by the caller."
  [name group step]
  (let [native-name (normalized-name :rule name)
        native-group (normalized-name :group group)]
    (when-not (fn? step)
      (invalid-argument "concurrent rule step must be a function"
                        {:name name :group group :step step}))
    {::kind ::rule
     :name name
     :group group
     ::native-name native-name
     ::native-group native-group
     ::step step
     ::callback-contract {:context-keys worker-context-keys
                          :result-values worker-result-values}}))

(defn invariant
  "Declare a named coordinator join-point invariant over shared state.

  The predicate receives a map containing `:shared`, `:round`, and `:group`.
  Initial and final checks use a nil group; sampled join-point checks use the
  current group."
  [name predicate]
  (let [native-name (normalized-name :invariant name)]
    (when-not (fn? predicate)
      (invalid-argument "concurrent invariant predicate must be a function"
                        {:name name :predicate predicate}))
    {::kind ::invariant
     :name name
     ::native-name native-name
     ::predicate predicate}))

(defn- validate-items! [kind predicate items]
  (let [items (vec items)]
    (doseq [item items]
      (when-not (predicate item)
        (invalid-argument
         (str "concurrent state machine contains a value not created by "
              (name kind))
         {:kind kind :value item})))
    (let [names (mapv ::native-name items)]
      (when-not (= (count names) (count (distinct names)))
        (invalid-argument
         (str "concurrent state machine has duplicate " (name kind) " names")
         {:kind kind :names names})))
    items))

(defn- require-sequence! [label value]
  ;; Accept only eagerly finite, ordered collection types.  Sets and maps make
  ;; native rule-array order process-dependent; lazy sequences can be infinite.
  (when-not (or (vector? value) (list? value))
    (invalid-argument (str label " must be a vector or list") {:value value}))
  value)

(defn- group-plan
  "Return the stable native group mapping for RULES.

  Group names are normalized, sorted lexicographically, and assigned
  nonnegative int64 ids starting at zero.  `:rule-groups` is parallel to the
  validated rule declarations and is ready for the native constructor."
  [rules]
  (let [rules (validate-items! :rule rule? (require-sequence! "rules" rules))
        names (->> rules (map ::native-group) distinct sort vec)
        ids (zipmap names (range))]
    {:names names
     :ids ids
     :rule-groups (mapv #(get ids (::native-group %)) rules)}))

#_{:clj-kondo/ignore [:unused-private-var]}
(defn- validate-options!
  "Validate and default the concurrent run options without native setup.

  `:workers` is deliberately required and fixed for the whole run.  The
  returned map is a copy with `:max-diagnostic-events` defaulted to 1024."
  [opts]
  (validation/require-map! ::invalid-option "concurrent run options" opts)
  (let [unknown (vec (remove allowed-option-keys (keys opts)))]
    (when (seq unknown)
      (invalid-option
       "concurrent run options contain forbidden or unknown keys"
       {:unknown-keys unknown
        :forbidden-keys (vec (filter #{:backend :database :database-key :name
                                       :phases :report-multiple-failures?
                                       :show-statistics? :observations? :coverage
                                       :counterexample :targeting}
                                      unknown))})))
  (when-not (contains? opts :workers)
    (invalid-option "concurrent run options require :workers"
                   {:missing-options [:workers]}))
  (validation/require-integer-range! ::invalid-option :workers
                                     (:workers opts) 2 max-int64)
  (doseq [[option minimum maximum]
          [[:test-cases 1 max-uint64]
           [:stateful-step-count 1 max-int64]
           [:seed 0 max-uint64]
           [:max-diagnostic-events 1 max-int64]]]
    (when (contains? opts option)
      (validation/require-integer-range! ::invalid-option option
                                         (get opts option)
                                         minimum maximum)))
  (when (contains? opts :verbosity)
    (when-not (contains? verbosity-values (:verbosity opts))
      (invalid-option "unknown concurrent verbosity"
                     {:option :verbosity
                      :value (:verbosity opts)
                      :allowed (vec (sort verbosity-values))})))
  (when (contains? opts :derandomize?)
    (validation/require-boolean! ::invalid-option :derandomize?
                                 (:derandomize? opts)))
  (when (contains? opts :suppress-health-checks)
    (let [checks (:suppress-health-checks opts)]
      (when-not (and (coll? checks) (not (string? checks)))
        (invalid-option "suppress-health-checks must be a collection"
                       {:option :suppress-health-checks :value checks}))
      (doseq [check checks]
        (when-not (contains? health-check-values check)
          (invalid-option "unknown health check"
                         {:option :suppress-health-checks
                          :value check
                          :allowed (vec (sort health-check-values))})))))
  (merge default-options opts))

#_{:clj-kondo/ignore [:unused-private-var]}
(defn- validate-config!
  "Validate a machine map and return its normalized execution plan.

  This function is pure with respect to libhegel: it only validates values and
  computes the deterministic group mapping used by a later executor."
  [config]
  (validation/require-map! ::invalid-argument
                           "concurrent state machine config" config)
  (validation/reject-unknown-keys!
   ::invalid-argument
   "concurrent state machine config"
   #{:shared :close! :rules :invariants}
   config)
  (when-not (contains? config :shared)
    (invalid-argument "concurrent state machine config requires :shared" {}))
  (when-not (contains? config :rules)
    (invalid-argument "concurrent state machine config requires :rules" {}))
  (let [rules-value (:rules config)
        invariants-value (if (contains? config :invariants)
                           (:invariants config)
                           [])]
    (require-sequence! "rules" rules-value)
    (require-sequence! "invariants" invariants-value)
    (let [rules (validate-items! :rule rule? rules-value)
          invariants (validate-items! :invariant invariant? invariants-value)]
      (when (empty? rules)
        (invalid-argument
         "cannot run a concurrent state machine with no rules" {}))
      (when (contains? config :close!)
        (validation/require-callable! ::invalid-argument :close!
                                      (:close! config)))
      (assoc config
             :rules rules
             :invariants invariants
             ::group-plan (group-plan rules)))))

(defn- public-runtime
  "Return the stable runtime labels used by the concurrent capability API."
  []
  (host/runtime))

(defn- unsupported-capability [runtime reason checks & [error]]
  (cond-> {:status :unsupported
           :runtime runtime
           :contract-revision capability-contract-revision
           :reason reason
           :checks checks}
    error (assoc :error
                 (let [data (ex-data error)]
                   (cond-> {:message (ex-message error)
                            :type (:type data)}
                     (seq (select-keys data [:expected :actual :library]))
                     (assoc :details
                            (select-keys data
                                         [:expected :actual :library])))))))

(defn- load-native-capability
  "Resolve the Jolt-only native preflight after portable host qualification."
  []
  #?(:jolt
     (do
       (require 'hegel.ffi)
       {:routes-supported?
        (ns-resolve 'hegel.ffi
                    'concurrent-state-machine-routes-supported?)
        :ensure-compatible!
        (ns-resolve 'hegel.ffi 'ensure-compatible-version!)})
     :default
     (throw
      (ex-info "concurrent native capability is Jolt-only"
               {:type ::executor-unqualified}))))

(defn- capability-version-check [ensure-compatible!]
  (host/try-catch-all
   (do
     (ensure-compatible!)
     {:ok? true})
   error
   {:ok? false :error error}))

(defn capability
  "Report whether the Jolt-only concurrent ABI is available.

  Host detection deliberately precedes the dynamic `hegel.ffi` require:
  unsupported hosts must remain loadable without looking up libhegel.  On
  Jolt, the FFI namespace is loaded only for this preflight; no state-machine
  or test-case allocation is performed here."
  []
  (let [runtime (public-runtime)]
    (if (not= :jolt runtime)
      (unsupported-capability
       runtime
       :executor-unqualified
       {:executor false
        :collect-safe-routes false
        :libhegel-compatible false})
      (host/try-catch-all
       (let [{:keys [routes-supported? ensure-compatible!]}
             (load-native-capability)]
         (cond
           (not routes-supported?)
           (unsupported-capability
            runtime
            :collect-safe-routes-unavailable
            {:executor true
             :collect-safe-routes false
             :libhegel-compatible false})

           (not (true? (routes-supported?)))
           (unsupported-capability
            runtime
            :collect-safe-routes-unavailable
            {:executor true
             :collect-safe-routes false
             :libhegel-compatible false})

           (not ensure-compatible!)
           (unsupported-capability
            runtime
            :libhegel-incompatible
            {:executor true
             :collect-safe-routes true
             :libhegel-compatible false})

           :else
           (let [{:keys [ok? error]}
                 (capability-version-check ensure-compatible!)]
             (if ok?
               {:status :supported
                :runtime runtime
                :contract-revision capability-contract-revision
                :checks {:executor true
                         :collect-safe-routes true
                         :libhegel-compatible true}}
               (unsupported-capability
                runtime
                :libhegel-incompatible
                {:executor true
                 :collect-safe-routes true
                 :libhegel-compatible false}
                error)))))
       error
       (unsupported-capability
        runtime
        :library-unavailable
        {:executor true
         :collect-safe-routes false
         :libhegel-compatible false}
        error)))))

#_{:clj-kondo/ignore [:unused-private-var]}
(defn- finalize-case!
  "Finalize a mocked concurrent case through one coordinator-owned seam.

  `ops` contains zero-argument cleanup functions except `:mark-complete!`,
  which receives the final outcome, and `:property-origin`, which resolves an
  invariant operation/error pair to its stable named origin.  A worker join
  must return true or `{:joined? true}`; otherwise this function throws before
  any free, mark, or release operation.  Once joined, every later phase is
  attempted in order.
  Join-invariant and close failures on a valid/interesting case become property
  failures.  Faults during a forced-invalid/overrun close or native cleanup
  remain run errors, reported only after best-effort finalization with any
  earlier property outcome preserved in the exception data."
  [ops outcome]
  (let [failure (atom (:failure outcome))
        run-errors (atom [])
        record-run-error! (fn [phase operation error]
                            (swap! run-errors conj
                                   {:phase phase
                                    :operation operation
                                    :exception error}))
        property-origin-resolution
        (fn [operation error]
          (if-let [explicit (:hegel/origin (ex-data error))]
            {:origin explicit}
            (case operation
              :close {:origin "hegel.stateful.concurrent/close"}
              :join-invariants
              (if-let [resolver (:property-origin ops)]
                (host/try-catch-all
                 (let [origin (resolver operation error)]
                   (if (and (string? origin) (not (str/blank? origin)))
                     {:origin origin}
                     {:reason :missing-origin}))
                 _resolver-error
                 {:reason :resolver-error})
                {:reason :missing-resolver})
              {:reason :unknown-property-operation})))
        record-property! (fn [phase operation origin error]
                           (let [entry {:phase phase
                                        :operation operation
                                        :origin origin
                                        :exception error}]
                             (if @failure
                               (swap! failure update :secondary
                                      (fnil conj []) entry)
                               (reset! failure
                                       (assoc entry :secondary [])))))
        property-call! (fn [phase operation f]
                         (when f
                           (host/try-catch-all
                            (f)
                            error
                            (if (#{:valid :interesting} (:status outcome))
                              (let [{:keys [origin reason]}
                                    (property-origin-resolution operation error)]
                                (if origin
                                  (record-property! phase operation origin error)
                                  (swap! run-errors conj
                                         {:phase :property-origin
                                          :operation operation
                                          :reason reason
                                          :exception error})))
                              (record-run-error! phase operation error)))))
        native-call! (fn [operation f]
                       (when f
                         (host/try-catch-all
                          (f)
                          error
                          (record-run-error! :native-cleanup operation error))))
        final-outcome (fn []
                        (cond-> outcome
                          @failure (assoc :failure @failure)
                          (and @failure (nil? (:origin outcome)))
                          (assoc :origin (:origin @failure))
                          (and @failure (= :valid (:status outcome)))
                          (assoc :status :interesting)))
        joined (host/try-catch-all
                ((:join-workers! ops))
                error
                {:joined? false :error error})
        joined? (if (map? joined) (:joined? joined) joined)]
    (when-not (true? joined?)
      (throw (ex-info
              "concurrent workers were not safely joined"
              {:type ::workers-not-joined
               :join-result joined})))
    (property-call! :join-invariant :join-invariants
                    (:join-invariants! ops))
    (property-call! :teardown :close (:close! ops))
    (native-call! :free-clones (:free-clones! ops))
    (native-call! :free-machine (:free-machine! ops))
    (native-call! :mark-complete
                  (when-let [mark-complete! (:mark-complete! ops)]
                    #(mark-complete! (final-outcome))))
    (native-call! :release-root (:release-root! ops))
    (let [completed (final-outcome)]
      (if (seq @run-errors)
        (throw
         (ex-info "concurrent case finalization failed"
                  {:type ::native-cleanup-failed
                   :outcome completed
                   :errors @run-errors}))
        completed))))
