(ns hegel.replay-bundle
  "Pure, bounded schema mechanics for portable replay bundles.

  This namespace deliberately has no codec, I/O, checker, or native-runtime
  dependency. It validates only bounded, portable data shape; it does not
  establish that a reproduction blob is safe for native execution."
  (:require [clojure.string :as str]))

(def limits
  {:max-text-chars 262144
   :max-depth 32
   :max-nodes 8192
   :max-string-chars 8192
   :max-failures 16
   :max-trace-events 256})

(def ^:private min-int64 -9223372036854775808N)
(def ^:private max-int64 9223372036854775807N)
(def ^:private max-uint64 18446744073709551615N)

(def ^:private runtime-hosts #{:jolt :bb :jvm :jank :clr})
(def ^:private backend-values #{:auto :default :urandom})
(def ^:private verbosity-values #{:quiet :normal :verbose :debug})
(def ^:private phase-values #{:explicit :reuse :generate :target :shrink})
(def ^:private health-check-values
  #{:filter-too-much :too-slow :test-cases-too-large :large-initial-test-case})

(def ^:private replay-option-keys
  #{:backend :test-cases :stateful-step-count :verbosity :derandomize?
    :report-multiple-failures? :phases :suppress-health-checks})

(defn- invalid!
  [path reason]
  (throw (ex-info "invalid Hegel replay bundle"
                  {:type ::invalid-bundle
                   :hegel/usage-error? true
                   :path path
                   :reason reason})))

(defn- nonblank-string? [value]
  (and (string? value) (not (str/blank? value))))

(defn- finite-floating? [value]
  (and (or (double? value) (float? value))
       (= value value)
       (not= value ##Inf)
       (not= value ##-Inf)))

(defn- portable-scalar? [value]
  (or (nil? value)
      (true? value)
      (false? value)
      (string? value)
      (keyword? value)
      (and (integer? value) (<= min-int64 value max-uint64))
      (finite-floating? value)))

(defn- consume-node! [path depth nodes]
  (when (> depth (:max-depth limits))
    (invalid! path :max-depth))
  (let [nodes (inc nodes)]
    (when (> nodes (:max-nodes limits))
      (invalid! path :max-nodes))
    nodes))

(defn- consume-text! [path text-chars value]
  (let [length (count value)
        text-chars (+ text-chars length)]
    (when (str/includes? value "\u0000")
      (invalid! path :nul-string))
    (when (> length (:max-string-chars limits))
      (invalid! path :max-string-chars))
    (when (> text-chars (:max-text-chars limits))
      (invalid! path :max-text-chars))
    text-chars))

(defn- walk-portable!
  "Bounded iterative walk.  Map keys are nodes too; seqs/lists/sets are
  rejected rather than realized or coerced into a portable representation."
  [value]
  (loop [frames (list [:value [] value 0])
         nodes 0
         text-chars 0]
    (if-let [[kind path item depth index] (first frames)]
      (let [frames (next frames)]
        (case kind
          :value
          (let [nodes (consume-node! path depth nodes)]
            (cond
              (record? item) (invalid! path :opaque-value)
              (map? item)
              (recur (conj frames [:map path (seq item) (inc depth) 0])
                     nodes text-chars)

              (vector? item)
              (recur (conj frames [:vector path item (inc depth) 0])
                     nodes text-chars)

              (string? item)
              (recur frames nodes (consume-text! path text-chars item))

              (keyword? item)
              ;; Keywords are serialized as text too.  Count their complete
              ;; spelling, including namespace and leading colon, so a large
              ;; keyword cannot bypass encoder allocation limits.
              (do
                (when (> (+ 1 (count (name item))
                            (if-let [ns (namespace item)] (inc (count ns)) 0))
                         (:max-string-chars limits))
                  (invalid! path :max-string-chars))
                (recur frames nodes (consume-text! path text-chars (str item))))

              (portable-scalar? item)
              (recur frames nodes text-chars)

              :else
              (invalid! path :opaque-value)))

          :map
          (if-let [entry (first item)]
            (let [[key value] entry
                  entry-path (conj path :entry index)]
              (recur (list* [:value (conj entry-path :key) key depth]
                            [:value (conj entry-path :value) value depth]
                            [:map path (next item) depth (inc index)]
                            frames)
                     nodes text-chars))
            (recur frames nodes text-chars))

          :vector
          (if (< index (count item))
            (recur (list* [:value (conj path index) (nth item index) depth]
                          [:vector path item depth (inc index)]
                          frames)
                   nodes text-chars)
            (recur frames nodes text-chars))))
      value)))

(defn- closed-map! [path value required allowed]
  (when-not (map? value)
    (invalid! path :map-required))
  (doseq [key required]
    (when-not (contains? value key)
      (invalid! (conj path key) :required-key)))
  (doseq [key (keys value)]
    (when-not (contains? allowed key)
      ;; A schema key may be an attacker-controlled long string or opaque
      ;; portable scalar.  Keep error data bounded and payload-free.
      (invalid! (conj path :unknown-key) :unknown-key)))
  value)

(defn- require-nonblank! [path value]
  (when-not (nonblank-string? value)
    (invalid! path :nonblank-string-required))
  value)

(defn- require-one-of! [path value allowed]
  (when-not (contains? allowed value)
    (invalid! path :invalid-enum))
  value)

(defn- require-boolean! [path value]
  (when-not (or (= true value) (= false value))
    (invalid! path :boolean-required))
  value)

(defn- require-integer-range! [path value minimum maximum]
  (when-not (and (integer? value) (<= minimum value maximum))
    (invalid! path :integer-out-of-range))
  value)

(defn- require-keyword-vector! [path value allowed]
  (when-not (vector? value)
    (invalid! path :vector-required))
  (doseq [[index entry] (map-indexed vector value)]
    (when-not (contains? allowed entry)
      (invalid! (conj path index) :invalid-enum)))
  value)

(defn- canonical-option-vector! [path value]
  ;; Core accepts collections for these enum masks.  Replay bundles preserve
  ;; only concrete, bounded forms: vectors and lists retain order; sets are
  ;; sorted because their semantic mask is unordered.  Lazy seqs are rejected
  ;; rather than realized while creating an export artifact.
  (cond
    (vector? value) value
    (list? value)
    (loop [remaining (seq value)
           copied []]
      (if (nil? remaining)
        copied
        (do
          (when (>= (count copied) (:max-nodes limits))
            (invalid! path :max-nodes))
          (recur (next remaining) (conj copied (first remaining))))))
    (set? value)
    (do
      ;; `count` is bounded-cost for concrete persistent sets; check it before
      ;; allocating the sorted export vector.
      (when (> (count value) (:max-nodes limits))
        (invalid! path :max-nodes))
      (doseq [item value]
        (when-not (keyword? item)
          (invalid! path :invalid-enum)))
      (vec (sort value)))
    :else (invalid! path :portable-enum-collection-required)))

(defn- canonical-seed! [path value]
  (when-not (and (string? value)
                 (re-matches #"(?:0|[1-9][0-9]*)" value)
                 (<= (count value) 20))
    (invalid! path :canonical-unsigned64-seed-required))
  (let [seed (bigint value)]
    (when (> seed max-uint64)
      (invalid! path :canonical-unsigned64-seed-required)))
  value)

(defn- validate-provenance-at! [path provenance]
  (closed-map! path provenance
               #{:hegel-sha :libhegel-version :runtime :property-id
                 :generator-revision :model-revision}
               #{:hegel-sha :libhegel-version :runtime :property-id
                 :generator-revision :model-revision})
  (when-not (and (string? (:hegel-sha provenance))
                 (re-matches #"[0-9a-f]{40}" (:hegel-sha provenance)))
    (invalid! (conj path :hegel-sha) :lowercase-sha40-required))
  (require-nonblank! (conj path :libhegel-version)
                     (:libhegel-version provenance))
  (let [runtime-path (conj path :runtime)
        runtime (:runtime provenance)]
    (closed-map! runtime-path runtime #{:host :version :os :arch}
                 #{:host :version :os :arch})
    (require-one-of! (conj runtime-path :host) (:host runtime) runtime-hosts)
    (doseq [key [:version :os :arch]]
      (require-nonblank! (conj runtime-path key) (get runtime key))))
  (require-nonblank! (conj path :property-id) (:property-id provenance))
  (require-nonblank! (conj path :generator-revision)
                     (:generator-revision provenance))
  (when-not (or (nil? (:model-revision provenance))
                (nonblank-string? (:model-revision provenance)))
    (invalid! (conj path :model-revision) :nil-or-nonblank-string-required))
  provenance)

(defn validate-provenance!
  "Validate portable provenance and return it unchanged."
  [provenance]
  (walk-portable! provenance)
  (validate-provenance-at! [] provenance))

(defn- validate-options-at! [path options]
  (closed-map! path options #{} replay-option-keys)
  (when (contains? options :backend)
    (require-one-of! (conj path :backend) (:backend options) backend-values))
  (when (contains? options :test-cases)
    (require-integer-range! (conj path :test-cases) (:test-cases options)
                            1 max-uint64))
  (when (contains? options :stateful-step-count)
    (require-integer-range! (conj path :stateful-step-count)
                            (:stateful-step-count options) 1 max-int64))
  (when (contains? options :verbosity)
    (require-one-of! (conj path :verbosity) (:verbosity options)
                     verbosity-values))
  (doseq [key [:derandomize? :report-multiple-failures?]
          :when (contains? options key)]
    (require-boolean! (conj path key) (get options key)))
  (when (contains? options :phases)
    (require-keyword-vector! (conj path :phases) (:phases options) phase-values))
  (when (contains? options :suppress-health-checks)
    (require-keyword-vector! (conj path :suppress-health-checks)
                             (:suppress-health-checks options)
                             health-check-values))
  options)

(defn snapshot-options
  "Keep only present, replay-relevant validated run settings.

  `:seed`, database identity, display names, and removed `:mode` are excluded.
  Enum collections are copied to vectors so the exported bundle is canonical."
  [validated-run-opts]
  (when-not (map? validated-run-opts)
    (invalid! [:options] :map-required))
  (let [options (select-keys validated-run-opts replay-option-keys)
        options (cond-> options
                  (contains? options :phases)
                  (update :phases #(canonical-option-vector! [:options :phases] %))
                  (contains? options :suppress-health-checks)
                  (update :suppress-health-checks
                          #(canonical-option-vector!
                            [:options :suppress-health-checks] %)))]
    (walk-portable! options)
    (validate-options-at! [:options] options)))

(defn- validate-failure! [path failure]
  (closed-map! path failure #{:origin :reproduction-blob}
               #{:origin :reproduction-blob})
  (require-nonblank! (conj path :origin) (:origin failure))
  (require-nonblank! (conj path :reproduction-blob)
                     (:reproduction-blob failure))
  failure)

(defn- validate-trace! [path trace]
  (closed-map! path trace #{:contract-id :contract-revision :events}
               #{:contract-id :contract-revision :events})
  (require-nonblank! (conj path :contract-id) (:contract-id trace))
  (require-nonblank! (conj path :contract-revision) (:contract-revision trace))
  (let [events (:events trace)]
    (when-not (vector? events)
      (invalid! (conj path :events) :vector-required))
    (when (> (count events) (:max-trace-events limits))
      (invalid! (conj path :events) :max-trace-events))
    (doseq [[index event] (map-indexed vector events)]
      (when-not (and (map? event) (not (record? event)))
        (invalid! (conj path :events index) :map-required))))
  trace)

(defn validate!
  "Validate one schema-version-1 bundle and return it unchanged."
  [bundle]
  ;; This bounded walk comes first so schema checks never recurse through or
  ;; coerce hostile nested data.
  (walk-portable! bundle)
  (closed-map! [] bundle
               #{:format :schema-version :provenance :seed :options :failures}
               #{:format :schema-version :provenance :seed :options :failures
                 :trace})
  (when-not (= :hegel/replay-bundle (:format bundle))
    (invalid! [:format] :format-required))
  (when-not (= 1 (:schema-version bundle))
    (invalid! [:schema-version] :schema-version-required))
  (validate-provenance-at! [:provenance] (:provenance bundle))
  (canonical-seed! [:seed] (:seed bundle))
  (validate-options-at! [:options] (:options bundle))
  (let [failures (:failures bundle)]
    (when-not (vector? failures)
      (invalid! [:failures] :nonempty-vector-required))
    (when (empty? failures)
      (invalid! [:failures] :nonempty-vector-required))
    (when (> (count failures) (:max-failures limits))
      (invalid! [:failures] :max-failures))
    (doseq [[index failure] (map-indexed vector failures)]
      (validate-failure! [:failures index] failure)))
  (when (contains? bundle :trace)
    (validate-trace! [:trace] (:trace bundle)))
  bundle)

(defn- exported-failure! [path failure]
  (when-not (and (map? failure) (true? (:reproduced? failure)))
    (invalid! path :reproduced-failure-required))
  (let [exported (select-keys failure [:origin :reproduction-blob])]
    (walk-portable! exported)
    (validate-failure! path exported)
    exported))

(defn- export-trace! [opts]
  (when-not (map? opts)
    (invalid! [:export-options] :map-required))
  (doseq [key (keys opts)]
    (when-not (contains? #{:trace :redact-trace} key)
      (invalid! [:export-options :unknown-key] :unknown-key)))
  (when (contains? opts :redact-trace)
    (when-not (ifn? (:redact-trace opts))
      (invalid! [:export-options :redact-trace] :callable-required)))
  (when (contains? opts :trace)
    (let [trace (:trace opts)
          trace (if (contains? opts :redact-trace)
                  ((:redact-trace opts) trace)
                  trace)]
      ;; Redaction is an explicit exporter hook, not a trust boundary.
      ;; Validate its complete portable shape before traversing trace fields.
      (walk-portable! trace)
      (validate-trace! [:trace] trace)
      trace)))

(defn from-result
  "Export the replay-relevant subset of a stable reproduced failure result.

  Optional `:trace` is exported only when supplied by the caller. A supplied
  `:redact-trace` function runs before validation and any failure propagates."
  ([provenance result]
   (from-result provenance result {}))
  ([provenance result opts]
   (validate-provenance! provenance)
   (when-not (map? result)
     (invalid! [:result] :map-required))
   (when-not (and (= :failed (:status result))
                  (= false (:passed? result))
                  (= false (:flaky? result)))
     (invalid! [:result] :stable-failed-result-required))
   (let [trace (export-trace! opts)
         failures (:failures result)]
     (when-not (and (vector? failures) (seq failures))
       (invalid! [:result :failures] :nonempty-vector-required))
     (when (> (count failures) (:max-failures limits))
       (invalid! [:result :failures] :max-failures))
     (let [bundle (cond-> {:format :hegel/replay-bundle
                           :schema-version 1
                           :provenance provenance
                           :seed (:seed result)
                           :options (snapshot-options (:replay-options result))
                           :failures (mapv (fn [index failure]
                                             (exported-failure!
                                              [:result :failures index] failure))
                                           (range) failures)}
                    (contains? opts :trace) (assoc :trace trace))]
       (validate! bundle)))))

(def ^:private provenance-paths
  [[:hegel-sha]
   [:libhegel-version]
   [:runtime :host]
   [:runtime :version]
   [:runtime :os]
   [:runtime :arch]
   [:property-id]
   [:generator-revision]
   [:model-revision]])

(defn- value-at [value path]
  (reduce get value path))

(defn compatibility
  "Compare validated provenance exactly, reporting mismatches in stable order."
  [expected-provenance bundle]
  (validate-provenance! expected-provenance)
  (validate! bundle)
  (let [actual (:provenance bundle)
        mismatches
        (reduce (fn [mismatches path]
                  (let [expected (value-at expected-provenance path)
                        observed (value-at actual path)]
                    (if (= expected observed)
                      mismatches
                      (conj mismatches {:path path
                                        :expected expected
                                        :actual observed}))))
                [] provenance-paths)]
    {:compatible? (empty? mismatches)
     :mismatches mismatches}))
