(ns hegel.ffi.jolt
  "Jolt implementation of the narrow native boundary. Signatures and layouts
  are derived from hegel/abi.edn."
  (:require [hegel.abi :as abi]
            [hegel.native :as native]
            [jolt.ffi :as ffi]))

(defn- scalar-type [type-id]
  (case type-id
    :c/void :void
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
        :by-value [:by-value (native-type target descriptor)]
        (throw (ex-info "unsupported Jolt ABI type form"
                        {:type type}))))

    (scalar-type type) (scalar-type type)

    :else
    (case (get-in descriptor [:types type :kind])
      :opaque (throw (ex-info "opaque values must be passed through pointers"
                              {:type type}))
      :struct (struct-type type descriptor)
      :function-pointer :pointer
      (throw (ex-info "unsupported Jolt ABI type"
                      {:type type
                       :descriptor (get-in descriptor [:types type])})))))

(defn layout [type-id]
  (eval (list 'jolt.ffi/layout
              (native-type type-id (abi/descriptor)))))

(defn- signature [function descriptor]
  {:args (mapv #(native-type % descriptor) (:args function))
   :return (native-type (:return function) descriptor)})

(defn capability
  [_function-id function descriptor]
  (try
    (signature function descriptor)
    {:status :supported
     :route :jolt/direct}
    (catch Throwable error
      {:status :unsupported
       :route :jolt/direct
       :reason (ex-message error)
       :data (ex-data error)})))

(def backend
  {:id :jolt
   :check-signature capability})

(defonce ^:private functions* (atom nil))

(defn- binding-routes
  "Return the routes declared for one canonical function. `:ordinary` retains
  the function's historical binding (including the existing always-blocking
  entries); `:collect-safe` is an opt-in Jolt alternate, never a fallback."
  [{:keys [collect-safe?]}]
  (cond-> [:ordinary]
    collect-safe? (conj :collect-safe)))

(defn- windows-symbol-lookup [path]
  ;; Jolt's library-scoped find-symbol is POSIX-only. GetModuleHandleW
  ;; retrieves the already loaded module without acquiring another reference.
  (let [to-wide (eval '(jolt.ffi/foreign-fn "MultiByteToWideChar"
                         [:uint :uint :string :int :pointer :int] :int))
        module (eval '(jolt.ffi/foreign-fn "GetModuleHandleW" [:pointer] :pointer))
        address (eval '(jolt.ffi/foreign-fn "GetProcAddress" [:pointer :string] :pointer))
        length (to-wide 65001 0 path -1 ffi/null 0)]
    (when (zero? length)
      (throw (ex-info "cannot convert override path to UTF-16" {:path path})))
    (let [buffer (ffi/alloc (* 2 length))]
      (try
        (when-not (= length (to-wide 65001 0 path -1 buffer length))
          (throw (ex-info "cannot convert override path to UTF-16" {:path path})))
        (let [handle (module buffer)]
          (when (ffi/null? handle)
            (throw (ex-info "cannot locate loaded override module" {:path path})))
          (fn [symbol] (address handle symbol)))
        (finally (ffi/free buffer))))))

(defn- load-symbol-lookup [path]
  (let [override (native/nonblank-env "HEGEL_LIBHEGEL_LIBRARY")
        preloaded (ffi/find-symbol "hegel_version")]
    (when (or override (nil? preloaded))
      (let [library (ffi/load-library path)]
        (when (and override preloaded)
          (when-not (ns-resolve 'jolt.ffi 'pointer-fn)
            (throw (ex-info "overriding a preloaded libhegel requires Jolt with pointer-fn"
                            {:path path})))
          (if (= :windows (:os (native/platform)))
            (windows-symbol-lookup path)
            (fn [symbol] (ffi/find-symbol library symbol))))))))

(defn- make-foreign-function
  [{:keys [symbol blocking?] :as function} descriptor route lookup]
  (let [{:keys [args return]} (signature function descriptor)
        collect-safe? (or blocking? (= :collect-safe route))
        form (if lookup
               (list 'jolt.ffi/pointer-fn args return)
               (list 'jolt.ffi/foreign-fn symbol args return))
        call (eval (if collect-safe? (concat form [:blocking]) form))]
    (if lookup
      (let [address (lookup symbol)]
        (when (or (nil? address) (ffi/null? address))
          (throw (ex-info "explicit libhegel override lacks required symbol"
                          {:symbol symbol})))
        (partial call address))
      call)))

(defn load!
  "Load LIBRARY-PATH and construct every function binding once."
  [library-path]
  (or @functions*
      (locking functions*
        (or @functions*
            (let [descriptor (abi/validate!)
                  coverage (abi/check-backend backend descriptor)]
              (when-not (:supported? coverage)
                (throw (ex-info "Jolt cannot express the canonical libhegel ABI"
                                coverage)))
              (when-let [probe (ns-resolve 'jolt.ffi 'stack-args-correct?)]
                (when (false? (probe))
                  (throw (ex-info "This Jolt/Chez build misplaces native stack arguments; use an official Jolt release with the FFI ABI fixes"
                                  {:type :hegel.ffi/unsupported-runtime-build
                                   :capability :stack-arguments}))))
              (let [lookup (load-symbol-lookup library-path)
                    bindings
                    (into {}
                          (map (fn [[function-id function]]
                                 [function-id
                                  (into {}
                                        (map (fn [route]
                                               [route
                                                (make-foreign-function
                                                 function descriptor route lookup)]))
                                        (binding-routes function))]))
                          (:functions descriptor))]
                (reset! functions* bindings)
                (abi/register-backend-report! coverage)
                bindings))))))

(defn function
  ([function-id] (function function-id :ordinary))
  ([function-id route]
   (let [bindings (or (get @functions* function-id)
                      (throw (ex-info "Jolt libhegel bindings are not loaded"
                                      {:function function-id :route route})))]
     (or (get bindings route)
         (throw (ex-info "Jolt libhegel function has no requested call route"
                         {:function function-id
                          :route route
                          :available-routes (vec (keys bindings))}))))))

;; Memory operations deliberately mirror only what the shared libhegel wrapper
;; needs. Allocation remains explicitly paired with free.
(def null ffi/null)
(defn null? [pointer] (ffi/null? pointer))
(defn with-native-scope [call] (call))
(defn alloc [size] (ffi/alloc size))
(defn free [pointer] (ffi/free pointer))
(defn sizeof [type] (ffi/sizeof type))
(defn read-value
  ([pointer type] (ffi/read pointer type))
  ([pointer type offset] (ffi/read pointer type offset)))
(defn write-value [pointer type offset value]
  (ffi/write pointer type value offset))
(defn read-array [pointer length] (ffi/read-array pointer length))
(defn write-array [pointer value] (ffi/write-array pointer value))
(defn read-utf8 [pointer length] (ffi/read-bytes pointer length))
(defn alloc-utf8 [text]
  ;; Each character occupies at most four UTF-8 bytes. write-bytes returns
  ;; the actual byte count and preserves embedded NULs on all supported Jolts.
  (let [data (ffi/alloc (max 1 (* 4 (count text))))]
    (try
      [data (ffi/write-bytes data text)]
      (catch Throwable error
        (ffi/free data)
        (throw error)))))
(defn native->string [pointer] (ffi/ptr->string pointer))
(defn layout-size [layout] (ffi/layout-size layout))
(defn read-field [pointer layout path] (ffi/read-field pointer layout path))
(defn write-field [pointer layout path value]
  (ffi/write-field pointer layout path value))
(defn by-value [pointer _layout] pointer)
