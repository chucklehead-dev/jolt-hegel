(ns hegel.ffi.backend
  "Selected runtime implementation of the small native boundary."
  (:require #?(:jolt [hegel.ffi.jolt :as impl]
               :bb [hegel.ffi.bb :as impl]
               :jank [hegel.ffi.jank-backend :as impl]
               :clj [hegel.ffi.jvm :as impl])))

(defn load! [library-path] (impl/load! library-path))
(defn function [function-id] (impl/function function-id))
(defn layout [type-id] (impl/layout type-id))

(def null impl/null)
(defn null? [pointer] (impl/null? pointer))
(defn alloc [size] (impl/alloc size))
(defn free [pointer] (impl/free pointer))
(defn sizeof [type] (impl/sizeof type))
(defn read-value
  ([pointer type] (impl/read-value pointer type))
  ([pointer type offset] (impl/read-value pointer type offset)))
(defn write-value [pointer type offset value]
  (impl/write-value pointer type offset value))
(defn read-array [pointer length] (impl/read-array pointer length))
(defn write-array [pointer value] (impl/write-array pointer value))
(defn read-utf8 [pointer length] (impl/read-utf8 pointer length))
(defn write-utf8 [pointer value] (impl/write-utf8 pointer value))
(defn string->native [value] (impl/string->native value))
(defn native->string [pointer] (impl/native->string pointer))
(defn layout-size [layout] (impl/layout-size layout))
(defn read-field [pointer layout path] (impl/read-field pointer layout path))
(defn write-field [pointer layout path value]
  (impl/write-field pointer layout path value))
