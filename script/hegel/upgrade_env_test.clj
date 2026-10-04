(ns hegel.upgrade-env-test
  "Fresh-process profile/environment and Antithesis controls (Babashka only)."
  (:require [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.test :as ct]
            [hegel.clojure-test :as hc]
            [hegel.core :as h]
            [hegel.ffi :as f]
            [hegel.generator :as g]))

(def opts {:profile "base" :test-cases 1 :seed 1 :database "" :verbosity :quiet})

(defn- check! [label condition]
  (when-not condition (throw (ex-info "upgrade environment control failed" {:control label})))
  (println "PASS" label))

(defn- child [mode overrides]
  (let [base-env (into {} (remove (fn [[name _]]
                                   (or (str/starts-with? name "HEGEL_")
                                       (= name "ANTITHESIS_OUTPUT_DIR"))))
                       (System/getenv))
        result (shell/sh "bb" "upgrade-env-test" mode
                         :env (merge base-env {"HEGEL_LIBHEGEL_LIBRARY" f/library-path} overrides))]
    (when-not (zero? (:exit result))
      (throw (ex-info "upgrade environment child failed" {:mode mode :exit (:exit result)})))
    (edn/read-string (:out result))))

(defn- run-parent! []
  (let [directory (.toFile (java.nio.file.Files/createTempDirectory
                           "hegel-upgrade-env-" (make-array java.nio.file.attribute.FileAttribute 0)))
        config (io/file directory "hegel.toml")
        output (io/file directory "antithesis")
        sdk (io/file output "sdk.jsonl")]
    (try
      (spit config "[profiles.named]\nextends = \"base\"\ntest_cases = 2\nprint_blob = true\n")
      (.mkdir output)
      (let [result (child "settings" {"HEGEL_CONFIG" (.getPath config)
                                      "HEGEL_DEFAULT_PROFILE" "named"
                                      "HEGEL_TEST_CASES" "4"
                                      "HEGEL_SEED" "18446744073709551615"
                                      "HEGEL_PRINT_BLOB" "false"})]
        (check! "environment wins over TOML and registered profiles"
                (= [4 4 7 false 18446744073709551615N] (:settings result)))
        (check! "run records the effective uint64 environment seed"
                (= {:passed? true :seed "18446744073709551615" :valid-test-cases 4}
                   (:run result))))
      (check! "malformed environment rejects settings before property execution"
              (= {:native-error? true :operation :settings-new-for-profile :calls 0}
                 (child "startup" {"HEGEL_CONFIG" (.getPath config) "HEGEL_TEST_CASES" "lots"})))
      (check! "missing Antithesis directory rejects startup before property execution"
              (= {:native-error? true :operation :run-start :calls 0}
                 (child "startup" {"HEGEL_CONFIG" (.getPath config)
                                   "ANTITHESIS_OUTPUT_DIR" (.getPath (io/file directory "missing"))})))
      (check! "property with automatic source location passes inside Antithesis"
              (:passed? (child "location" {"HEGEL_CONFIG" (.getPath config)
                                           "ANTITHESIS_OUTPUT_DIR" (.getPath output)})))
      (let [entries (mapv #(json/parse-string % true) (str/split-lines (slurp sdk)))
            text (json/generate-string entries)]
        (check! "native SDK output records the frontend's source identity"
                (and (seq entries) (str/includes? text "hegel.upgrade-env-test")
                     (str/includes? text "upgrade_env_test.clj")
                     (str/includes? text "passes properties"))))
      (finally
        ;; Only files this control creates, never an unrelated directory tree.
        (doseq [file [sdk output config directory]] (io/delete-file file true))))))

(defn -main [& [mode]]
  (case mode
    "settings"
    (do
      (h/register-profile! "registered" {:profile "base" :test-cases 9})
      (prn {:settings [(:test-cases (h/resolved-options))
                       (:test-cases (h/resolved-options {:profile "registered"}))
                       (:test-cases (h/resolved-options {:test-cases 7}))
                       (:print-blob? (h/resolved-options))
                       (:seed (h/resolved-options))]
            :run (select-keys (h/run-test! {:database "" :verbosity :quiet}
                                          (fn [_] (h/draw! (g/integer))))
                              [:passed? :seed :valid-test-cases])}))
    "startup"
    (let [calls (atom 0)
          error (try (h/run-test! (assoc opts :test-location
                                        {:file "control.clj" :line 1
                                         :class-name "control" :function "control"})
                                   (fn [_] (swap! calls inc)))
                     nil (catch Throwable error error))]
      (prn {:native-error? (f/error? error) :operation (:operation (ex-data error)) :calls @calls}))
    "location"
    (binding [ct/report (fn [_])]
      (prn (select-keys (hc/with opts [n (g/integer 1 1)] (ct/is (= 1 n))) [:passed?])))
    (run-parent!))
  (System/exit 0))
