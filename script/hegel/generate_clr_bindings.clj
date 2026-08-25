(ns hegel.generate-clr-bindings
  "Generate the managed libhegel P/Invoke surface from resources/hegel/abi.edn."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [hegel.abi :as abi]))

(def ^:private output "clr/Hegel.Native/GeneratedBindings.cs")

(def ^:private scalar-types
  {:c/void "void"
   :c/bool "byte"
   :c/int8 "sbyte"
   :c/uint8 "byte"
   :c/int16 "short"
   :c/uint16 "ushort"
   :c/int32 "int"
   :c/uint32 "uint"
   :c/int64 "long"
   :c/uint64 "ulong"
   :c/size "nuint"
   :c/float "float"
   :c/double "double"
   :c/string "IntPtr"})

(defn- words [value]
  (-> (if (keyword? value)
        (str (when-let [ns (namespace value)] (str ns "-")) (name value))
        (str value))
      (str/replace #"[^A-Za-z0-9]+" "-")
      (str/split #"-")))

(defn- pascal [value]
  (apply str (map str/capitalize (remove str/blank? (words value)))))

(defn- method-name [function-id]
  (pascal function-id))

(defn- struct-name [type-id]
  (pascal type-id))

(declare clr-type)

(defn- clr-type [descriptor type]
  (cond
    (vector? type)
    (case (first type)
      :pointer "IntPtr"
      :by-value (clr-type descriptor (second type)))

    (contains? scalar-types type) (get scalar-types type)

    :else
    (case (get-in descriptor [:types type :kind])
      :struct (struct-name type)
      (:opaque :function-pointer) "IntPtr"
      (throw (ex-info "cannot render CLR ABI type" {:type type})))))

(defn- ordered-struct-types [types]
  (let [structs (into {} (filter #(= :struct (:kind (val %))) types))]
    (loop [remaining structs emitted [] emitted-ids #{}]
      (if (empty? remaining)
        emitted
        (let [ready (->> remaining
                         (filter
                          (fn [[_ {:keys [fields]}]]
                            (every? (fn [{:keys [type]}]
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

(defn- struct-source [descriptor [type-id {:keys [fields]}]]
  (str "[StructLayout(LayoutKind.Sequential)]\n"
       "public struct " (struct-name type-id) "\n{\n"
       (apply str
              (map (fn [{:keys [name type]}]
                     (str "    public " (clr-type descriptor type) " "
                          (pascal name) ";\n"))
                   fields))
       "}"))

(defn- native-method-source [descriptor [function-id function]]
  (let [args (map-indexed
              (fn [index type]
                (str (clr-type descriptor type) " arg" index))
              (:args function))]
    (str "    [LibraryImport(LibraryName, EntryPoint = \"" (:symbol function) "\")]\n"
         "    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]\n"
         "    internal static partial " (clr-type descriptor (:return function))
         " " (method-name function-id) "(" (str/join ", " args) ");")))

(defn- argument-expression [descriptor index type]
  (let [value (str "args[" index "]")]
    (cond
      (vector? type)
      (case (first type)
        :pointer (str "ToIntPtr(" value ")")
        :by-value (str "Marshal.PtrToStructure<" (clr-type descriptor type)
                       ">(ToIntPtr(" value "))"))

      (= :c/string type) (str "ToIntPtr(" value ")")
      (= :function-pointer (get-in descriptor [:types type :kind]))
      (str "ToIntPtr(" value ")")
      (= :c/bool type) (str "ToByte(" value ")")
      (= :c/int8 type) (str "Convert.ToSByte(" value ", CultureInfo.InvariantCulture)")
      (= :c/uint8 type) (str "ToByte(" value ")")
      (= :c/int16 type) (str "Convert.ToInt16(" value ", CultureInfo.InvariantCulture)")
      (= :c/uint16 type) (str "Convert.ToUInt16(" value ", CultureInfo.InvariantCulture)")
      (= :c/int32 type) (str "Convert.ToInt32(" value ", CultureInfo.InvariantCulture)")
      (= :c/uint32 type) (str "Convert.ToUInt32(" value ", CultureInfo.InvariantCulture)")
      (= :c/int64 type) (str "Convert.ToInt64(" value ", CultureInfo.InvariantCulture)")
      (= :c/uint64 type) (str "ToUInt64(" value ")")
      (= :c/size type) (str "ToNUInt(" value ")")
      (= :c/float type) (str "Convert.ToSingle(" value ", CultureInfo.InvariantCulture)")
      (= :c/double type) (str "Convert.ToDouble(" value ", CultureInfo.InvariantCulture)")
      :else (throw (ex-info "cannot convert CLR argument" {:type type})))))

(defn- invoker-source [descriptor [function-id function]]
  (let [method (method-name function-id)
        args (str/join ", "
                       (map-indexed #(argument-expression descriptor %1 %2)
                                    (:args function)))
        call (str "NativeMethods." method "(" args ")")]
    (str "    private static object? Invoke" method "(object?[] args)\n"
         "    {\n"
         "        RequireArity(\"" (name function-id) "\", args, "
         (count (:args function)) ");\n"
         (if (= :c/void (:return function))
           (str "        " call ";\n        return null;\n")
           (str "        return " call ";\n"))
         "    }")))

(defn- generated-source [descriptor]
  (let [functions (sort-by key (:functions descriptor))
        structs (ordered-struct-types (:types descriptor))]
    (str "// Generated from resources/hegel/abi.edn. DO NOT EDIT.\n"
         "using System;\n"
         "using System.Collections.Generic;\n"
         "using System.Globalization;\n"
         "using System.Runtime.CompilerServices;\n"
         "using System.Runtime.InteropServices;\n\n"
         "namespace Hegel.Native;\n\n"
         (str/join "\n\n" (map #(struct-source descriptor %) structs))
         "\n\ninternal static partial class NativeMethods\n{\n"
         "    internal const string LibraryName = \"hegel\";\n\n"
         (str/join "\n\n" (map #(native-method-source descriptor %) functions))
         "\n}\n\n"
         "public static partial class Bridge\n{\n"
         "    public static IReadOnlyList<string> ExpectedSymbols { get; } = new string[]\n"
         "    {\n"
         (apply str (map (fn [[_ function]]
                           (str "        \"" (:symbol function) "\",\n"))
                         functions))
         "    };\n\n"
         "    public static object? Invoke(string functionId, object?[]? values)\n"
         "    {\n"
         "        var args = values ?? Array.Empty<object?>();\n"
         "        return functionId switch\n"
         "        {\n"
         (apply str (map (fn [[function-id _]]
                           (str "            \"" (name function-id) "\" => Invoke"
                                (method-name function-id) "(args),\n"))
                         functions))
         "            _ => throw new ArgumentOutOfRangeException(nameof(functionId), functionId, \"Unknown libhegel function\"),\n"
         "        };\n"
         "    }\n\n"
         (str/join "\n\n" (map #(invoker-source descriptor %) functions))
         "\n}\n")))

(defn- write-output! [content]
  (io/make-parents output)
  (spit output content)
  (println "generated" output))

(defn- check-output! [content]
  (let [file (io/file output)]
    (when-not (and (.isFile file) (= content (slurp file)))
      (throw (ex-info (str "generated CLR artifact is stale: " output)
                      {:type ::stale-generated-artifact :path output})))
    (println "PASS generated artifact is current:" output)))

(defn -main [& args]
  (let [check? (= ["--check"] (vec args))
        content (generated-source (abi/validate!))]
    (when-not (or (empty? args) check?)
      (throw (ex-info "usage: generate-clr-bindings [--check]" {:args args})))
    (if check?
      (check-output! content)
      (write-output! content))))
