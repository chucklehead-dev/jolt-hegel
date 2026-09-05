(ns hegel.header-layout-check-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]
            [hegel.header-layout :as compiler]
            [hegel.header-layout-check :as check]
            [hegel.header-snapshot :as header]))

(deftest compiler-versus-canonical-layout
  (let [snapshot (header/snapshot)
        descriptor (edn/read-string (slurp "resources/hegel/abi.edn"))
        measurement (compiler/measure! snapshot)]
    (is (= {:scalars 12 :structs 6 :enums 2}
           (check/check! snapshot descriptor measurement)))
    (doseq [[label mutant]
            [["scalar width" (assoc-in descriptor [:types :c/uint32 :bits] 64)]
             ["scalar signedness" (assoc-in descriptor [:types :c/uint32 :signed?] true)]
             ["same-sized field order"
              (update-in descriptor [:types :hegel/date :fields]
                         #(assoc % 1 (nth % 2) 2 (nth % 1)))]
             ["nested aggregate order"
              (update-in descriptor [:types :hegel/datetime :fields] #(vec (reverse %)))]]]
      (testing label
        (is (thrown? clojure.lang.ExceptionInfo
                     (check/check! snapshot mutant measurement)))))
    (is (thrown? clojure.lang.ExceptionInfo
                 (check/check! snapshot descriptor
                               (assoc-in measurement [:enums :hegel_label_t :HEGEL_LABEL_LIST] 2))))
    (is (thrown? clojure.lang.ExceptionInfo
                 (check/check! snapshot descriptor
                               (assoc-in measurement [:structs :hegel_time_t :align] 8))))
    (is (thrown? clojure.lang.ExceptionInfo
                 (check/check! snapshot descriptor
                               (assoc-in measurement [:enum-layouts :hegel_result_t :size] 8))))))
