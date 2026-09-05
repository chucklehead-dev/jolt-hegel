(ns hegel.header-snapshot
  "Fail-closed offline parser for the pinned libhegel C header."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.security MessageDigest]))

(def fixture-dir "test/fixtures/hegel-0.36.3")
(defn fail! [message data] (throw (ex-info (str "header snapshot parse failed: " message) data)))
(defn read-utf8 [path] (slurp (io/file path) :encoding "UTF-8"))
(defn sha256 [bytes]
  (->> (.digest (doto (MessageDigest/getInstance "SHA-256") (.update bytes)))
       (map #(format "%02x" (bit-and 0xff %)))
       (apply str)))
(defn validate-provenance! [raw provenance]
  (let [bytes (.getBytes raw "UTF-8")
        checks [[:sha256 (sha256 bytes) (:sha256 provenance)]
                [:bytes (alength bytes) (:bytes provenance)]
                [:lines (count (str/split raw #"\n")) (:lines provenance)]]]
    (doseq [[key actual expected] checks]
      (when-not (= actual expected)
        (fail! (str "fixture provenance mismatch for " (name key))
               {:key key :actual actual :expected expected})))
    provenance))
(defn validate-directives! [text]
  (doseq [line (str/split-lines text)
          :when (str/starts-with? (str/trim line) "#")]
    (when-not (re-matches #"\s*#(?:ifndef HEGEL_H|define HEGEL_H|include <(?:stddef|stdint|stdbool)\.h>|define HEGEL_STATE_MACHINE_DONE INT64_MIN|ifdef __cplusplus|endif(?: // __cplusplus| /\* HEGEL_H \*/)?)[ \t]*" line)
      (fail! "unsupported preprocessor directive" {:line line}))))
(defn strip-comments [text]
  (-> text (str/replace #"(?s)/\*.*?\*/" "") (str/replace #"(?m)//.*$" "")))
(defn parse-int [s]
  (let [s (str/trim s)]
    (cond
      (re-matches #"-?\d+" s) (Long/parseLong s)
      (re-matches #"0[xX][0-9a-fA-F]+" s) (Long/parseLong (subs s 2) 16)
      (re-matches #"\(1 << \d+\)" s)
      (bit-shift-left 1 (Long/parseLong (second (re-matches #"\(1 << (\d+)\)" s))))
      (#{"INT64_MIN" "UINT64_MAX"} s) s
      :else (fail! (str "unsupported constant expression " (pr-str s)) {:expression s}))))
(defn unique! [kind entries]
  (let [duplicates (->> entries frequencies (keep (fn [[k n]] (when (> n 1) k))) seq)]
    (when duplicates (fail! (str "duplicate " kind) {:names duplicates})))
  entries)
(defn unique-names! [kind entries]
  (unique! kind (map first entries)) entries)
(defn parse-defines [text]
  (let [entries (->> (re-seq #"(?m)^[ \t]*#define[ \t]+(HEGEL_[A-Z0-9_]+)[ \t]+([^\r\n]+)" text)
                     (map (fn [[_ name value]] [name (parse-int value)])))]
    (into (sorted-map) (unique! "defines" entries))))
(defn parse-enum-items [body]
  (mapv (fn [item]
          (let [[name value] (map str/trim (str/split item #"=" 2))]
            (when-not (re-matches #"HEGEL_[A-Z0-9_]+" name)
              (fail! (str "unsupported enum item " name) {:item item}))
            [name (if value (parse-int value) ::implicit)]))
        (remove str/blank? (str/split body #","))))
(defn parse-enums [text]
  (let [entries (map (fn [[_ body name]]
                       (let [members (parse-enum-items body)]
                         [name (unique-names! (str "enum members in " name) members)]))
                     (re-seq #"(?s)typedef\s+enum\s*\{(.*?)\}\s*(hegel_[a-z0-9_]+)\s*;" text))]
    (into (sorted-map) (unique-names! "enums" entries))))
(defn normalize-type [s]
  (let [s (-> s str/trim (str/replace #"\s+" " "))]
    (cond
      (= s "void") "void" (= s "bool") "bool"
      (= s "int8_t") "int8" (= s "uint8_t") "uint8"
      (= s "int16_t") "int16" (= s "uint16_t") "uint16"
      (= s "int32_t") "int32" (= s "uint32_t") "uint32"
      (= s "int64_t") "int64" (= s "uint64_t") "uint64"
      (= s "size_t") "size" (= s "float") "float" (= s "double") "double"
      (= s "bool *") "bool*"
      (= s "double *") "double*" (= s "float *") "float*"
      (re-matches #"HegelRecursion \*+" s) s
      (re-matches #"(?:const )?(?:u?int(?:8|16|32|64)_t|size_t) \*" s) s
      (= s "void *") "void*" (= s "hegel_output_callback_t") "output-callback"
      (= s "char *") "char*" (= s "const char *") "const-char*"
      (= s "const char *const *") "const-char**"
      (= s "const char **") "const-char**"
      (re-matches #"(?:const )?hegel_[a-z0-9_]+_t \*+" s) s
      (re-matches #"(?:const )?hegel_[a-z0-9_]+_t" s) s
      :else (fail! (str "unsupported C type " (pr-str s)) {:type s}))))
(defn parse-field [field]
  (let [[_ type name] (re-matches #"(.+?)\s+(\*?[a-zA-Z_][a-zA-Z0-9_]*)\s*" (str/trim field))
        pointer? (and name (str/starts-with? name "*"))
        name (when name (str/replace name #"^\*" ""))
        type (if pointer? (str type " *") type)]
    (when-not (and type name) (fail! "unsupported struct field" {:field field}))
    {:name name :type (normalize-type type)}))
(defn parse-structs [text]
  (let [entries (map (fn [[_ body name]]
                       [name (mapv parse-field (remove str/blank? (str/split body #";")))])
                     (re-seq #"(?s)typedef\s+struct\s*\{(.*?)\}\s*(hegel_[a-z0-9_]+_t)\s*;" text))]
    (into (sorted-map) (unique-names! "structs" entries))))
(defn parse-opaque-handles [text]
  (->> (concat (re-seq #"typedef\s+struct\s+(hegel_[a-z0-9_]+_t)\s+\1\s*;" text)
               (re-seq #"typedef\s+struct\s+(HegelRecursion)\s+\1\s*;" text))
       (map second) sort vec))
(declare parse-arg)
(defn parse-callbacks [text]
  (let [matches (re-seq #"typedef\s+void\s+\(\*hegel_output_callback_t\)\(([^;]+)\);" text)]
    (when (and (str/includes? text "hegel_output_callback_t") (empty? matches))
      (fail! "malformed callback typedef" {}))
    (when (> (count matches) 1) (fail! "duplicate callback typedef" {}))
    (into {} (map (fn [[_ args]]
                    (let [parsed (mapv parse-arg (str/split args #","))
                          expected [{:name "user_data" :type "void*"}
                                    {:name "line" :type "const-char*"}
                                    {:name "len" :type "size"}]]
                      (when-not (= expected parsed)
                        (fail! "unsupported callback signature" {:args parsed}))
                      ["hegel_output_callback_t" {:return "void" :args parsed}])) matches))))
(defn parse-arg [arg]
  (let [arg (-> arg str/trim (str/replace #"\s+" " "))]
    (if (or (str/blank? arg) (= arg "void")) nil
        (let [[_ type name] (re-matches #"(.+?)(?:\s+|\s*)([a-zA-Z_][a-zA-Z0-9_]*)$" arg)]
          (when-not (and type name) (fail! "unsupported function argument" {:argument arg}))
          {:name name :type (normalize-type type)}))))
(defn parse-functions [text]
  (let [entries (map (fn [[_ return name args]]
                       [name {:name name :return (normalize-type return)
                              :args (->> (str/split args #",") (map parse-arg) (remove nil?) vec)}])
                     (re-seq #"(?m)(hegel_result_t|hegel_context_t \*|const char \*)\s*(hegel_[a-z0-9_]+)\s*\(([^;]*?)\)\s*;" text))]
    (mapv second (unique-names! "functions" entries))))
(defn remove-recognized [text]
  (-> text
      (str/replace #"(?m)^\s*#.*$" "")
      (str/replace #"(?s)typedef\s+enum\s*\{.*?\}\s*hegel_[a-z0-9_]+\s*;" "")
      (str/replace #"(?s)typedef\s+struct\s*\{.*?\}\s*hegel_[a-z0-9_]+_t\s*;" "")
      (str/replace #"typedef\s+struct\s+(hegel_[a-z0-9_]+_t)\s+\1\s*;" "")
      (str/replace #"typedef\s+struct\s+(HegelRecursion)\s+\1\s*;" "")
      (str/replace #"typedef\s+void\s+\(\*hegel_output_callback_t\)\([^;]+\);" "")
      (str/replace #"(?m)(hegel_result_t|hegel_context_t \*|const char \*)\s+hegel_[a-z0-9_]+\s*\([^;]*?\)\s*;" "")
      (str/replace #"(?m)(?:hegel_context_t \*|const char \*)hegel_[a-z0-9_]+\([^;]*?\);" "")
      (str/replace #"extern\s+\"C\"\s*\{" "")
      (str/replace #"(?m)^\s*#(?:ifndef|define|include|ifdef|endif).*?$" "")
      (str/replace #"\}" "")))
(defn assert-no-unknown! [text]
  (let [residual (-> text (str/replace #"\s+" " ") str/trim)]
    (when-not (str/blank? residual)
      (fail! "unsupported top-level declaration" {:residual (subs residual 0 (min 240 (count residual)))}))))
(defn parse-header [raw]
  (let [text (strip-comments raw)]
    (validate-directives! text)
    (let [result {:defines (parse-defines text) :enums (parse-enums text)
                  :structs (parse-structs text) :opaque-handles (parse-opaque-handles text)
                  :callbacks (parse-callbacks text) :functions (parse-functions text)}]
      (assert-no-unknown! (remove-recognized text)) result)))
(defn snapshot []
  (let [raw (read-utf8 (str fixture-dir "/hegel.h"))
        provenance (edn/read-string (read-utf8 (str fixture-dir "/provenance.edn")))]
    (validate-provenance! raw provenance)
    (assoc (parse-header raw) :provenance provenance)))
(defn -main [& args]
  (let [result (snapshot)]
    (if (= ["--counts"] (vec args))
      (println (pr-str {:defines (count (:defines result)) :enums (count (:enums result))
                        :structs (count (:structs result))
                        :opaque-handles (count (:opaque-handles result))
                        :callbacks (count (:callbacks result))
                        :functions (count (:functions result))}))
      (prn result))))
