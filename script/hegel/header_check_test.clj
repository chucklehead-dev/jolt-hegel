(ns hegel.header-check-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]
            [hegel.header-check :as check]
            [hegel.header-snapshot :as header]))

(defn descriptor [] (edn/read-string (slurp "resources/hegel/abi.edn")))

(deftest exact-header-contract
  (is (= {:functions 103 :structs 6 :opaque-handles 13 :callbacks 1}
         (check/check! (header/snapshot) (descriptor)))))

(deftest independent-signature-and-field-mutations
  (let [snapshot (header/snapshot)
        original (descriptor)]
    (doseq [[label mutant]
            [["missing function" (update original :functions dissoc :context-new)]
             ["same-count symbol replacement"
              (assoc-in original [:functions :context-new :symbol] "hegel_missing")]
             ["signedness"
              (assoc-in original [:functions :settings-set-seed :args 2] :c/int64)]
             ["argument order"
              (update-in original [:functions :settings-set-seed :args]
                         #(assoc % 2 (nth % 3) 3 (nth % 2)))]
             ["pointer depth"
              (assoc-in original [:functions :settings-new :args 1]
                        [:pointer :hegel/settings])]
             ["by-value versus pointer"
              (assoc-in original [:functions :generate-date :args 2]
                        [:pointer :hegel/date])]
             ["same-size field order"
              (update-in original [:types :hegel/date :fields]
                         #(assoc % 1 (nth % 2) 2 (nth % 1)))]
             ["callback return"
              (assoc-in original [:types :hegel/output-callback :return] :c/int32)]
             ["callback argument"
              (assoc-in original [:types :hegel/output-callback :args 2] :c/uint32)]
             ["omitted opaque handle"
              (update original :types dissoc :hegel/printer)]
             ["new printer result pointer"
              (assoc-in original [:functions :printer-value :args 2]
                        [:pointer :hegel/string-result])]]]
      (testing label
        (let [failure (try (check/check! snapshot mutant) nil
                           (catch clojure.lang.ExceptionInfo e (ex-data e)))]
          (is (seq (:path failure)))
          (is (not= (:header failure) (:descriptor failure))))))
    ;; Adjacent valid control: ownership metadata is intentionally not guessed
    ;; from C signatures and does not alter this signature comparison.
    (is (= 103 (:functions (check/check! snapshot
                                        (assoc-in original
                                                  [:functions :context-new :review-note]
                                                  "non-ABI metadata")))))))

(deftest printer-ownership-is-explicit
  (let [functions (:functions (descriptor))]
    (is (= {:out {:kind :owned :release :printer-options-free}}
           (:ownership (:printer-options-new functions))))
    (doseq [name [:printer-new :printer-deferred :test-case-printer]]
      (is (= {:out {:kind :owned :release :printer-free}}
             (:ownership (get functions name))) name))
    (is (= {:out {:kind :owned :release :printer-value-result-free}}
           (:ownership (:printer-value functions))))))
