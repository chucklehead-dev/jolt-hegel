(ns hegel.header-constants
  "Compare source literal constants with the offline pinned-header snapshot.

   This namespace reads source forms as data. It never requires or evaluates
   hegel.ffi/hegel.core, so native backends and host conditionals are absent
   from this audit."
  (:require [clojure.java.io :as io]
            [hegel.header-snapshot :as header]))

(def fixture-dir "test/fixtures/hegel-0.33.3")
(def source-root "src")

(def ffi-enum-map
  {:status-valid ["hegel_status_t" "HEGEL_STATUS_VALID"]
   :status-invalid ["hegel_status_t" "HEGEL_STATUS_INVALID"]
   :status-overrun ["hegel_status_t" "HEGEL_STATUS_OVERRUN"]
   :status-interesting ["hegel_status_t" "HEGEL_STATUS_INTERESTING"]
   :run-status-passed ["hegel_run_status_t" "HEGEL_RUN_STATUS_PASSED"]
   :run-status-failed ["hegel_run_status_t" "HEGEL_RUN_STATUS_FAILED"]
   :run-status-error ["hegel_run_status_t" "HEGEL_RUN_STATUS_ERROR"]
   :run-status-failed-nondeterministic ["hegel_run_status_t" "HEGEL_RUN_STATUS_FAILED_NONDETERMINISTIC"]
   :label-list ["hegel_label_t" "HEGEL_LABEL_LIST"]
   :label-set ["hegel_label_t" "HEGEL_LABEL_SET"]
   :label-map ["hegel_label_t" "HEGEL_LABEL_MAP"]
   :label-tuple ["hegel_label_t" "HEGEL_LABEL_TUPLE"]
   :label-one-of ["hegel_label_t" "HEGEL_LABEL_ONE_OF"]
   :label-optional ["hegel_label_t" "HEGEL_LABEL_OPTIONAL"]
   :label-flat-map ["hegel_label_t" "HEGEL_LABEL_FLAT_MAP"]
   :label-filter ["hegel_label_t" "HEGEL_LABEL_FILTER"]
   :label-mapped ["hegel_label_t" "HEGEL_LABEL_MAPPED"]
   :label-recursive ["hegel_label_t" "HEGEL_LABEL_RECURSIVE"]})

(def core-enum-maps
  {:mode-values {"test-run" ["hegel_mode_t" "HEGEL_MODE_TEST_RUN"]
                 "single-test-case" ["hegel_mode_t" "HEGEL_MODE_SINGLE_TEST_CASE"]}
   :backend-values {"auto" ["hegel_backend_t" "HEGEL_BACKEND_AUTO"]
                    "default" ["hegel_backend_t" "HEGEL_BACKEND_DEFAULT"]
                    "urandom" ["hegel_backend_t" "HEGEL_BACKEND_URANDOM"]}
   :verbosity-values {"quiet" ["hegel_verbosity_t" "HEGEL_VERBOSITY_QUIET"]
                      "normal" ["hegel_verbosity_t" "HEGEL_VERBOSITY_NORMAL"]
                      "verbose" ["hegel_verbosity_t" "HEGEL_VERBOSITY_VERBOSE"]
                      "debug" ["hegel_verbosity_t" "HEGEL_VERBOSITY_DEBUG"]}
   :phase-values {"explicit" ["hegel_phase_t" "HEGEL_PHASE_EXPLICIT"]
                  "reuse" ["hegel_phase_t" "HEGEL_PHASE_REUSE"]
                  "generate" ["hegel_phase_t" "HEGEL_PHASE_GENERATE"]
                  "target" ["hegel_phase_t" "HEGEL_PHASE_TARGET"]
                  "shrink" ["hegel_phase_t" "HEGEL_PHASE_SHRINK"]}
   :health-check-values {"filter-too-much" ["hegel_health_check_t" "HEGEL_HC_FILTER_TOO_MUCH"]
                         "too-slow" ["hegel_health_check_t" "HEGEL_HC_TOO_SLOW"]
                         "test-cases-too-large" ["hegel_health_check_t" "HEGEL_HC_TEST_CASES_TOO_LARGE"]
                         "large-initial-test-case" ["hegel_health_check_t" "HEGEL_HC_LARGE_INITIAL_TEST_CASE"]}})

(defn read-forms [path]
  (with-open [reader (java.io.PushbackReader. (io/reader path))]
    ;; `ffi.cljc` contains ::stop-test and ::assumption-rejected. Resolve
    ;; those reader keywords against the source namespace, never the caller's
    ;; dynamic namespace; this remains data-only and does not require ffi.
    (binding [*ns* (or (find-ns 'hegel.ffi) (create-ns 'hegel.ffi))
              *read-eval* false]
      (loop [forms []]
        (let [form (read {:eof ::eof :read-cond :allow :features #{:clj}} reader)]
          (if (= ::eof form) forms (recur (conj forms form))))))))

(defn def-map [forms name]
  (let [matches (filter #(and (seq? %) (= 'def (first %)) (= name (second %))) forms)]
    (when-not (= 1 (count matches))
      (throw (ex-info "expected exactly one source definition" {:name name :count (count matches)})))
    (let [form (nth matches 0)
          value (nth form 2)]
      (if (string? value) (nth form 3) value))))

(defn def-literals [forms names]
  (into {}
        (map (fn [name]
               [name (let [value (def-map forms (symbol name))]
                       (when-not (or (number? value) (map? value))
                         (throw (ex-info "source definition is not a literal" {:name name :value value})))
                       value)]))
             names))

(defn- function-form [forms name]
  (let [matches (filter #(and (seq? %) (contains? #{'defn 'defn-} (first %))
                              (= name (second %))) forms)]
    (when-not (= 1 (count matches))
      (throw (ex-info "expected exactly one source function" {:name name :count (count matches)})))
    (first matches)))

(defn- function-body [form name]
  (let [[_ _ & tail] form
        tail (if (string? (first tail)) (next tail) tail)
        [args & body] tail]
    (when-not (and (vector? args) (= 1 (count body)))
      (throw (ex-info "unsupported source function shape" {:name name :form form})))
    (first body)))

(defn- case-branches [form name]
  (let [[op selector & clauses] form]
    (when-not (and (= 'case op) (= 'rc selector) (odd? (count clauses)))
      (throw (ex-info "unsupported result-code case shape" {:name name :form form})))
    {:entries (into {} (map vec (partition 2 (butlast clauses))))
     :default (last clauses)}))

(defn- throw-type [branch name]
  (let [[throw-op ex-info-form] branch
        [_ex-info _message data] ex-info-form]
    (when-not (and (= 'throw throw-op)
                   (seq? ex-info-form)
                   (= 'ex-info _ex-info)
                   (map? data)
                   (= 2 (count branch)))
      (throw (ex-info "unsupported result-code error branch" {:name name :branch branch})))
    (let [type (:type data)]
      (when-not (keyword? type)
        (throw (ex-info "result-code branch has no literal error type" {:name name :branch branch})))
      type)))

(defn- result-code-case [forms]
  (let [name 'check-draw!
        body (function-body (function-form forms name) name)
        {:keys [entries default]} (case-branches body name)]
    (when-not (and (= #{0 -1 -2} (set (keys entries)))
                   (= default '(check! ctx operation rc)))
      (throw (ex-info "check-draw! result-code cases changed" {:entries (keys entries) :default default})))
    (when-not (= :hegel.ffi/stop-test (throw-type (get entries -1) name))
      (throw (ex-info "HEGEL_E_STOP_TEST branch changed" {})))
    (when-not (= :hegel.ffi/assumption-rejected (throw-type (get entries -2) name))
      (throw (ex-info "HEGEL_E_ASSUME branch changed" {})))
    {"HEGEL_OK" 0 "HEGEL_E_STOP_TEST" -1 "HEGEL_E_ASSUME" -2}))

(defn- recursion-code [forms]
  (let [name 'check-recursion-control!
        form (function-body (function-form forms name) name)
        [if-op test then else] form]
    (when-not (and (= 'if if-op)
                   (= :retry then)
                   (seq? test) (= '= (first test))
                   (= -10 (second test)) (= 'rc (nth test 2))
                   (seq? else) (= 'do (first else)))
      (throw (ex-info "check-recursion-control! result-code shape changed" {:form form})))
    {"HEGEL_E_RETRY" -10}))

(defn result-codes
  "Extract literal result-code branches without evaluating ffi.cljc."
  [ffi-forms]
  (merge (result-code-case ffi-forms) (recursion-code ffi-forms)))

(defn source-constants []
  (let [ffi (read-forms (str source-root "/hegel/ffi.cljc"))
        core (read-forms (str source-root "/hegel/core.cljc"))]
    {:ffi (merge (def-literals ffi (keys ffi-enum-map))
                 {:state-machine-done (def-map ffi 'state-machine-done)
                  :no-max-size (def-map ffi 'no-max-size)
                  :result-codes (result-codes ffi)})
     :core (into {}
                 (map (fn [name] [name (def-map core (symbol name))])
                      (keys core-enum-maps)))}))

(defn enum-values [snapshot enum-name]
  (into {} (get-in snapshot [:enums enum-name])))

(defn require-value! [source-name value expected]
  (when-not (= value expected)
    (throw (ex-info "ABI constant mismatch"
                    {:source source-name :actual value :expected expected}))))
(defn symbolic-value [value]
  (case value
    "INT64_MIN" -9223372036854775808N
    "UINT64_MAX" 18446744073709551615N
    value))

(defn compare-constants
  "Compare source literals against a parsed header snapshot. Returns summary
   data or throws on missing mapped declarations, missing runtime defs, or a
   changed value. The expected numeric values are never duplicated here."
  [snapshot {:keys [ffi core]}]
  (let [lookup (fn [[enum-name constant-name]]
                 (let [values (enum-values snapshot enum-name)]
                   (when-not (contains? values constant-name)
                     (throw (ex-info "mapped header enum constant is missing"
                                     {:enum enum-name :constant constant-name})))
                   (get values constant-name)))
        compare-map (fn [source mapping]
                      (doseq [[source-name header-ref] mapping]
                        (when-not (contains? source source-name)
                          (throw (ex-info "runtime constant definition is missing" {:source source-name})))
                        (require-value! source-name (get source source-name) (lookup header-ref))))]
    (compare-map ffi ffi-enum-map)
    (doseq [[map-name mapping] core-enum-maps]
      (let [source-map (get core map-name)]
        (when-not (map? source-map)
          (throw (ex-info "runtime enum map definition is missing" {:source map-name})))
        (when-not (= (set (keys source-map)) (set (map keyword (keys mapping))))
          (throw (ex-info "runtime enum map has unsupported or missing keys"
                          {:source map-name
                           :actual (set (keys source-map))
                           :expected (set (map keyword (keys mapping)))})))
        (doseq [[source-name header-ref] mapping]
          (when-not (contains? source-map (keyword source-name))
            (throw (ex-info "runtime enum map key is missing" {:source map-name :key source-name})))
          (require-value! (str map-name "/" source-name)
                          (get source-map (keyword source-name))
                          (lookup header-ref)))))
    (when-not (= #{"HEGEL_OK" "HEGEL_E_STOP_TEST" "HEGEL_E_ASSUME" "HEGEL_E_RETRY"}
                 (set (keys (:result-codes ffi))))
      (throw (ex-info "runtime result-code mapping has unsupported or missing keys"
                      {:actual (set (keys (:result-codes ffi)))})))
    (doseq [[constant-name value] (:result-codes ffi)]
      (require-value! (str "result-code/" constant-name)
                      value
                      (lookup ["hegel_result_t" constant-name])))
    (require-value! "state-machine-done" (:state-machine-done ffi)
                    (symbolic-value (get-in snapshot [:defines "HEGEL_STATE_MACHINE_DONE"])))
    (require-value! "no-max-size" (:no-max-size ffi) 18446744073709551615N)
    {:status :pass :enum-count (+ (count ffi-enum-map) (reduce + (map count (vals core-enum-maps))))
     :define-count 2}))

(defn snapshot []
  (header/snapshot))

(defn -main [& _]
  (println (pr-str (compare-constants (snapshot) (source-constants)))))
