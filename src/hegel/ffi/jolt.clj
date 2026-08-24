(ns hegel.ffi.jolt
  "Jolt implementation of the narrow native boundary. Signatures and layouts
  are derived from hegel/abi.edn."
  (:require [hegel.abi :as abi]
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
  [function-id function descriptor]
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

(defn- make-foreign-function [{:keys [symbol blocking?] :as function} descriptor]
  (let [{:keys [args return]} (signature function descriptor)
        form (if blocking?
               (list 'jolt.ffi/foreign-fn symbol args return :blocking)
               (list 'jolt.ffi/foreign-fn symbol args return))]
    (eval form)))

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
              (ffi/load-library library-path)
              (let [bindings
                    (into {}
                          (map (fn [[function-id function]]
                                 [function-id
                                  (make-foreign-function function descriptor)]))
                          (:functions descriptor))]
                (reset! functions* bindings)
                (abi/register-backend-report! coverage)
                bindings))))))

(defn function [function-id]
  (or (get @functions* function-id)
      (throw (ex-info "Jolt libhegel bindings are not loaded"
                      {:function function-id}))))

;; Memory operations deliberately mirror only what the shared libhegel wrapper
;; needs. Allocation remains explicitly paired with free.
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
