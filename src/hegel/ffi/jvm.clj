(ns hegel.ffi.jvm
  "Direct JDK 22+ Foreign Function & Memory backend for libhegel."
  (:require [hegel.abi :as abi])
  (:import [java.lang.foreign Arena FunctionDescriptor Linker Linker$Option
            MemoryLayout MemorySegment SymbolLookup ValueLayout]
           [java.lang.invoke MethodHandle]
           [java.nio.charset StandardCharsets]
           [java.nio.file Paths]
           [java.util ArrayList]))

(set! *warn-on-reflection* true)

(def null MemorySegment/NULL)

(defn null? [pointer]
  (or (nil? pointer)
      (zero? (.address ^MemorySegment pointer))))

(def ^:private scalar-layouts
  {:c/bool ValueLayout/JAVA_BOOLEAN
   :c/int8 ValueLayout/JAVA_BYTE
   :c/uint8 ValueLayout/JAVA_BYTE
   :c/int16 ValueLayout/JAVA_SHORT
   :c/uint16 ValueLayout/JAVA_SHORT
   :c/int32 ValueLayout/JAVA_INT
   :c/uint32 ValueLayout/JAVA_INT
   :c/int64 ValueLayout/JAVA_LONG
   :c/uint64 ValueLayout/JAVA_LONG
   :c/size ValueLayout/JAVA_LONG
   :c/float ValueLayout/JAVA_FLOAT
   :c/double ValueLayout/JAVA_DOUBLE
   :c/string ValueLayout/ADDRESS})

(def ^:private memory-type-aliases
  {:bool :c/bool
   :int8 :c/int8
   :uint8 :c/uint8
   :int16 :c/int16
   :uint16 :c/uint16
   :int :c/int32
   :int32 :c/int32
   :uint :c/uint32
   :uint32 :c/uint32
   :int64 :c/int64
   :uint64 :c/uint64
   :size_t :c/size
   :float :c/float
   :double :c/double
   :string :c/string
   :pointer :pointer})

(defn- normalize-memory-type [type]
  (get memory-type-aliases type type))

(defn- align-up [offset alignment]
  (let [remainder (mod offset alignment)]
    (if (zero? remainder)
      offset
      (+ offset (- alignment remainder)))))

(declare type-info)

(defn- named-layout [^MemoryLayout layout field-name]
  (.withName layout (name field-name)))

(defn- struct-info [type-id descriptor]
  (let [fields (get-in descriptor [:types type-id :fields])
        built
        (reduce
         (fn [{:keys [offset alignment elements field-info]} field]
           (let [field-name (:name field)
                 child (type-info (:type field) descriptor)
                 child-alignment (:alignment child)
                 field-offset (align-up offset child-alignment)
                 padding (- field-offset offset)
                 child-layout (named-layout (:layout child) field-name)
                 child-fields
                 (if (:fields child)
                   (into {}
                         (map (fn [[path info]]
                                [(into [field-name] path)
                                 (update info :offset + field-offset)]))
                         (:fields child))
                   {[field-name] {:offset field-offset
                                  :type (:type field)}})]
             {:offset (+ field-offset (:size child))
              :alignment (max alignment child-alignment)
              :elements (cond-> elements
                          (pos? padding)
                          (conj (MemoryLayout/paddingLayout padding))
                          true
                          (conj child-layout))
              :field-info (merge field-info child-fields)}))
         {:offset 0 :alignment 1 :elements [] :field-info {}}
         fields)
        final-size (align-up (:offset built) (:alignment built))
        tail-padding (- final-size (:offset built))
        elements (cond-> (:elements built)
                   (pos? tail-padding)
                   (conj (MemoryLayout/paddingLayout tail-padding)))
        layout (MemoryLayout/structLayout
                (into-array MemoryLayout elements))]
    {:type type-id
     :layout (.withName layout (name type-id))
     :size final-size
     :alignment (:alignment built)
     :fields (:field-info built)}))

(defn type-info [type descriptor]
  (cond
    (vector? type)
    (let [[kind target] type]
      (case kind
        :pointer {:type type
                  :layout ValueLayout/ADDRESS
                  :size (.byteSize ValueLayout/ADDRESS)
                  :alignment (.byteAlignment ValueLayout/ADDRESS)}
        :by-value (type-info target descriptor)
        (throw (ex-info "unsupported JVM ABI type form" {:type type}))))

    (= :c/void type)
    {:type type :layout nil :size 0 :alignment 1}

    (contains? scalar-layouts type)
    (let [layout (get scalar-layouts type)]
      {:type type
       :layout layout
       :size (.byteSize ^MemoryLayout layout)
       :alignment (.byteAlignment ^MemoryLayout layout)})

    :else
    (case (get-in descriptor [:types type :kind])
      :opaque (throw (ex-info "opaque values must be passed through pointers"
                              {:type type}))
      :struct (struct-info type descriptor)
      :function-pointer {:type type
                         :layout ValueLayout/ADDRESS
                         :size (.byteSize ValueLayout/ADDRESS)
                         :alignment (.byteAlignment ValueLayout/ADDRESS)}
      (throw (ex-info "unsupported JVM ABI type"
                      {:type type
                       :descriptor (get-in descriptor [:types type])})))))

(defonce ^:private layouts* (atom {}))

(defn layout [type-id]
  (or (get @layouts* type-id)
      (let [info (type-info type-id (abi/descriptor))]
        (swap! layouts* assoc type-id info)
        info)))

(defn layout-size [layout-info] (:size layout-info))

(defn- ffi-layout [type descriptor]
  (:layout (type-info type descriptor)))

(defn- function-descriptor [{:keys [args return]} descriptor]
  (let [argument-layouts
        (into-array MemoryLayout (map #(ffi-layout % descriptor) args))]
    (if (= :c/void return)
      (FunctionDescriptor/ofVoid argument-layouts)
      (FunctionDescriptor/of (ffi-layout return descriptor) argument-layouts))))

(defn capability [_function-id function descriptor]
  (try
    (function-descriptor function descriptor)
    {:status :supported :route :jvm/ffm}
    (catch Throwable error
      {:status :unsupported
       :route :jvm/ffm
       :reason (ex-message error)
       :data (ex-data error)})))

(def backend {:id :jvm :check-signature capability})

(defonce ^:private linker* (delay (Linker/nativeLinker)))
(defonce ^:private library* (atom nil))
(defonce ^:private functions* (atom nil))
(defonce ^:private allocations* (atom {}))

(defn- number-long ^long [value]
  (.longValue ^Number value))

(defn- set-address! [^MemorySegment segment ^long offset
                     ^MemorySegment value]
  (.set segment ValueLayout/ADDRESS offset value))

(defn- coerce-argument [type value]
  (cond
    (vector? type)
    (case (first type)
      (:pointer :by-value) (or value null))

    (= :c/bool type)
    (if (boolean? value) value (not (zero? (number-long value))))

    (contains? #{:c/int8 :c/uint8} type) (unchecked-byte (number-long value))
    (contains? #{:c/int16 :c/uint16} type) (unchecked-short (number-long value))
    (contains? #{:c/int32 :c/uint32} type) (unchecked-int (number-long value))
    (contains? #{:c/int64 :c/uint64 :c/size} type) (number-long value)
    (= :c/float type) (float value)
    (= :c/double type) (double value)
    (= :c/string type) (or value null)
    (= :hegel/output-callback type) (or value null)
    :else value))

(defn- make-function
  [^SymbolLookup lookup function-id {:keys [symbol args] :as function} descriptor]
  (let [address (.orElse (.find lookup symbol) nil)]
    (when-not address
      (throw (ex-info (str "libhegel symbol not found: " symbol)
                      {:function function-id :symbol symbol})))
    (let [^MethodHandle handle
          (.downcallHandle ^Linker @linker*
                           ^MemorySegment address
                           (function-descriptor function descriptor)
                           (make-array Linker$Option 0))]
      (fn [& values]
        (let [arguments (ArrayList.)]
          (doseq [[type value] (map vector args values)]
            (.add arguments (coerce-argument type value)))
          (.invokeWithArguments handle ^java.util.List arguments))))))

(defn load! [library-path]
  (or @functions*
      (locking functions*
        (or @functions*
            (let [descriptor (abi/validate!)
                  coverage (abi/check-backend backend descriptor)]
              (when-not (:supported? coverage)
                (throw (ex-info "JVM FFM cannot express the canonical libhegel ABI"
                                coverage)))
              (let [arena (Arena/ofShared)
                    path (Paths/get library-path (make-array String 0))
                    lookup (SymbolLookup/libraryLookup path arena)
                    bindings
                    (into {}
                          (map (fn [[function-id function]]
                                 [function-id
                                  (make-function lookup function-id function descriptor)]))
                          (:functions descriptor))]
                (reset! library* {:arena arena :lookup lookup :path library-path})
                (reset! functions* bindings)
                (abi/register-backend-report! coverage)
                bindings))))))

(defn function [function-id]
  (or (get @functions* function-id)
      (throw (ex-info "JVM libhegel bindings are not loaded"
                      {:function function-id}))))

(defn alloc [size]
  (let [arena (Arena/ofShared)
        segment (.allocate arena (long (max 1 size)) 8)
        address (.address segment)]
    (swap! allocations* assoc address arena)
    segment))

(defn free [pointer]
  (when-not (null? pointer)
    (when-let [^Arena arena (get @allocations* (.address ^MemorySegment pointer))]
      (swap! allocations* dissoc (.address ^MemorySegment pointer))
      (.close arena)))
  nil)

(defn sizeof [type]
  (let [type (normalize-memory-type type)]
    (if (= :pointer type)
      (.byteSize ValueLayout/ADDRESS)
      (:size (type-info type (abi/descriptor))))))

(defn- segment-for [pointer size]
  (.reinterpret ^MemorySegment pointer (long (max 1 size))))

(defn read-value
  ([pointer type] (read-value pointer type 0))
  ([pointer type offset]
   (let [type (normalize-memory-type type)
         size (sizeof type)
         ^MemorySegment segment (segment-for pointer (+ offset size))
         offset (long offset)]
     (case type
       :pointer (.get segment ValueLayout/ADDRESS offset)
       :c/string (.get segment ValueLayout/ADDRESS offset)
       :c/bool (.get segment ValueLayout/JAVA_BOOLEAN offset)
       :c/int8 (long (.get segment ValueLayout/JAVA_BYTE offset))
       :c/uint8 (bit-and 0xff (long (.get segment ValueLayout/JAVA_BYTE offset)))
       :c/int16 (long (.get segment ValueLayout/JAVA_SHORT offset))
       :c/uint16 (bit-and 0xffff (long (.get segment ValueLayout/JAVA_SHORT offset)))
       :c/int32 (long (.get segment ValueLayout/JAVA_INT offset))
       :c/uint32 (bit-and 0xffffffff (long (.get segment ValueLayout/JAVA_INT offset)))
       :c/int64 (.get segment ValueLayout/JAVA_LONG offset)
       :c/uint64 (.get segment ValueLayout/JAVA_LONG offset)
       :c/size (.get segment ValueLayout/JAVA_LONG offset)
       :c/float (.get segment ValueLayout/JAVA_FLOAT offset)
       :c/double (.get segment ValueLayout/JAVA_DOUBLE offset)
       (throw (ex-info "unsupported JVM memory read type" {:type type}))))))

(defn write-value [pointer type offset value]
  (let [type (normalize-memory-type type)
        size (sizeof type)
        ^MemorySegment segment (segment-for pointer (+ offset size))
        offset (long offset)]
    (case type
      :pointer (set-address! segment offset (or value null))
      :c/string (set-address! segment offset (or value null))
      :c/bool (.set segment ValueLayout/JAVA_BOOLEAN offset
                    (boolean
                     (if (boolean? value)
                       value
                       (not (zero? (number-long value))))))
      (:c/int8 :c/uint8) (.set segment ValueLayout/JAVA_BYTE offset
                               (unchecked-byte (number-long value)))
      (:c/int16 :c/uint16) (.set segment ValueLayout/JAVA_SHORT offset
                                  (unchecked-short (number-long value)))
      (:c/int32 :c/uint32) (.set segment ValueLayout/JAVA_INT offset
                                  (unchecked-int (number-long value)))
      (:c/int64 :c/uint64 :c/size) (.set segment ValueLayout/JAVA_LONG offset
                                             (number-long value))
      :c/float (.set segment ValueLayout/JAVA_FLOAT offset (float value))
      :c/double (.set segment ValueLayout/JAVA_DOUBLE offset (double value))
      (throw (ex-info "unsupported JVM memory write type" {:type type}))))
  nil)

(defn read-array [pointer length]
  (if (zero? length)
    (byte-array 0)
    (.toArray ^MemorySegment (segment-for pointer length)
              ValueLayout/JAVA_BYTE)))

(defn write-array [pointer value]
  (when (pos? (alength ^bytes value))
    (let [source (MemorySegment/ofArray ^bytes value)
          ^MemorySegment target (segment-for pointer (alength ^bytes value))]
      (.copyFrom target source)))
  nil)

(defn read-utf8 [pointer length]
  (String. ^bytes (read-array pointer length) StandardCharsets/UTF_8))

(defn write-utf8 [pointer value]
  (let [bytes (.getBytes ^String value StandardCharsets/UTF_8)]
    (write-array pointer bytes)
    (alength bytes)))

(defn string->native [value]
  (let [bytes (.getBytes ^String (str value) StandardCharsets/UTF_8)
        pointer (alloc (inc (alength bytes)))]
    (write-array pointer bytes)
    (write-value pointer :uint8 (alength bytes) 0)
    pointer))

(defn native->string [pointer]
  (when-not (null? pointer)
    (let [^MemorySegment segment (segment-for pointer Long/MAX_VALUE)]
      (.getString segment 0 StandardCharsets/UTF_8))))

(defn read-field [pointer layout-info path]
  (if-let [{:keys [offset type]} (get (:fields layout-info) path)]
    (read-value pointer type offset)
    (throw (ex-info "unknown struct field path"
                    {:path path :layout (:type layout-info)}))))

(defn write-field [pointer layout-info path value]
  (if-let [{:keys [offset type]} (get (:fields layout-info) path)]
    (write-value pointer type offset value)
    (throw (ex-info "unknown struct field path"
                    {:path path :layout (:type layout-info)}))))
