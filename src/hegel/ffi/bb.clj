(ns hegel.ffi.bb
  "Babashka implementation of the narrow native boundary. Signatures and
  layouts are derived from hegel/abi.edn and executed by babashka.ffi."
  (:require [babashka.ffi :as ffi]
            [hegel.abi :as abi]))

(defn- scalar-type [type-id]
  (case type-id
    :c/void :void
    ;; The common libhegel wrapper supplies C boolean flags as numeric 0/1.
    ;; Keep the raw one-byte representation instead of Babashka's truthiness
    ;; convenience, where numeric zero would otherwise coerce to true.
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
        :by-value (native-type target descriptor)
        (throw (ex-info "unsupported Babashka ABI type form" {:type type}))))

    (scalar-type type) (scalar-type type)

    :else
    (case (get-in descriptor [:types type :kind])
      :opaque (throw (ex-info "opaque values must be passed through pointers"
                              {:type type}))
      :struct (struct-type type descriptor)
      :function-pointer :pointer
      (throw (ex-info "unsupported Babashka ABI type"
                      {:type type
                       :descriptor (get-in descriptor [:types type])})))))

(defn- signature [{:keys [args return]} descriptor]
  {:args (mapv #(native-type % descriptor) args)
   :return (native-type return descriptor)})

(defn capability [_function-id function descriptor]
  (try
    (let [{:keys [args return]} (signature function descriptor)
          selected (ffi/signature-backend args return)]
      {:status :supported
       :route (case selected
                :trampoline :bb/trampoline
                :libffi :bb/libffi
                :ffm :bb/ffm)})
    (catch Throwable error
      {:status :unsupported
       :route :bb/unsupported
       :reason (ex-message error)
       :data (ex-data error)})))

(def backend {:id :bb :check-signature capability})

(defonce ^:private library* (atom nil))
(defonce ^:private functions* (atom nil))

(defn load! [library-path]
  (or @functions*
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
                                 (let [{:keys [args return]}
                                       (signature function descriptor)]
                                   [function-id
                                    (ffi/cfn library (:symbol function)
                                             args return)])))
                          (:functions descriptor))]
                (reset! library* library)
                (reset! functions* bindings)
                ;; Use binding metadata as the authoritative runtime route.
                (let [actual
                      (update coverage :functions
                              (fn [functions]
                                (into {}
                                      (map (fn [[function-id entry]]
                                             (let [selected
                                                   (:babashka.ffi/backend
                                                    (meta (get bindings function-id)))]
                                               [function-id
                                                (assoc entry :route
                                                       (case selected
                                                         :trampoline :bb/trampoline
                                                         :libffi :bb/libffi
                                                         :ffm :bb/ffm))])))
                                      functions)))]
                  (abi/register-backend-report! actual))
                bindings))))))

(defn function [function-id]
  (or (get @functions* function-id)
      (throw (ex-info "Babashka libhegel bindings are not loaded"
                      {:function function-id}))))

(defn layout [type-id]
  (ffi/layout (native-type type-id (abi/descriptor))))

(def null ffi/null)
(defn null? [pointer] (ffi/null? pointer))
(defn alloc [size] (ffi/alloc size))
(defn free [pointer] (ffi/free pointer))
(defn sizeof [type] (ffi/sizeof type))
(defn read-value
  ([pointer type] (ffi/read pointer type))
  ([pointer type offset] (ffi/read pointer type offset)))
(defn write-value [pointer type offset value]
  (ffi/write pointer type offset value))
(defn read-array [pointer length] (ffi/read-array pointer length))
(defn write-array [pointer value] (ffi/write-array pointer value))
(defn read-utf8 [pointer length] (ffi/read-bytes pointer length))
(defn write-utf8 [pointer value] (ffi/write-bytes pointer value))
(defn string->native [value] (ffi/string->ptr value))
(defn native->string [pointer] (ffi/ptr->string pointer))
(defn layout-size [layout] (ffi/layout-size layout))
(defn read-field [pointer layout path] (ffi/read-field pointer layout path))
(defn write-field [pointer layout path value]
  (ffi/write-field pointer layout path value))
