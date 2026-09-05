(ns hegel.header-layout
  "Compile a pinned C-header layout probe; this measures one target compiler."
  (:refer-clojure :exclude [run!])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hegel.header-snapshot :as header])
  (:import [java.nio.file Files Path Paths]
           [java.util UUID]
           [java.util.concurrent TimeUnit]))

(def ^:private default-header "test/fixtures/hegel-0.33.3/hegel.h")
(def ^:private default-timeout-ms 10000)

(def ^:private primitive-expressions
  [["char" "char"] ["bool" "bool"]
   ["int8_t" "int8_t"] ["uint8_t" "uint8_t"]
   ["int16_t" "int16_t"] ["uint16_t" "uint16_t"]
   ["int32_t" "int32_t"] ["uint32_t" "uint32_t"]
   ["int64_t" "int64_t"] ["uint64_t" "uint64_t"]
   ["size_t" "size_t"] ["float" "float"] ["double" "double"]
   ["pointer" "void *"]])

(defn- include-name [value]
  ;; A preprocessor header-name is not a C string literal. Use forward path
  ;; separators on Windows and reject characters that cannot be represented
  ;; safely inside a quoted #include directive; do not apply regex replacement
  ;; string escaping (which previously emitted literal $0 for backslashes).
  (when (re-find #"[\"\r\n]" value)
    (throw (ex-info "Unsupported character in C header path" {:path value})))
  (str "\"" (str/replace value "\\" "/") "\""))

(defn- emit-map-entry [key expression]
  (str "printf(" (pr-str (str ":" key " %zu ")) ", (size_t)(" expression "));\n"))

(defn- emit-enum-entry [key expression]
  ;; The parsed header only admits integer enum expressions. Cast after C has
  ;; evaluated the expression so the probe reports its target compiler value.
  (str "printf(" (pr-str (str ":" key " %lld ")) ", (long long)(" expression "));\n"))

(defn- emit-text [text]
  (str "printf(" (pr-str text) ");\n"))

(defn- emit-open-map [key]
  (emit-text (str ":" key " {")))

(defn probe-source
  "Return C11 source that emits EDN for the snapshot's concrete ABI layout.
  `header-path` is embedded as the sole non-system include."
  [snapshot header-path]
  (let [structs (:structs snapshot)
        enums (:enums snapshot)
        defines (:defines snapshot)]
    (str "#include <stdio.h>\n"
         "#include <stddef.h>\n"
         "#include <stdint.h>\n"
         "#include <limits.h>\n"
         "#include <stdbool.h>\n"
         "#include " (include-name (.getAbsolutePath (io/file header-path))) "\n\n"
         "int main(void) {\n"
         (emit-text "{")
         (emit-enum-entry "char-bit" "CHAR_BIT")
         (emit-text ":primitives {")
         (apply str (map (fn [[name type]] (emit-map-entry name (str "sizeof(" type ")")))
                         primitive-expressions))
         (emit-text "} :primitive-alignments {")
         (apply str (map (fn [[name type]] (emit-map-entry name (str "_Alignof(" type ")")))
                         primitive-expressions))
         (emit-text "} :defines {")
         (apply str (map (fn [[name _]] (emit-enum-entry name name)) defines))
         (emit-text "} :enums {")
         (apply str
                (for [[enum-name members] enums]
                  (str (emit-open-map enum-name)
                       (apply str (map (fn [[name _]] (emit-enum-entry name name)) members))
                       (emit-text "} "))))
         (emit-text "} :enum-layouts {")
         (apply str
                (for [[enum-name _] enums]
                  (str "printf(" (pr-str (str ":" enum-name " {:size %zu :align %zu} ")) ", "
                       "sizeof(" enum-name "), (size_t)_Alignof(" enum-name "));\n")))
         (emit-text "} :structs {")
         (apply str
                (for [[struct-name fields] structs]
                  (str "printf(" (pr-str (str ":" struct-name " {:size %zu :align %zu :fields {")) ", "
                       "sizeof(" struct-name "), (size_t)_Alignof(" struct-name "));\n"
                       (apply str
                              (for [{:keys [name]} fields]
                                (emit-map-entry name
                                                (str "offsetof(" struct-name ", " name ")"))))
                       (emit-text "}} "))))
         (emit-text "}}}\n")
         "return 0;\n"
         "}\n")))

(defn- task-dir! []
  (let [root (Paths/get "target" (make-array String 0))]
    (Files/createDirectories root (make-array java.nio.file.attribute.FileAttribute 0))
    (Files/createTempDirectory root "header-layout-"
                               (make-array java.nio.file.attribute.FileAttribute 0))))

(defn- read-log [path]
  (try (slurp (.toFile path)) (catch Exception _ "<unreadable>")))

(defn- start! [phase command work-dir log-file]
  (try
    (-> (ProcessBuilder. ^java.util.List command)
        (.directory (.toFile work-dir))
        (.redirectErrorStream true)
        (.redirectOutput (.toFile log-file))
        (.start))
    (catch Exception e
      (throw (ex-info "C layout probe could not start"
                      {:phase phase :command command :log (str log-file)} e)))))

(defn- force-reap! [process]
  (when (.isAlive process) (.destroyForcibly process))
  (try
    {:forced? true
     :reaped? (.waitFor process 1000 TimeUnit/MILLISECONDS)
     :alive? (.isAlive process)}
    (catch InterruptedException _
      (.interrupt (Thread/currentThread))
      {:forced? true :reaped? false :alive? (.isAlive process)
       :interrupted? true})))

(defn- run! [phase command work-dir log-file timeout-ms]
  (let [process (start! phase command work-dir log-file)
        exited? (atom false)]
    (try
      (if (.waitFor process timeout-ms TimeUnit/MILLISECONDS)
        (do
          (reset! exited? true)
          (let [exit (.exitValue process)]
            (when-not (zero? exit)
              (let [output (read-log log-file)]
                (throw (ex-info (str "C layout probe failed during " (name phase)
                                    " (exit " exit "): "
                                    (subs output 0 (min 8000 (count output))))
                                {:phase phase :command command :exit exit :log (str log-file)
                                 :output output}))))
            {:exit exit :log (str log-file)}))
        (let [reap (force-reap! process)]
          (throw (ex-info "C layout probe timed out"
                          (merge {:phase phase :command command :timeout-ms timeout-ms
                                  :log (str log-file)} reap)))))
      (finally
        ;; This covers interruption and any error after start; normal nonzero
        ;; exits are already reaped and do not need another termination call.
        (when (and (not @exited?) (.isAlive process))
          (force-reap! process))))))

(defn measure!
  "Compile and run the generated C11 probe. Returns compiler-observed EDN.
  `compiler` must accept cc/clang-style C11 flags (MSVC is not supported).
  Logs and source are retained in a unique target/header-layout-* directory."
  ([snapshot] (measure! snapshot {}))
  ([snapshot {:keys [compiler header-path timeout-ms work-dir]
              :or {compiler (or (System/getenv "HEGEL_ABI_CC") "cc")
                   header-path default-header timeout-ms default-timeout-ms}}]
   (let [work-dir (or work-dir (task-dir!))
         work-dir (if (instance? Path work-dir) work-dir (Paths/get (str work-dir) (make-array String 0)))
         work-dir (.toAbsolutePath work-dir)
         _ (Files/createDirectories work-dir (make-array java.nio.file.attribute.FileAttribute 0))
         source (.resolve work-dir "header-layout-probe.c")
         executable (.resolve work-dir (str "header-layout-probe-" (UUID/randomUUID)
                                        (when (str/starts-with?
                                              (str/lower-case (System/getProperty "os.name"))
                                              "windows") ".exe")))
         compile-log (.resolve work-dir "compile.log")
         run-log (.resolve work-dir "run.log")]
     (spit (.toFile source) (probe-source snapshot header-path))
     (run! :compile [compiler "-std=c11" (str source) "-o" (str executable)]
           work-dir compile-log timeout-ms)
     (run! :probe [(str executable)] work-dir run-log timeout-ms)
     (try
       (assoc (edn/read-string (read-log run-log))
              :probe {:work-dir (str work-dir)
                      :compile-log (str compile-log)
                      :run-log (str run-log)})
       (catch Exception e
         (throw (ex-info "C layout probe emitted invalid EDN"
                         {:work-dir (str work-dir) :run-log (str run-log)
                          :output (read-log run-log)} e)))))))

(defn -main [& _]
  (prn (measure! (header/snapshot))))
