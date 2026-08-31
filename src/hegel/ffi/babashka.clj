(ns hegel.ffi.babashka
  "Shared Babashka and JVM implementation of the narrow native boundary.

  Both hosts use the standalone upstream babashka.ffi API.  The surrounding
  hegel.ffi layer owns lexical native scopes; this adapter never reconstructs
  ownership with a pointer-to-arena registry."
  (:require [babashka.ffi :as ffi]
            [hegel.abi :as abi]
            [hegel.host :as host])
  (:import [java.nio.charset StandardCharsets]))

(defn- scalar-type [type-id]
  (case type-id
    :c/void :void
    ;; libhegel's public C wrapper accepts and returns one-byte numeric flags.
    ;; Keep 0 distinct from truthiness at the common wrapper boundary.
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
    ;; Common wrappers pass explicitly owned pointers rather than asking the
    ;; FFI binding to allocate temporary C strings.
    :c/string :pointer
    nil))

(declare native-type)

(defn- struct-type [type-id descriptor]
  [:struct
   (mapv (fn [{field-name :name field-type :type}]
           [field-name (native-type field-type descriptor)])
         (get-in descriptor [:types type-id :fields]))])

(defn native-type [type descriptor]
  (cond
    (vector? type)
    (let [[kind target] type]
      (case kind
        :pointer :pointer
        ;; Upstream babashka.ffi represents a by-value struct directly by its
        ;; layout and its value as a field map; there is no :by-value wrapper.
        :by-value (native-type target descriptor)
        (throw (ex-info "unsupported babashka.ffi ABI type form" {:type type}))))

    (scalar-type type) (scalar-type type)

    :else
    (case (get-in descriptor [:types type :kind])
      :opaque (throw (ex-info "opaque values must be passed through pointers"
                              {:type type}))
      :struct (struct-type type descriptor)
      :function-pointer :pointer
      (throw (ex-info "unsupported babashka.ffi ABI type"
                      {:type type
                       :descriptor (get-in descriptor [:types type])})))))

(defn- signature [{:keys [args return]} descriptor]
  {:args (mapv #(native-type % descriptor) args)
   :return (native-type return descriptor)})

(defn- runtime-route [selected]
  (case (host/runtime)
    :bb (case selected
          :trampoline :bb/trampoline
          :libffi :bb/libffi
          :ffm :bb/ffm
          :bb/unsupported)
    :jvm (case selected
           :ffm :jvm/ffm
           ;; A JVM binding should always use FFM. Preserve the actual value
           ;; in diagnostics if a future upstream backend adds another route.
           [:jvm selected])
    [:unknown selected]))

(defn capability [_function-id function descriptor]
  (try
    (let [{:keys [args return]} (signature function descriptor)]
      ;; Resolve every aggregate now. cfn performs the definitive signature
      ;; validation when the selected library is loaded.
      (doseq [type (conj args return)
              :when (vector? type)]
        (ffi/sizeof type))
      {:status :supported
       :route (if (= :jvm (host/runtime)) :jvm/ffm :bb/runtime-selected)})
    (catch Throwable error
      {:status :unsupported
       :route (if (= :jvm (host/runtime)) :jvm/ffm :bb/unsupported)
       :reason (ex-message error)
       :data (ex-data error)})))

(def backend
  {:id (case (host/runtime) :bb :bb :jvm :jvm (host/runtime))
   :check-signature capability})

(defonce ^:private library* (atom nil))
(defonce ^:private functions* (atom nil))

(defn- ensure-runtime-capable! []
  (when (= :bb (host/runtime))
    (try
      ;; Native-image struct calls require libffi. Force that route before
      ;; touching the configured libhegel path so a Linux static Babashka build
      ;; cannot be misdiagnosed as a missing or corrupt libhegel installation.
      ;; Eleven scalar arguments are outside the compiled trampoline set and
      ;; exercise the same linked-libffi capability without making a C call.
      (let [probe (ffi/cfn "abs" (vec (repeat 11 :int)) :int)]
        (when-not (= :libffi (:babashka.ffi/backend (meta probe)))
          (throw (ex-info "Babashka did not select its libffi backend"
                          {:selected (:babashka.ffi/backend (meta probe))}))))
      (catch Throwable cause
        (throw
         (ex-info
          (str "this Babashka build cannot provide libhegel's required FFI "
               "ABI; use an FFI-capable Babashka 1.13.220+ build. On Linux, "
               "install babashka-<version>-linux-<arch>.tar.gz, not the "
               "-static asset, and verify that `bb describe` reports a "
               "non-nil :libffi/version")
          {:type :hegel.ffi/unsupported-runtime-build
           :runtime :bb
           :babashka-version (System/getProperty "babashka.version")
           :required-capability :libffi}
          cause))))))

(defn- coerce-call-argument [type value]
  ;; Clojure represents UINT64_MAX as a BigInt. The C carrier is still one
  ;; machine word, so preserve its low 64 bits instead of asking clojure.core/
  ;; long to reject the mathematically unsigned value before babashka.ffi sees
  ;; it. This is the same carrier conversion the former JVM backend performed.
  (if (and (contains? #{:c/uint64 :c/size} type) (number? value))
    (.longValue ^Number value)
    value))

(defn- make-binding [library function descriptor]
  (let [{:keys [args return]} (signature function descriptor)
        raw (ffi/cfn library (:symbol function) args return)]
    (if (some #(contains? #{:c/uint64 :c/size} %) (:args function))
      (with-meta
        (fn [& values]
          (let [arg-types (:args function)
                expected (count arg-types)
                actual (count values)]
            ;; mapv over two collections stops at the shorter input. Check
            ;; exact arity first so unsigned coercion cannot silently discard
            ;; surplus arguments before the upstream binding sees them.
            (when-not (= expected actual)
              (throw
               (ex-info
                (str "libhegel function " (:symbol function) " expects "
                     expected " args, got " actual)
                {:type ::wrong-arity
                 :symbol (:symbol function)
                 :expected expected
                 :actual actual})))
            (apply raw (mapv coerce-call-argument arg-types values))))
        (meta raw))
      raw)))

(defn load! [library-path]
  (or @functions*
      (do
        (ensure-runtime-capable!)
        (locking functions*
          (or @functions*
              (let [descriptor (abi/validate!)
                  coverage (abi/check-backend backend descriptor)]
                (when-not (:supported? coverage)
                  (throw (ex-info
                          "babashka.ffi cannot express the canonical libhegel ABI"
                          coverage)))
                (let [library (ffi/load-library library-path)
                      bindings
                      (into {}
                            (map (fn [[function-id function]]
                                   [function-id
                                    (make-binding library function descriptor)]))
                            (:functions descriptor))
                      actual
                      (update coverage :functions
                              (fn [functions]
                                (into {}
                                      (map (fn [[function-id entry]]
                                             (let [selected
                                                   (:babashka.ffi/backend
                                                    (meta (get bindings function-id)))]
                                               [function-id
                                                (assoc entry :route
                                                       (runtime-route selected))])))
                                      functions)))]
                  (reset! library* library)
                  (reset! functions* bindings)
                  (abi/register-backend-report! actual)
                  bindings)))))))

(defn function [function-id]
  (or (get @functions* function-id)
      (throw (ex-info "babashka.ffi libhegel bindings are not loaded"
                      {:function function-id}))))

(def null ffi/null)
(defn null? [pointer] (or (nil? pointer) (ffi/null? pointer)))

;; A dynamic scope keeps the public backend contract small while making
;; allocation lifetime lexical. It is never captured or transferred between
;; threads: every common wrapper allocates, calls C, copies results, and returns
;; before with-native-scope closes the confined arena.
(def ^:dynamic *native-scope* nil)

(defn with-native-scope [call]
  (with-open [arena (ffi/confined-arena)]
    (binding [*native-scope* arena]
      (call))))

(defn- current-scope []
  (or *native-scope*
      (throw (ex-info "native allocation requires with-native-scope"
                      {:type ::scope-required}))))

(defn alloc [size] (ffi/alloc (current-scope) (max 1 size)))

;; Closing the lexical arena releases allocations together. This remains in
;; the contract so the malloc-based Jolt, jank and CLR implementations retain
;; their explicit finally cleanup unchanged.
(defn free [_pointer] nil)

(defn sizeof [type] (ffi/sizeof (or (scalar-type type) type)))

(defn read-value
  ([pointer type] (read-value pointer type 0))
  ([pointer type offset]
   (ffi/read pointer (or (scalar-type type) type) offset)))

(defn write-value [pointer type offset value]
  (ffi/write pointer (or (scalar-type type) type) value offset))

(defn read-array [pointer length]
  (if (zero? length)
    (byte-array 0)
    (ffi/read-array (ffi/reinterpret pointer length) :uint8 length)))

(defn write-array [pointer value]
  (ffi/write-array pointer :uint8 value))

(defn read-utf8 [pointer length]
  (String. ^bytes (read-array pointer length) StandardCharsets/UTF_8))

(defn write-utf8 [pointer value]
  (let [bytes (.getBytes ^String (str value) StandardCharsets/UTF_8)]
    (write-array pointer bytes)
    (alength bytes)))

(defn string->native [value]
  ;; Allocate explicitly instead of ffi/string->ptr: the same allocation also
  ;; serves length-delimited buffers containing U+0000 before the common layer
  ;; supplies their byte length to C.
  (let [bytes (.getBytes ^String (str value) StandardCharsets/UTF_8)
        pointer (alloc (inc (alength bytes)))]
    (write-array pointer bytes)
    (write-value pointer :uint8 (alength bytes) 0)
    pointer))

(defn native->string [pointer] (ffi/ptr->string pointer))
(defn layout [type-id] (native-type type-id (abi/descriptor)))
(defn layout-size [layout] (ffi/sizeof layout))
(defn read-field [pointer layout path]
  (ffi/read pointer (ffi/place layout path)))
(defn write-field [pointer layout path value]
  (ffi/write pointer (ffi/place layout path) value))

(defn by-value
  "Copy a descriptor-derived aggregate from native memory to the field map
  expected by upstream babashka.ffi's by-value call convention."
  [pointer layout]
  (ffi/read pointer layout))
