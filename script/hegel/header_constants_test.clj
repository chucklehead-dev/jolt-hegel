(ns hegel.header-constants-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.walk :as walk]
            [hegel.header-constants :as constants]))

(defn replace-enum [snapshot enum-name constant-name value]
  (update-in snapshot [:enums enum-name]
             (fn [entries]
               (mapv (fn [[name actual]]
                       [name (if (= name constant-name) value actual)]) entries))))

(deftest pinned-source-constants-pass
  (is (= :pass (:status (constants/compare-constants
                         (constants/snapshot)
                         (constants/source-constants))))))

(deftest removed-mode-enum-is-not-accepted-as-a-runtime-contract
  (let [source (constants/source-constants)]
    (is (not (contains? (:core source) :mode-values)))
    (is (not (contains? (:enums (constants/snapshot)) "hegel_mode_t")))))

(deftest source-reading-is-independent-of-caller-namespace
  (let [a (binding [*ns* (create-ns 'hegel.constants-test-a)]
            (constants/source-constants))
        b (binding [*ns* (create-ns 'hegel.constants-test-b)]
            (constants/source-constants))]
    (is (= (:result-codes (:ffi a)) (:result-codes (:ffi b))))
    (is (= :pass (:status (constants/compare-constants (constants/snapshot) a))))
    (is (= :pass (:status (constants/compare-constants (constants/snapshot) b))))))

(deftest same-count-label-mutant-fails
  (let [snapshot (-> (constants/snapshot)
                     (replace-enum "hegel_label_t" "HEGEL_LABEL_LIST" 2))]
    (is (thrown? clojure.lang.ExceptionInfo
                 (constants/compare-constants snapshot (constants/source-constants))))))

(deftest phase-flag-mutant-fails
  (let [snapshot (-> (constants/snapshot)
                     (replace-enum "hegel_phase_t" "HEGEL_PHASE_GENERATE" 8))]
    (is (thrown? clojure.lang.ExceptionInfo
                 (constants/compare-constants snapshot (constants/source-constants))))))

(deftest missing-mapping-fails
  (let [snapshot (update-in (constants/snapshot) [:enums "hegel_label_t"]
                            (fn [entries] (remove #(= "HEGEL_LABEL_LIST" (first %)) entries)))]
    (is (thrown? clojure.lang.ExceptionInfo
                 (constants/compare-constants snapshot (constants/source-constants))))))

(deftest result-code-branches-pass
  (let [source (constants/source-constants)]
    (is (= {"HEGEL_OK" 0
            "HEGEL_E_STOP_TEST" -1
            "HEGEL_E_ASSUME" -2
            "HEGEL_E_RETRY" -10}
           (get-in source [:ffi :result-codes])))))

(defn replace-function-literal [forms function-name old new]
  (mapv (fn [form]
          (if (and (seq? form)
                   (contains? #{'defn 'defn-} (first form))
                   (= function-name (second form)))
            (walk/postwalk #(if (= old %) new %) form)
            form))
        forms))

(deftest changed-result-code-branch-fails-closed
  (let [forms (constants/read-forms "src/hegel/ffi.cljc")
        mutant (replace-function-literal forms 'check-draw! -1 -3)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (constants/result-codes mutant)))))

(deftest changed-recursion-code-branch-fails-closed
  (let [forms (constants/read-forms "src/hegel/ffi.cljc")
        mutant (replace-function-literal forms 'check-recursion-control! -10 -11)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (constants/result-codes mutant)))))
