(ns hegel.event-contract
  "Explicit versioned operation-event semantics; independent of transport.

  Generic hegel.trace/hegel.history callers retain their existing domains.
  Use this profile only when the producer claims its complete event contract."
  (:require [hegel.internal.event-id :as event-id]
            [hegel.trace :as trace]
            [hegel.validation :as validation]))

(def contract-id "hegel.operation-events")
(def contract-revision "1")

(defn- options! [opts]
  (validation/reject-unknown-keys! ::invalid-options "event contract options"
                                   #{:max-events :sequence-start} opts)
  (let [opts (merge {:max-events trace/default-max-events :sequence-start 1} opts)]
    (when-not (and (integer? (:max-events opts)) (pos? (:max-events opts)))
      (validation/usage-error! ::invalid-options
                               "event contract :max-events must be a positive integer" {}))
    (when-not (integer? (:sequence-start opts))
      (validation/usage-error! ::invalid-options
                               "event contract :sequence-start must be an integer" {}))
    opts))

(def ^:private shape
  (trace/rule
   :hegel.operation-events/shape
   (fn [events]
     (every?
      (fn [event]
        (and (map? event)
             (integer? (:seq event))
             (some? (event-id/key (:operation-id event)))
             (contains? #{:invoke :return :throw} (:phase event))
             (or (not= :invoke (:phase event))
                 (and (contains? event :operation)
                      (contains? event :parent-operation-id)
                      (or (nil? (:parent-operation-id event))
                          (some? (event-id/key (:parent-operation-id event))))
                      (contains? event :context-id)))))
      events))))

(def ^:private semantic-rules
  [(trace/closed-lifecycles :hegel.operation-events/lifecycles)
   (trace/causal-parentage :hegel.operation-events/parentage)
   (trace/causal-links :hegel.operation-events/causal-links)
   (trace/context-coherence :hegel.operation-events/context)])

(defn check!
  "Check a complete canonical operation-event vector and return it unchanged.

  Options: :max-events (positive, default 256), :sequence-start (integer,
  default 1). Bounds and semantic failures retain hegel.trace error/evidence
  conventions. Invalid options are non-shrinkable usage errors. Parentage is
  causal, not synchronous nesting. Payloads and extra metadata are preserved;
  callers remain responsible for domain validation, byte limits and redaction."
  ([events] (check! events {}))
  ([events opts]
   (let [{:keys [max-events sequence-start]} (options! opts)]
     (trace/check! events
                   (into [shape
                          (trace/contiguous-sequence
                           :hegel.operation-events/sequence sequence-start)]
                         semantic-rules)
                   {:max-events max-events}))))

(defn check-envelope!
  "Check profile identity and event semantics, returning the same envelope.

  Envelope keys are exactly :contract-id, :contract-revision and :events.
  Unknown/malformed identity is a usage error, not a property counterexample.
  This is not a transport decoder or provenance/authentication check."
  ([envelope] (check-envelope! envelope {}))
  ([envelope opts]
   ;; Do not echo an invalid envelope's potentially unbounded/raw payload.
   (when-not (and (map? envelope)
                  (= #{:contract-id :contract-revision :events}
                     (set (keys envelope)))
                  (= contract-id (:contract-id envelope))
                  (= contract-revision (:contract-revision envelope)))
     (validation/usage-error! ::invalid-envelope
                              "expected hegel.operation-events revision 1 envelope" {}))
   (check! (:events envelope) opts)
   envelope))
