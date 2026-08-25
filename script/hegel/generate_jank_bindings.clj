(ns hegel.generate-jank-bindings
  "Generate deterministic jank ABI artifacts from resources/hegel/abi.edn."
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [hegel.abi :as abi]))

(def ^:private pointer-size 8)

(def ^:private outputs
  {:abi-data "src/hegel/abi_data.jank"
   :bindings "src/hegel/ffi/jank_generated.jank"
   :header "generated/hegel/jank/libhegel_abi.hpp"})

(defn- canonical [value]
  (cond
    (map? value)
    (into (sorted-map-by #(compare (pr-str %1) (pr-str %2)))
          (map (fn [[key item]] [(canonical key) (canonical item)]))
          value)

    (vector? value) (mapv canonical value)
    (set? value) (into (sorted-set-by #(compare (pr-str %1) (pr-str %2)))
                       (map canonical)
                       value)
    (seq? value) (map canonical value)
    :else value))

(defn- pprint-str [value]
  (with-out-str
    (binding [pprint/*print-right-margin* 100
              pprint/*print-miser-width* 60]
      (pprint/pprint (canonical value)))))

(defn- align-up [offset alignment]
  (let [remainder (mod offset alignment)]
    (if (zero? remainder)
      offset
      (+ offset (- alignment remainder)))))

(declare type-layout)

(defn- scalar-layout [{:keys [kind bits]}]
  (case kind
    :void {:size 0 :alignment 1}
    :boolean {:size (quot bits 8) :alignment (quot bits 8)}
    :integer (let [size (if (= :pointer-width bits)
                          pointer-size
                          (quot bits 8))]
               {:size size :alignment size})
    :float (let [size (quot bits 8)]
             {:size size :alignment size})
    :string {:size pointer-size :alignment pointer-size}
    :opaque nil
    :function-pointer {:size pointer-size :alignment pointer-size}
    nil))

(defn- struct-layout [descriptor type-id]
  (let [fields (get-in descriptor [:types type-id :fields])
        built
        (reduce
         (fn [{:keys [offset alignment field-offsets]} {:keys [name type]}]
           (let [{child-size :size child-alignment :alignment
                  child-fields :fields}
                 (type-layout descriptor type)
                 field-offset (align-up offset child-alignment)
                 nested-offsets
                 (if child-fields
                   (into {[name] {:offset field-offset :type type}}
                         (map (fn [[path nested]]
                                [(into [name] path)
                                 (update nested :offset + field-offset)]))
                         child-fields)
                   {[name] {:offset field-offset :type type}})]
             {:offset (+ field-offset child-size)
              :alignment (max alignment child-alignment)
              :field-offsets (merge field-offsets nested-offsets)}))
         {:offset 0 :alignment 1 :field-offsets {}}
         fields)
        size (align-up (:offset built) (:alignment built))]
    {:size size
     :alignment (:alignment built)
     :fields (:field-offsets built)}))

(defn- type-layout [descriptor type]
  (cond
    (vector? type)
    (case (first type)
      :pointer {:size pointer-size :alignment pointer-size}
      :by-value (type-layout descriptor (second type)))

    :else
    (let [type-descriptor (get-in descriptor [:types type])]
      (or (scalar-layout type-descriptor)
          (case (:kind type-descriptor)
            :struct (struct-layout descriptor type)
            (throw (ex-info "type has no concrete layout"
                            {:type type :descriptor type-descriptor})))))))

(defn- concrete-layouts [descriptor]
  (into {}
        (keep (fn [[type-id type-descriptor]]
                (when (not= :opaque (:kind type-descriptor))
                  [type-id (assoc (type-layout descriptor type-id)
                                  :type type-id)])))
        (:types descriptor)))

(def ^:private scalar-cpp-types
  {:c/void "void"
   :c/bool "std::uint8_t"
   :c/int8 "std::int8_t"
   :c/uint8 "std::uint8_t"
   :c/int16 "std::int16_t"
   :c/uint16 "std::uint16_t"
   :c/int32 "std::int32_t"
   :c/uint32 "std::uint32_t"
   :c/int64 "std::int64_t"
   :c/uint64 "std::uint64_t"
   :c/size "std::size_t"
   :c/float "float"
   :c/double "double"
   :c/string "char const *"})

(defn- identifier [value]
  (-> (if (keyword? value)
        (str (when-let [namespace (namespace value)] (str namespace "_"))
             (name value))
        (str value))
      (str/replace #"[^A-Za-z0-9_]" "_")
      (str/replace #"_+" "_")))

(defn- cpp-type-name [type-id]
  (str "hegel_jank_" (identifier type-id)))

(declare cpp-type)

(defn- cpp-type [descriptor type]
  (cond
    (vector? type)
    (let [[kind target] type]
      (case kind
        :pointer (str (cpp-type descriptor target) " *")
        :by-value (cpp-type descriptor target)))

    (contains? scalar-cpp-types type) (get scalar-cpp-types type)

    :else
    (case (get-in descriptor [:types type :kind])
      (:opaque :struct :function-pointer) (cpp-type-name type)
      (throw (ex-info "cannot render C++ ABI type" {:type type})))))

(defn- function-pointer-declaration [descriptor type-id type-descriptor]
  (str "using " (cpp-type-name type-id) " = "
       (cpp-type descriptor (:return type-descriptor)) " (*) ("
       (if (seq (:args type-descriptor))
         (str/join ", " (map #(cpp-type descriptor %) (:args type-descriptor)))
         "void")
       ");"))

(defn- struct-declaration [descriptor type-id type-descriptor]
  (str "struct " (cpp-type-name type-id) "\n{\n"
       (apply str
              (map (fn [{:keys [name type]}]
                     (str "  " (cpp-type descriptor type) " " (identifier name) ";\n"))
                   (:fields type-descriptor)))
       "};"))

(defn- function-alias [descriptor function-id function]
  (str "using hegel_jank_fn_" (identifier function-id) " = "
       (cpp-type descriptor (:return function)) " (*) ("
       (if (seq (:args function))
         (str/join ", " (map #(cpp-type descriptor %) (:args function)))
         "void")
       "); // " (:symbol function)))

(defn- call-wrapper [descriptor function-id function]
  (let [id (identifier function-id)
        args (map-indexed (fn [index type]
                            (str (cpp-type descriptor type) " arg" index))
                          (:args function))
        values (map #(str "arg" %) (range (count (:args function))))]
    (str "inline " (cpp-type descriptor (:return function))
         " hegel_jank_call_" id "(hegel_jank_bindings *bindings"
         (when (seq args) (str ", " (str/join ", " args))) ")\n{\n  "
         (when-not (= :c/void (:return function)) "return ")
         "bindings->" id "(" (str/join ", " values) ");\n}")))

(defn- ordered-struct-types [types]
  (let [structs (into {} (filter #(= :struct (:kind (val %))) types))]
    (loop [remaining structs
           emitted []
           emitted-ids #{}]
      (if (empty? remaining)
        emitted
        (let [ready (->> remaining
                         (filter
                          (fn [[_ {:keys [fields]}]]
                            (every?
                             (fn [{:keys [type]}]
                               (or (not (contains? structs type))
                                   (contains? emitted-ids type)))
                             fields)))
                         (sort-by key)
                         vec)]
          (when (empty? ready)
            (throw (ex-info "cyclic by-value struct dependency"
                            {:remaining (keys remaining)})))
          (recur (apply dissoc remaining (map first ready))
                 (into emitted ready)
                 (into emitted-ids (map first ready))))))))

(defn- header-source [descriptor layouts]
  (let [types (:types descriptor)
        opaque-types (sort-by key (filter #(= :opaque (:kind (val %))) types))
        struct-types (ordered-struct-types types)
        callback-types (sort-by key (filter #(= :function-pointer (:kind (val %))) types))]
    (str "// Generated from resources/hegel/abi.edn. DO NOT EDIT.\n"
         "#pragma once\n\n"
         "#include <cstddef>\n#include <cstdint>\n#include <cstdlib>\n#include <cstring>\n"
         "#include <stdexcept>\n#include <string>\n#include <type_traits>\n"
         "#include <jank/runtime/convert.hpp>\n#include <jank/runtime/rtti.hpp>\n#include <jank/runtime/obj/big_integer.hpp>\n#include <jank/runtime/obj/persistent_string.hpp>\n"
         "#if defined(_WIN32)\n#include <windows.h>\n#else\n#include <dlfcn.h>\n#endif\n\n"
         "extern \"C\"\n{\n"
         (apply str
                (map (fn [[type-id _]]
                       (str "struct " (cpp-type-name type-id) ";\n"))
                     opaque-types))
         "\n"
         (str/join "\n\n"
                   (map (fn [[type-id type-descriptor]]
                          (struct-declaration descriptor type-id type-descriptor))
                        struct-types))
         "\n\n"
         (str/join "\n"
                   (map (fn [[type-id type-descriptor]]
                          (function-pointer-declaration descriptor type-id type-descriptor))
                        callback-types))
         "\n\n"
         (str/join "\n"
                   (map (fn [[function-id function]]
                          (function-alias descriptor function-id function))
                        (sort-by key (:functions descriptor))))
         "\n}\n\n"
         "struct hegel_jank_bindings\n{\n  void *library{};\n"
         (apply str
                (map (fn [[function-id _]]
                       (str "  hegel_jank_fn_" (identifier function-id) " "
                            (identifier function-id) "{};\n"))
                     (sort-by key (:functions descriptor))))
         "};\n\n"
         "inline hegel_jank_bindings *hegel_jank_active_bindings{};\n\n"
         "inline void *hegel_jank_open_library(std::string const &path)\n{\n"
         "#if defined(_WIN32)\n  auto handle = LoadLibraryA(path.c_str());\n"
         "  if(handle == nullptr) { throw std::runtime_error{\"LoadLibraryA failed for \" + path}; }\n"
         "  return reinterpret_cast<void *>(handle);\n#else\n"
         "  auto handle = dlopen(path.c_str(), RTLD_NOW | RTLD_LOCAL);\n"
         "  if(handle == nullptr) { throw std::runtime_error{dlerror()}; }\n  return handle;\n#endif\n}\n\n"
         "inline void *hegel_jank_find_symbol(void *library, char const *symbol)\n{\n"
         "#if defined(_WIN32)\n  auto address = GetProcAddress(reinterpret_cast<HMODULE>(library), symbol);\n"
         "#else\n  dlerror();\n  auto address = dlsym(library, symbol);\n#endif\n"
         "  if(address == nullptr) { throw std::runtime_error{std::string{\"libhegel symbol not found: \"} + symbol}; }\n"
         "  return reinterpret_cast<void *>(address);\n}\n\n"
         "inline hegel_jank_bindings *hegel_jank_load_bindings(std::string const &path)\n{\n"
         "  if(hegel_jank_active_bindings != nullptr) { return hegel_jank_active_bindings; }\n"
         "  auto *bindings = new hegel_jank_bindings{};\n"
         "  bindings->library = hegel_jank_open_library(path);\n"
         (apply str
                (map (fn [[function-id function]]
                       (let [id (identifier function-id)]
                         (str "  bindings->" id " = reinterpret_cast<hegel_jank_fn_" id ">"
                              "(hegel_jank_find_symbol(bindings->library, \"" (:symbol function) "\"));\n")))
                     (sort-by key (:functions descriptor))))
         "  hegel_jank_active_bindings = bindings;\n"
         "  return bindings;\n}\n\n"
         "inline hegel_jank_bindings *hegel_jank_current_bindings()\n{\n"
         "  if(hegel_jank_active_bindings == nullptr) { throw std::runtime_error{\"libhegel bindings are not loaded\"}; }\n"
         "  return hegel_jank_active_bindings;\n}\n\n"
         (str/join "\n\n"
                   (map (fn [[function-id function]]
                          (call-wrapper descriptor function-id function))
                        (sort-by key (:functions descriptor))))
         "\n\ninline void *hegel_jank_null_pointer() { return nullptr; }\n"
         "inline void *hegel_jank_alloc(std::size_t size) { return std::calloc(1, size == 0 ? 1 : size); }\n"
         "inline void hegel_jank_free(void *pointer) { std::free(pointer); }\n"
         "inline bool hegel_jank_null_pointer_p(void *pointer) { return pointer == nullptr; }\n"
         "inline void *hegel_jank_read_pointer(void *pointer, std::size_t offset) { return *reinterpret_cast<void **>(static_cast<std::uint8_t *>(pointer) + offset); }\n"
         "inline void hegel_jank_write_pointer(void *pointer, std::size_t offset, void *value) { *reinterpret_cast<void **>(static_cast<std::uint8_t *>(pointer) + offset) = value; }\n"
         (apply str
                (map (fn [[type-id cpp-type]]
                       (let [id (identifier type-id)]
                         (str "inline " cpp-type " hegel_jank_read_" id "(void *pointer, std::size_t offset) { return *reinterpret_cast<" cpp-type " *>(static_cast<std::uint8_t *>(pointer) + offset); }\n"
                              "inline void hegel_jank_write_" id "(void *pointer, std::size_t offset, " cpp-type " value) { *reinterpret_cast<" cpp-type " *>(static_cast<std::uint8_t *>(pointer) + offset) = value; }\n")))
                     (sort-by key (dissoc scalar-cpp-types :c/void :c/string))))
         "inline std::string hegel_jank_read_utf8(void *pointer, std::size_t length) { return std::string{static_cast<char const *>(pointer), length}; }\n"
         "inline std::size_t hegel_jank_write_utf8(void *pointer, ::jank::runtime::object_ref value) { auto string = ::jank::runtime::try_object<::jank::runtime::obj::persistent_string>(value); std::memcpy(pointer, string->data.data(), string->data.size()); return string->data.size(); }\n"
         "inline void *hegel_jank_string_dup(::jank::runtime::object_ref value) { auto string = ::jank::runtime::try_object<::jank::runtime::obj::persistent_string>(value); auto *result = static_cast<char *>(hegel_jank_alloc(string->data.size() + 1)); std::memcpy(result, string->data.data(), string->data.size()); result[string->data.size()] = '\\0'; return result; }\n"
         "inline std::string hegel_jank_native_string(void *pointer) { return pointer == nullptr ? std::string{} : std::string{static_cast<char const *>(pointer)}; }\n\n"
         "inline std::uint64_t hegel_jank_to_uint64(::jank::runtime::object_ref value) { if(value.get_type() == ::jank::runtime::object_type::big_integer) { return ::jank::runtime::try_object<::jank::runtime::obj::big_integer>(value)->data.convert_to<std::uint64_t>(); } return ::jank::runtime::convert<std::uint64_t>::from_object(value); }\n\n"
         (apply str
                (map (fn [[type-id type-descriptor]]
                       (let [layout (get layouts type-id)
                             type-name (cpp-type-name type-id)]
                         (str "static_assert(std::is_standard_layout_v<" type-name ">);\n"
                              "static_assert(sizeof(" type-name ") == " (:size layout) ");\n"
                              "static_assert(alignof(" type-name ") == " (:alignment layout) ");\n"
                              (apply str
                                     (map (fn [{:keys [name]}]
                                            (str "static_assert(offsetof(" type-name ", "
                                                 (identifier name) ") == "
                                                 (get-in layout [:fields [name] :offset]) ");\n"))
                                          (:fields type-descriptor)))
                              "\n")))
                     struct-types)))))

(defn- binding-specs [descriptor]
  (into {}
        (map (fn [[function-id function]]
               [function-id
                (assoc (select-keys function [:symbol :args :return :blocking?])
                       :cpp-function-type
                       (str "hegel_jank_fn_" (identifier function-id))
                       :route :jank/generated
                       :native-ready? false)]))
        (:functions descriptor)))

(declare jank-type-form)

(defn- jank-type-form [descriptor type]
  (cond
    (vector? type)
    (let [[kind target] type]
      (case kind
        :pointer (str "(:* " (jank-type-form descriptor target) ")")
        :by-value (jank-type-form descriptor target)))

    (= :c/string type) "(:* (:const cpp/char))"
    (contains? scalar-cpp-types type)
    (case type
      :c/bool "cpp/std.uint8_t"
      :c/int8 "cpp/std.int8_t"
      :c/uint8 "cpp/std.uint8_t"
      :c/int16 "cpp/std.int16_t"
      :c/uint16 "cpp/std.uint16_t"
      :c/int32 "cpp/std.int32_t"
      :c/uint32 "cpp/std.uint32_t"
      :c/int64 "cpp/std.int64_t"
      :c/uint64 "cpp/std.uint64_t"
      :c/size "cpp/std.size_t"
      :c/float "cpp/float"
      :c/double "cpp/double"
      :c/void "cpp/void")
    :else (str "cpp/" (cpp-type-name type))))

(defn- jank-argument [descriptor type index]
  (let [value (str "(nth values " index ")")
        native-pointer (str "(cpp/unbox (:* cpp/void) " value ")")]
    (cond
      (vector? type)
      (let [[kind target] type]
        (case kind
          :pointer
          (str "(cpp/unsafe-cast " (jank-type-form descriptor type)
               " " native-pointer ")")
          :by-value
          (str "(cpp/* (cpp/unsafe-cast (:* " (jank-type-form descriptor target)
               ") " native-pointer "))")))

      (= :c/string type)
      (str "(cpp/unsafe-cast " (jank-type-form descriptor type)
           " " native-pointer ")")

      (= :function-pointer (get-in descriptor [:types type :kind]))
      (str "(cpp/unsafe-cast " (jank-type-form descriptor type)
           " " native-pointer ")")

      (= :c/uint64 type)
      (str "(cpp/hegel_jank_to_uint64 " value ")")

      :else
      (str "(cpp/cast " (jank-type-form descriptor type) " " value ")"))))

(defn- jank-invoker [descriptor function-id function]
  (let [call (str "(cpp/hegel_jank_call_" (identifier function-id)
                  " native-bindings"
                  (when (seq (:args function))
                    (str "\n     "
                         (str/join "\n     "
                                   (map-indexed #(jank-argument descriptor %2 %1)
                                                (:args function)))))
                  ")")
        return (:return function)
        pointer-result?
        (or (= :c/string return)
            (and (vector? return) (= :pointer (first return)))
            (= :function-pointer (get-in descriptor [:types return :kind])))]
    (str "(defn invoke-" (identifier function-id) " [& values]\n"
         "  (let [native-bindings (cpp/hegel_jank_current_bindings)]\n"
         "    " (if pointer-result?
                    (str "(cpp/box (cpp/unsafe-cast (:* cpp/void) " call "))")
                    call)
         "))")))

(defn- abi-data-source [descriptor]
  (str ";; Generated from resources/hegel/abi.edn. DO NOT EDIT.\n"
       "(ns hegel.abi-data\n  (:refer-clojure :exclude [descriptor]))\n\n"
       "(def descriptor\n"
       (pprint-str descriptor)
       ")\n"))

(defn- bindings-source [descriptor layouts]
  (str ";; Generated from resources/hegel/abi.edn. DO NOT EDIT.\n"
       "(ns hegel.ffi.jank-generated\n"
       "  (:include \"hegel/jank/libhegel_abi.hpp\"))\n\n"
       "(def pointer-size " pointer-size ")\n\n"
       "(def binding-specs\n"
       (pprint-str (binding-specs descriptor))
       ")\n\n"
       "(def type-layouts\n"
       (pprint-str layouts)
       ")\n\n"
       (str/join "\n\n"
                 (map (fn [[function-id function]]
                        (jank-invoker descriptor function-id function))
                      (sort-by key (:functions descriptor))))
       "\n\n(def invokers\n"
       (pprint-str
        (into {}
              (map (fn [[function-id _]]
                     [function-id (symbol (str "invoke-" (identifier function-id)))]))
              (:functions descriptor)))
       ")\n"))

(defn- rendered-outputs []
  (let [descriptor (abi/validate!)
        layouts (concrete-layouts descriptor)]
    {:abi-data (abi-data-source descriptor)
     :bindings (bindings-source descriptor layouts)
     :header (str/replace (header-source descriptor layouts) #"\n+\z" "\n")}))

(defn- write-output! [path content]
  (io/make-parents path)
  (spit path content)
  (println "generated" path))

(defn- check-output! [path expected]
  (let [file (io/file path)]
    (when-not (and (.isFile file) (= expected (slurp file)))
      (throw (ex-info (str "generated jank artifact is stale: " path)
                      {:type ::stale-generated-artifact
                       :path path})))
    (println "PASS generated artifact is current:" path)))

(defn -main [& args]
  (let [check? (= ["--check"] (vec args))
        rendered (rendered-outputs)]
    (when-not (or (empty? args) check?)
      (throw (ex-info "usage: generate-jank-bindings [--check]"
                      {:args args})))
    (doseq [[id path] outputs]
      (if check?
        (check-output! path (get rendered id))
        (write-output! path (get rendered id))))))
