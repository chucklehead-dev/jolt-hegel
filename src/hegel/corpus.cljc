(ns hegel.corpus
  "Pure, bounded schema and consumption mechanics for materialized corpora.

  This namespace has no file, libhegel, or model dependency. Jolt hashing uses
  platform crypto through a narrow digest adapter. A corpus
  digest is integrity relative to a separately supplied caller pin, not a
  signature or a claim that its values are safe to publish."
  (:require [clojure.string :as str]
            [hegel.corpus.digest :as digest]
            [hegel.internal.portable-data :as portable-data]
            [hegel.internal.portable-edn :as portable-edn]
            [hegel.replay-bundle :as bundle]))

(def ^:private payload-limits
  {:max-text-chars 262144
   :max-depth 32
   :max-nodes 65536
   :max-string-chars 8192})

(def ^:private envelope-limits
  {:max-text-chars 2097152
   :max-depth 32
   :max-nodes 65536
   ;; The payload is a scalar in the outer envelope, but has its own tighter
   ;; data limits after decoding.
   :max-string-chars 262144})

(def ^:private max-uint64 18446744073709551615N)
(def ^:private max-values 256)

(defn- invalid!
  [path reason]
  (throw (ex-info "invalid Hegel materialized corpus"
                  {:type ::invalid-corpus
                   :hegel/usage-error? true
                   :path path
                   :reason reason})))

(defn- edn-invalid! [reason]
  (invalid! [] reason))

(defn- nonblank-string? [value]
  (and (string? value) (not (str/blank? value))))

(defn- closed-map! [path value required allowed]
  (when-not (map? value)
    (invalid! path :map-required))
  (doseq [key required]
    (when-not (contains? value key)
      (invalid! (conj path key) :required-key)))
  (doseq [key (keys value)]
    ;; Do not echo attacker-controlled keys in exception data.
    (when-not (contains? allowed key)
      (invalid! (conj path :unknown-key) :unknown-key)))
  value)

(defn- nonblank! [path value]
  (when-not (nonblank-string? value)
    (invalid! path :nonblank-string-required))
  value)

(defn- uint64-seed! [path value]
  (when-not (and (string? value)
                 (re-matches #"(?:0|[1-9][0-9]*)" value)
                 (<= (count value) 20))
    (invalid! path :canonical-unsigned64-seed-required))
  (when (> (bigint value) max-uint64)
    (invalid! path :canonical-unsigned64-seed-required))
  value)

(defn- count! [path value]
  (when-not (and (integer? value) (<= 1 value max-values))
    (invalid! path :count-out-of-range))
  value)

(defn- policy! [path value]
  (when-not (= :exact-valid-count value)
    (invalid! path :invalid-valid-case-policy))
  value)

(defn- known-bundle-error? [data]
  (and (= :hegel.replay-bundle/invalid-bundle (:type data))
       (true? (:hegel/usage-error? data))))

(defn- validate-bundle-provenance! [path provenance]
  (try
    (bundle/validate-provenance! (dissoc provenance :seam-revision))
    (catch clojure.lang.ExceptionInfo error
      (let [data (ex-data error)]
        (if (known-bundle-error? data)
          (invalid! (into path (or (:path data) []))
                    (or (:reason data) :invalid-provenance))
          (throw error))))))

(defn- validate-provenance-at! [path provenance]
  (closed-map! path provenance
               #{:hegel-sha :libhegel-version :runtime :property-id
                 :generator-revision :model-revision :seam-revision}
               #{:hegel-sha :libhegel-version :runtime :property-id
                 :generator-revision :model-revision :seam-revision})
  ;; Reuse the replay-bundle contract rather than duplicating runtime, SHA, and
  ;; model-revision policy.  The corpus-only seam remains explicit.
  (validate-bundle-provenance! path provenance)
  (nonblank! (conj path :seam-revision) (:seam-revision provenance))
  provenance)

(defn- digest-text! [path text]
  ;; The digest adapter owns host-specific UTF-8/surrogate checking.  It is a
  ;; usage boundary here, so translate only its marked validation errors.
  (try
    (digest/validate-text! text)
    (catch clojure.lang.ExceptionInfo error
      (if (true? (:hegel/usage-error? (ex-data error)))
        (invalid! path :invalid-unicode)
        (throw error)))))

(defn- validate-unicode-data! [value]
  ;; EDN escapes can create an unpaired surrogate after the transport text was
  ;; checked.  Walk the decoded portable shape too, including keyword spelling.
  (loop [pending (list value)]
    (when (seq pending)
      (let [item (first pending)]
      (cond
        (map? item) (recur (concat (mapcat identity item) (next pending)))
        (vector? item) (recur (concat item (next pending)))
        (string? item) (do (digest-text! [] item) (recur (next pending)))
        (keyword? item) (do (digest-text! [] (str item))
                            (recur (next pending)))
        :else (recur (next pending))))))
  value)

(defn validate-provenance!
  "Validate corpus provenance, including its required seam revision."
  [provenance]
  (portable-data/validate! provenance payload-limits invalid!)
  (validate-unicode-data! provenance)
  (validate-provenance-at! [] provenance))

(defn validate-payload!
  "Validate a complete, bounded corpus payload and return it unchanged."
  [payload]
  (portable-data/validate! payload payload-limits invalid!)
  (validate-unicode-data! payload)
  (closed-map! [] payload
               #{:provenance :seed :count :valid-case-policy :values}
               #{:provenance :seed :count :valid-case-policy :values})
  (validate-provenance-at! [:provenance] (:provenance payload))
  (uint64-seed! [:seed] (:seed payload))
  (count! [:count] (:count payload))
  (policy! [:valid-case-policy] (:valid-case-policy payload))
  (when-not (vector? (:values payload))
    (invalid! [:values] :vector-required))
  (when-not (= (:count payload) (count (:values payload)))
    (invalid! [:values] :count-mismatch))
  payload)

(defn- validate-envelope! [envelope]
  (portable-data/validate! envelope envelope-limits invalid!)
  (validate-unicode-data! envelope)
  (closed-map! [] envelope #{:format :schema-version :sha256 :payload}
               #{:format :schema-version :sha256 :payload})
  (when-not (= :hegel/materialized-corpus (:format envelope))
    (invalid! [:format] :invalid-format))
  (when-not (= 1 (:schema-version envelope))
    (invalid! [:schema-version] :unsupported-schema-version))
  (when-not (and (string? (:sha256 envelope))
                 (re-matches #"[0-9a-f]{64}" (:sha256 envelope)))
    (invalid! [:sha256] :lowercase-sha256-required))
  (when-not (string? (:payload envelope))
    (invalid! [:payload] :string-required))
  ;; Validate the exact stored text before it is given to the byte digest.
  (digest-text! [:payload] (:payload envelope))
  envelope)

(defn- validate-expected! [expected]
  ;; Expected is trusted caller configuration, but validating it first keeps an
  ;; invalid pin from causing hashing or parsing of an attacker artifact.
  (portable-data/validate! expected payload-limits invalid!)
  (validate-unicode-data! expected)
  (closed-map! [] expected #{:sha256 :provenance :count :valid-case-policy}
               #{:sha256 :provenance :count :valid-case-policy})
  (when-not (and (string? (:sha256 expected))
                 (re-matches #"[0-9a-f]{64}" (:sha256 expected)))
    (invalid! [:sha256] :lowercase-sha256-required))
  (validate-provenance-at! [:provenance] (:provenance expected))
  (count! [:count] (:count expected))
  (policy! [:valid-case-policy] (:valid-case-policy expected))
  expected)

(defn seal
  "Seal an already materialized payload into a schema-v1 envelope."
  [payload]
  (validate-payload! payload)
  (let [payload-text (portable-edn/encode payload payload-limits
                                           edn-invalid! validate-payload!)
        _ (digest-text! [:payload] payload-text)
        envelope {:format :hegel/materialized-corpus
                  :schema-version 1
                  :sha256 (digest/sha256 payload-text)
                  :payload payload-text}]
    (validate-envelope! envelope)))

(defn encode
  "Encode a validated envelope as restricted, bounded EDN transport text."
  [envelope]
  (portable-edn/encode envelope envelope-limits edn-invalid! validate-envelope!))

(defn decode
  "Decode one bounded outer envelope.  Digest comparison is performed by consume!."
  [text]
  (portable-edn/decode text envelope-limits edn-invalid! validate-envelope!))

(defn consume!
  "Return corpus payload only after independent pin, digest, and fields agree."
  [expected envelope]
  (validate-expected! expected)
  (validate-envelope! envelope)
  (let [payload-text (:payload envelope)
        actual-sha (digest/sha256 payload-text)]
    (when-not (= (:sha256 envelope) actual-sha)
      (invalid! [:sha256] :payload-digest-mismatch))
    (when-not (= (:sha256 expected) actual-sha)
      (invalid! [:sha256] :expected-digest-mismatch))
    (let [payload (portable-edn/decode payload-text payload-limits
                                       edn-invalid! validate-payload!)]
      (when-not (= (:provenance expected) (:provenance payload))
        (invalid! [:provenance] :expected-provenance-mismatch))
      (when-not (= (:count expected) (:count payload))
        (invalid! [:count] :expected-count-mismatch))
      (when-not (= (:valid-case-policy expected)
                   (:valid-case-policy payload))
        (invalid! [:valid-case-policy] :expected-valid-case-policy-mismatch))
      payload)))
