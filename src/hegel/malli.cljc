(ns hegel.malli
  "Optional, bounded Malli schema adapter for Hegel generators.

  Malli is intentionally not a runtime dependency of jolt-hegel. Consumers
  that require this namespace must supply `metosin/malli`."
  (:require [hegel.generator :as g]
            [hegel.host :as host]
            [malli.core :as m]))

(def ^:private default-max-size 100)
(def ^:private uint64-max 18446744073709551615N)
(def ^:private generated-value-origin "hegel.malli/generated-value")

(defn- adapter-error [type message form path data]
  (throw (ex-info message
                  (merge {:type type
                          :path path
                          :form form}
                         data))))

(defn- unsupported-schema! [form path ast message]
  (adapter-error ::unsupported-schema message form path
                 {:schema-type (:type ast)}))

(defn- unsupported-property! [form path property value]
  (adapter-error ::unsupported-property
                 (str "unsupported Malli property " property)
                 form (conj path :properties property)
                 {:property property :value value}))

(defn- validate-properties! [form path properties allowed]
  (doseq [[property value] properties]
    (when-not (contains? allowed property)
      (unsupported-property! form path property value)))
  properties)

(defn- uint64-size? [value]
  (and (integer? value)
       (<= 0 value uint64-max)))

(defn- bounded-size-properties [form path properties fallback-max]
  (validate-properties! form path properties #{:min :max})
  (let [minimum (if (contains? properties :min) (:min properties) 0)
        maximum (if (contains? properties :max)
                  (:max properties)
                  (max minimum fallback-max))]
    (when-not (and (uint64-size? minimum)
                   (uint64-size? maximum)
                   (<= 0 minimum maximum))
      (adapter-error ::invalid-property
                     "Malli size bounds must be uint64 integers with min <= max"
                     form path {:min minimum :max maximum}))
    {:min-size minimum :max-size maximum}))

(defn- numeric-bounds [form path properties default-min default-max integer-bounds?]
  (validate-properties! form path properties #{:min :max})
  (let [minimum (if (contains? properties :min) (:min properties) default-min)
        maximum (if (contains? properties :max) (:max properties) default-max)
        valid-bound? (if integer-bounds?
                       #(and (integer? %)
                             (<= Long/MIN_VALUE % Long/MAX_VALUE))
                       #(and (number? %)
                             (= % %)
                             (<= (- Double/MAX_VALUE) % Double/MAX_VALUE)))]
    (when-not (and (valid-bound? minimum)
                   (valid-bound? maximum)
                   (<= minimum maximum))
      (adapter-error ::invalid-property
                     (if integer-bounds?
                       "Malli integer bounds must be integers with min <= max"
                       "Malli double bounds must be finite numbers with min <= max")
                     form path {:min minimum :max maximum}))
    [minimum maximum]))

(declare ast-generator)

(defn- children-generators [form path ast config ref-generators]
  (mapv (fn [index child]
          (ast-generator form (conj path :children index) child config
                         ref-generators))
        (range)
        (:children ast)))

(defn- map-generator [form path ast config ref-generators]
  (let [properties (validate-properties! form path (:properties ast) #{:closed})]
    (when-not (true? (:closed properties))
      (unsupported-schema! form path ast
                           "only closed Malli maps are supported"))
    (let [entries (->> (:keys ast)
                       (sort-by (fn [[_ entry]] (:order entry))))
          entry-generators
          (mapv
           (fn [[key {:keys [properties value]}]]
             (when (= key ::m/default)
               (unsupported-schema! form (conj path :keys key) ast
                                    "default Malli map entries are not supported"))
             (let [entry-path (conj path :keys key)
                   entry-properties
                   (validate-properties! form entry-path properties #{:optional})
                   optional (:optional entry-properties)
                   _ (when-not (or (nil? optional) (boolean? optional))
                       (adapter-error ::invalid-property
                                      "Malli map entry :optional must be boolean"
                                      form (conj entry-path :properties :optional)
                                      {:property :optional :value optional}))
                   value-generator
                   (ast-generator form (conj entry-path :value) value config
                                  ref-generators)
                   present-generator (g/fmap (fn [generated]
                                               [true key generated])
                                             value-generator)]
               (if optional
                 (g/one-of [(g/just [false key nil]) present-generator])
                 present-generator)))
           entries)]
      (g/fmap
       (fn [generated-entries]
         (reduce (fn [result [present? key value]]
                   (if present? (assoc result key value) result))
                 {}
                 generated-entries))
       (g/tuple* entry-generators)))))

(defn- ast-generator
  [form path ast {:keys [default-max-size] :as config} ref-generators]
  ;; Malli lifts :registry out of :properties into the AST node. Recursive
  ;; roots are handled before ordinary AST compilation; reject a registry at
  ;; any other supported node instead of silently discarding it.
  (let [properties (:properties ast)
        type (:type ast)]
    ;; Let a reference report its unsupported schema type. For supported schema
    ;; types, a lifted registry remains an unsupported property.
    (when (and (not= :ref type) (contains? ast :registry))
      (unsupported-property! form path :registry (:registry ast)))
    (case type
      :nil
      (do (validate-properties! form path properties #{})
          (g/just nil))

      :boolean
      (do (validate-properties! form path properties #{})
          (g/boolean))

      :int
      (let [[minimum maximum]
            (numeric-bounds form path properties
                            Long/MIN_VALUE Long/MAX_VALUE true)]
        (g/integer minimum maximum))

      :double
      (let [[minimum maximum]
            (numeric-bounds form path properties
                            (- Double/MAX_VALUE) Double/MAX_VALUE false)]
        (g/double {:min minimum
                   :max maximum
                   :nan? false
                   :infinity? false}))

      :string
      ;; Malli on the JVM measures String bounds in UTF-16 code units, while
      ;; libhegel and Jolt measure Unicode code points. Restrict this optional
      ;; adapter to the BMP so the supported-schema validity contract and seed
      ;; behavior agree on every host.
      (g/string (assoc (bounded-size-properties form path properties
                                                default-max-size)
                       :max-codepoint 65535))

      :=
      (do (validate-properties! form path properties #{})
          (g/just (:value ast)))

      :enum
      (do (validate-properties! form path properties #{})
          (g/sampled-from (:values ast)))

      :maybe
      (do (validate-properties! form path properties #{})
          (g/optional
           (ast-generator form (conj path :child) (:child ast) config
                          ref-generators)))

      :or
      (do (validate-properties! form path properties #{})
          (g/one-of (children-generators form path ast config ref-generators)))

      :tuple
      (do (validate-properties! form path properties #{})
          (g/tuple* (children-generators form path ast config ref-generators)))

      :vector
      (g/vector (bounded-size-properties form path properties default-max-size)
                (ast-generator form (conj path :child) (:child ast) config
                               ref-generators))

      :sequential
      (g/vector (bounded-size-properties form path properties default-max-size)
                (ast-generator form (conj path :child) (:child ast) config
                               ref-generators))

      :set
      (g/set (bounded-size-properties form path properties default-max-size)
             (ast-generator form (conj path :child) (:child ast) config
                            ref-generators))

      :map-of
      (let [bounds (bounded-size-properties form path properties
                                            default-max-size)]
        (g/map bounds
               (ast-generator form (conj path :key) (:key ast) config
                              ref-generators)
               (ast-generator form (conj path :value) (:value ast) config
                              ref-generators)))

      :map
      (map-generator form path ast config ref-generators)

      :ref
      (if (and (not (contains? ast :registry))
               (contains? ref-generators (:value ast)))
        (get ref-generators (:value ast))
        (unsupported-schema! form path ast
                             "only the active self-reference is supported"))

      (unsupported-schema! form path ast
                           (str "unsupported Malli schema type " type)))))

(defn- ast-contains-ref? [ast reference]
  (or (and (= :ref (:type ast))
           (= reference (:value ast)))
      (some #(ast-contains-ref? % reference) (:children ast))
      (when-let [child (:child ast)]
        (ast-contains-ref? child reference))
      (when-let [key-ast (:key ast)]
        (ast-contains-ref? key-ast reference))
      (when-let [value-ast (:value ast)]
        (and (map? value-ast)
             (ast-contains-ref? value-ast reference)))
      (some (fn [[_ entry]]
              (ast-contains-ref? (:value entry) reference))
            (:keys ast))))

(defn- recursive-root [form ast]
  (let [[registry reference]
        (case (:type ast)
          :ref (do
                 (validate-properties! form [] (:properties ast) #{})
                 [(:registry ast) (:value ast)])
          :schema (let [child (:child ast)]
                    (when (= :ref (:type child))
                      (validate-properties! form [] (:properties ast) #{})
                      (validate-properties! form [:child]
                                            (:properties child) #{})
                      [(:registry ast) (:value child)]))
          nil)]
    (when registry
      (when-not (= #{reference} (set (keys registry)))
        (unsupported-schema!
         form [:registry] ast
         "recursive Malli schemas require exactly one registry entry"))
      (let [definition (get registry reference)]
        (when-not (= :or (:type definition))
          (unsupported-schema!
           form [:registry reference] ast
           "recursive Malli schemas require an :or definition at the root"))
        {:reference reference
         :definition definition
         :path [:registry reference]}))))

(defn- recursive-generator
  [form {:keys [reference definition path]}
   {:keys [max-depth max-leaves] :as config}]
  (let [properties (:properties definition)
        _ (validate-properties! form path properties #{})
        indexed-branches (map-indexed vector (:children definition))
        [recursive-branches leaf-branches]
        ((juxt filter remove)
         (fn [[_ branch]] (ast-contains-ref? branch reference))
         indexed-branches)]
    (when (empty? leaf-branches)
      (unsupported-schema!
       form path definition
       "recursive Malli schemas require at least one nonrecursive :or branch"))
    (when (empty? recursive-branches)
      (unsupported-schema!
       form path definition
       "recursive Malli schemas require at least one self-referencing :or branch"))
    (g/recursive
     {:max-depth max-depth :max-leaves max-leaves}
     (g/one-of
      (mapv (fn [[index branch]]
              (ast-generator form (conj path :children index)
                             branch config {}))
            leaf-branches))
     (fn [subtree]
       (g/one-of
        (mapv (fn [[index branch]]
                (ast-generator form (conj path :children index)
                               branch config {reference subtree}))
              recursive-branches))))))

(defn- normalize-config [form config]
  (when-not (map? config)
    (adapter-error ::invalid-config "Malli adapter config must be a map"
                   form [:config] {:config config}))
  (doseq [[key value] config]
    (when-not (contains? #{:default-max-size :max-depth :max-leaves} key)
      (adapter-error ::invalid-config
                     (str "unsupported Malli adapter config " key)
                     form [:config key] {:config config :key key :value value})))
  (let [maximum (if (contains? config :default-max-size)
                  (:default-max-size config)
                  default-max-size)
        max-depth (get config :max-depth 32)
        max-leaves (get config :max-leaves 100)]
    (doseq [[key value]
            [[:default-max-size maximum]
             [:max-depth max-depth]
             [:max-leaves max-leaves]]]
      (when-not (uint64-size? value)
        (adapter-error ::invalid-config
                       (str "Malli adapter " key " must be a uint64 integer")
                       form [:config key]
                       {:config config :key key :value value})))
    {:default-max-size maximum
     :max-depth max-depth
     :max-leaves max-leaves}))

(defn generator
  "Build a bounded Hegel generator for a supported Malli schema.

  Supported schemas are `:nil`, `:boolean`, `:int`, `:double`, `:string`,
  `:=`, `:enum`, `:maybe`, `:or`, `:tuple`, `:vector`, `:sequential`, `:set`,
  `:map-of`, and closed explicit `:map` schemas. `:min` and `:max` constrain
  scalar and collection schemas. Collection and string schemas without `:max`
  use `:default-max-size`, which defaults to 100. A recursive schema may use
  one self-referencing registry entry whose definition is an `:or` with both
  base and recursive branches. `:max-depth` and `:max-leaves` configure the
  native recursive generator.

  Other references, mutual recursion, regex schemas, predicates, functions,
  classes, transforms, custom generator properties, open maps, and default map
  entries are rejected synchronously instead of being filtered after
  generation."
  ([schema]
   (generator schema {}))
  ([schema config]
   (let [compiled-schema
         (host/try-catch-all
          (m/schema schema)
          error
          (adapter-error ::invalid-schema "invalid Malli schema"
                         schema [] {:cause error}))
         form (m/form compiled-schema)
         config (normalize-config form config)
         ast (m/ast compiled-schema)
         validator (m/validator compiled-schema)
         recursive (recursive-root form ast)
         generated (if recursive
                     (recursive-generator form recursive config)
                     (ast-generator form [] ast config {}))]
     (g/fmap
      (fn [value]
        (when-not (validator value)
          (adapter-error ::invalid-generated-value
                         "Malli adapter generated an invalid value"
                         form [] {:hegel/origin generated-value-origin
                                  :value value}))
        value)
      generated))))
