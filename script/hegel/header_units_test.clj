(ns hegel.header-units-test
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [hegel.header-units :as units]))

(deftest temporal-units-are-not-just-layout-widths
  (let [raw (slurp "test/fixtures/hegel-0.33.3/hegel.h")
        descriptor (edn/read-string (slurp "resources/hegel/abi.edn"))]
    (is (= {:unit :microsecond :range [0 999999]} (units/check! raw descriptor)))
    (doseq [[header data]
            [[raw (assoc-in descriptor [:types :hegel/time :fields 3 :unit] :nanosecond)]
             [raw (assoc-in descriptor [:types :hegel/time :fields 3 :range] [0 999999999])]
             [(str/replace raw "microsecond" "nanosecond") descriptor]
             [(str/replace raw "[0, 999999]" "[0, 999999999]") descriptor]]]
      (is (thrown? clojure.lang.ExceptionInfo (units/check! header data))))))
