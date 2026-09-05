(ns hegel.signature-policy-test
  "Characterization of the canonical ABI -> selected-host carrier policy.

  resources/hegel/abi.edn is the single canonical definition of libhegel's C
  ABI. Nothing here restates its 103 signatures or its 6 struct layouts:
  every traversal, including struct field order, is derived recursively from
  `hegel.abi/functions` and `hegel.abi/types`. What *is* literalized is the
  small per-target scalar carrier policy each adapter applies to that
  canonical data, and the target-specific by-value wrapper rule, because
  expectations built by calling the adapter cannot detect drift in the
  adapter.

  The carrier policy is recorded for both targets side by side, so the
  intentional Jolt/babashka.ffi deltas -- including the ones that propagate
  into derived struct layouts -- are asserted as data on every host even
  though only the selected host's adapter is loaded."
  (:require [clojure.test :refer [deftest is]]
            [hegel.abi :as abi]
            [hegel.host :as host]
            #?(:jolt [hegel.ffi.jolt :as adapter]
               :bb [hegel.ffi.babashka :as adapter]
               :clj [hegel.ffi.babashka :as adapter])))

;; The selected adapter mirrors hegel.ffi.backend's own reader conditional:
;; Jolt selects the Jolt adapter, Babashka and the JVM share the upstream
;; babashka.ffi adapter.
(def ^:private target #?(:jolt :jolt :default :babashka))

(def ^:private adapter-namespace
  #?(:jolt 'hegel.ffi.jolt :default 'hegel.ffi.babashka))

(def ^:private carrier-policy
  "Independently literalized scalar carrier policy per target.

  Jolt names its 32-bit integer carriers :int/:uint; pinned babashka.ffi names
  them :int32/:uint32. Every other carrier agrees. `:c/string` is deliberately
  :pointer on both: the common wrapper owns its own NUL-terminated and
  length-delimited buffers rather than letting the binder allocate them."
  {:jolt {:c/void :void
          :c/bool :uint8
          :c/int8 :int8
          :c/uint8 :uint8
          :c/int16 :int16
          :c/uint16 :uint16
          :c/int32 :int
          :c/uint32 :uint
          :c/int64 :int64
          :c/uint64 :uint64
          :c/size :size_t
          :c/float :float
          :c/double :double
          :c/string :pointer}
   :babashka {:c/void :void
              :c/bool :uint8
              :c/int8 :int8
              :c/uint8 :uint8
              :c/int16 :int16
              :c/uint16 :uint16
              :c/int32 :int32
              :c/uint32 :uint32
              :c/int64 :int64
              :c/uint64 :uint64
              :c/size :size_t
              :c/float :float
              :c/double :double
              :c/string :pointer}})

(def ^:private carriers (get carrier-policy target))

(defn- by-value-form
  "Jolt tags a by-value aggregate argument and the caller passes a storage
  pointer; upstream babashka.ffi takes the bare layout and the caller passes a
  field map. The two call conventions are not interchangeable."
  [variant layout]
  (case variant
    :jolt [:by-value layout]
    :babashka layout))

(defn- translate
  "Translate one canonical ABI type form under `variant`'s independently
  literalized scalar carrier policy. Struct fields are expanded recursively
  from `hegel.abi/types`, so field order and identity always come from the
  canonical descriptor: only the leaf scalar carrier names in
  `carrier-policy` are literalized."
  [types variant form]
  (let [variant-carriers (get carrier-policy variant)]
    (cond
      (vector? form)
      (let [[kind referent] form]
        (case kind
          :pointer :pointer
          :by-value (by-value-form variant (translate types variant referent))
          ::unrepresentable))

      (contains? variant-carriers form) (get variant-carriers form)

      :else
      (case (:kind (get types form))
        :struct [:struct (mapv (fn [{:keys [name type]}]
                                  [name (translate types variant type)])
                                (:fields (get types form)))]
        :function-pointer :pointer
        ::unrepresentable))))

(defn- expected-type [types form] (translate types target form))
(defn- expected-by-value [layout] (by-value-form target layout))

(defn- form-class
  "Classify a canonical argument or return form for census assertions."
  [types form]
  (cond
    (vector? form) (first form)
    (= :c/string form) :string
    (= :function-pointer (:kind (get types form))) :callback
    (= :struct (:kind (get types form))) :aggregate
    (= :opaque (:kind (get types form))) :opaque
    :else :scalar))

(defn- type-ids-of-kind [types kind]
  (sort (keep (fn [[type-id descriptor]]
                (when (= kind (:kind descriptor)) type-id))
              types)))

(defn- rejection [thunk]
  (try
    (thunk)
    nil
    (catch clojure.lang.ExceptionInfo error error)))

(deftest the-selected-host-binds-the-expected-adapter-target
  (is (contains? #{:bb :jvm :jolt} (host/runtime)))
  (is (= (case (host/runtime) :jolt :jolt :babashka) target))
  ;; Cross-check the literalized adapter-namespace against the namespace the
  ;; reader conditional actually required, via the resolved var's own
  ;; metadata, rather than recomputing the same case form a second time.
  (is (= adapter-namespace (ns-name (:ns (meta #'adapter/native-type))))
      "the reader-conditional-selected adapter namespace must match adapter-namespace"))

(deftest the-canonical-abi-is-the-single-definition-of-the-native-surface
  (let [descriptor (abi/validate!)
        types (abi/types)
        functions (abi/functions)]
    (is (= 1 (:schema-version descriptor)))
    (is (= 103 (count functions))
        "the canonical descriptor is the only inventory of libhegel functions")
    (is (= 34 (count types)))
    ;; 12 scalar carriers plus void and string, 13 opaque handles, 6 structs
    ;; and exactly one callback type.
    (is (= {:void 1 :boolean 1 :integer 9 :float 2 :string 1
            :opaque 13 :struct 6 :function-pointer 1}
           (frequencies (map :kind (vals types)))))
    (is (= 12 (count (filter #(contains? #{:boolean :integer :float} (:kind %))
                             (vals types))))
        "twelve scalar carriers, excluding void and string")
    (is (= (count functions) (count (set (map :symbol (vals functions)))))
        "symbols are unique, so no second hand-maintained list can drift")))

(deftest canonical-argument-and-return-forms-have-an-exact-census
  (let [types (abi/types)
        functions (abi/functions)
        argument-forms (mapcat :args (vals functions))
        return-forms (map :return (vals functions))
        callback-users (sort (keep (fn [[function-id function]]
                                     (when (some #(= :function-pointer
                                                     (:kind (get types %)))
                                                 (:args function))
                                       function-id))
                                   functions))]
    (is (= 365 (count argument-forms)))
    (is (= {:pointer 277 :scalar 70 :string 10 :by-value 6 :callback 2}
           (frequencies (map #(form-class types %) argument-forms))))
    (is (= 103 (count return-forms)))
    (is (= {:scalar 101 :pointer 1 :string 1}
           (frequencies (map #(form-class types %) return-forms))))
    ;; Every aggregate crosses the boundary through caller-owned storage on
    ;; the way out. No function returns an aggregate by value, which is why no
    ;; target needs a return-buffer convention.
    (is (empty? (filter #(= :by-value (form-class types %)) return-forms)))
    (is (empty? (filter #(= :aggregate (form-class types %)) return-forms)))
    ;; Six by-value aggregate arguments, all of them date/time bounds.
    (is (= {:generate-date 2 :generate-time 2 :generate-datetime 2}
           (into {}
                 (keep (fn [[function-id function]]
                         (let [count* (count (filter #(= :by-value (form-class types %))
                                                     (:args function)))]
                           (when (pos? count*) [function-id count*]))))
                 functions)))
    (is (= [:run-start :test-case-from-blob] callback-users)
        "one callback type, used by exactly two functions")
    (is (= [:hegel/output-callback] (type-ids-of-kind types :function-pointer)))))

(deftest every-declared-carrier-maps-to-the-literal-target-policy
  (let [descriptor (abi/descriptor)
        types (abi/types)]
    (is (= (set (keys carriers))
           (set (concat (type-ids-of-kind types :void)
                        (type-ids-of-kind types :boolean)
                        (type-ids-of-kind types :integer)
                        (type-ids-of-kind types :float)
                        (type-ids-of-kind types :string))))
        "the literal policy covers every declared carrier, and only those")
    (doseq [[type-id expected] carriers]
      (is (= expected (adapter/native-type type-id descriptor))
          (str type-id " must map to " expected " on " (name target))))))

(deftest every-declared-type-id-translates-under-every-canonical-form
  (let [descriptor (abi/descriptor)
        types (abi/types)
        struct-ids (type-ids-of-kind types :struct)]
    ;; Pointers, opaque handles and callbacks all collapse to one machine word
    ;; on both targets. That collapse is what makes the canonical descriptor,
    ;; not the adapters, the right place for the shared policy.
    (doseq [type-id (keys types)]
      (is (= :pointer (adapter/native-type [:pointer type-id] descriptor))
          (str "[:pointer " type-id "] must collapse to :pointer")))
    (is (= :pointer (adapter/native-type :hegel/output-callback descriptor)))
    (is (= 6 (count struct-ids))
        "a fixed struct census guards the recursive derivation below against silent drift")
    (doseq [type-id struct-ids]
      (let [layout (translate types target type-id)]
        (is (= layout (adapter/native-type type-id descriptor))
            (str type-id " layout"))
        (is (= (expected-by-value layout)
               (adapter/native-type [:by-value type-id] descriptor))
            (str type-id " by-value form"))))))

(deftest bare-opaque-handles-and-unrepresentable-forms-are-rejected
  (let [descriptor (abi/descriptor)
        opaque (type-ids-of-kind (abi/types) :opaque)]
    (is (= 13 (count opaque)))
    (doseq [type-id opaque]
      (is (= type-id
             (:type (ex-data (rejection #(adapter/native-type type-id descriptor)))))
          (str type-id " must not be expressible by value"))
      (is (= type-id
             (:type (ex-data
                     (rejection #(adapter/native-type [:by-value type-id]
                                                       descriptor)))))
          (str "[:by-value " type-id "] must not be expressible")))
    (doseq [form [[:array :c/uint8] :c/int128 :hegel/not-a-declared-type]]
      (is (= form
             (:type (ex-data (rejection #(adapter/native-type form descriptor)))))
          (str (pr-str form) " must be rejected")))))

(deftest every-canonical-function-signature-translates-to-the-target-policy
  (let [descriptor (abi/descriptor)
        types (abi/types)
        translated (atom 0)]
    (doseq [[function-id {:keys [args return]}] (abi/functions)]
      (doseq [[position form] (map-indexed vector args)]
        (swap! translated inc)
        (is (= (expected-type types form)
               (adapter/native-type form descriptor))
            (str function-id " argument " position " " (pr-str form))))
      (swap! translated inc)
      (is (= (expected-type types return)
             (adapter/native-type return descriptor))
          (str function-id " return " (pr-str return))))
    (is (= 468 @translated)
        "365 argument forms and 103 return forms were each translated")))

(deftest the-adapter-signature-composition-matches-the-public-translation
  (let [descriptor (abi/descriptor)
        types (abi/types)
        ;; `signature` is private: this is the narrowest seam that proves the
        ;; per-function composition, and it exposes no new production API.
        ;; A failed resolution is not caught here: this seam must fail this
        ;; test closed on every supported host, not be swallowed as a
        ;; host-specific allowed nil.
        signature (ns-resolve adapter-namespace 'signature)]
    (is (some? signature)
        "the private signature seam must resolve on every supported host")
    (doseq [[function-id function] (abi/functions)]
      (is (= {:args (mapv #(expected-type types %) (:args function))
              :return (expected-type types (:return function))}
             (signature function descriptor))
          (str function-id " composed signature")))))

(deftest the-two-target-policies-differ-only-where-intended
  (let [types (abi/types)
        jolt (:jolt carrier-policy)
        babashka (:babashka carrier-policy)]
    (is (= (set (keys jolt)) (set (keys babashka))))
    (is (= {:c/int32 {:jolt :int :babashka :int32}
            :c/uint32 {:jolt :uint :babashka :uint32}}
           (into {}
                 (keep (fn [[type-id jolt-carrier]]
                         (let [other (get babashka type-id)]
                           (when-not (= jolt-carrier other)
                             [type-id {:jolt jolt-carrier :babashka other}]))))
                 jolt))
        "exactly two carriers differ between the targets")
    ;; The 32-bit delta propagates into any derived struct layout that
    ;; carries an :int32/:uint32 field; the other three structs only carry
    ;; :pointer/:size_t fields and stay identical across targets.
    (let [struct-ids (type-ids-of-kind types :struct)
          jolt-layouts (into {} (map (juxt identity #(translate types :jolt %))) struct-ids)
          babashka-layouts (into {} (map (juxt identity #(translate types :babashka %))) struct-ids)]
      ;; Real selected-adapter by-value coverage lives in
      ;; every-declared-type-id-translates-under-every-canonical-form, which
      ;; compares expected-by-value against the actual adapter/native-type
      ;; result; by-value-form is not re-asserted against itself here.
      (is (= [:hegel/date :hegel/datetime :hegel/time]
             (sort (keep (fn [[type-id layout]]
                           (when-not (= layout (get babashka-layouts type-id))
                             type-id))
                         jolt-layouts)))))))
