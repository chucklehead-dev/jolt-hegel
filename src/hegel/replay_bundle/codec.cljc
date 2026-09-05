(ns hegel.replay-bundle.codec
  "Bounded EDN transport for replay bundles; never executes reproduction blobs."
  (:require [hegel.internal.portable-edn :as portable-edn]
            [hegel.replay-bundle :as bundle]))

(defn- invalid! [reason]
  ;; Reader exceptions can echo source text. Do not retain their message/cause.
  (throw (ex-info "invalid Hegel replay bundle encoding"
                  {:type ::invalid-encoding
                   :hegel/usage-error? true
                   :path []
                   :reason reason})))

(defn decode
  "Read exactly one bounded schema-v1 bundle from restricted EDN text.

  No tagged literals, reader dispatch, lists, sets or character literals are
  accepted. Validation does not establish blob authenticity or native safety."
  [text]
  (portable-edn/decode text bundle/limits invalid! bundle/validate!))

(defn encode
  "Encode a validated bundle as bounded, readable EDN without metadata.

  Reject values whose printed representation cannot round-trip (for example
  dynamically constructed keywords containing reader syntax). No trace or
  reproduction-blob redaction is performed by this transport layer."
  [value]
  (portable-edn/encode value bundle/limits invalid! bundle/validate!))
