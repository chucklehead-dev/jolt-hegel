(ns hegel.header-units-test
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [hegel.header-snapshot :as header]
            [hegel.header-units :as units]))

(deftest temporal-units-are-not-just-layout-widths
  (let [raw (header/read-utf8 (str header/fixture-dir "/hegel.h"))
        descriptor (edn/read-string (slurp "resources/hegel/abi.edn"))]
    (is (= {:unit :nanosecond :range [0 999999999]} (units/check! raw descriptor)))
    (doseq [[header data]
            [[raw (assoc-in descriptor [:types :hegel/time :fields 3 :unit] :microsecond)]
             [raw (assoc-in descriptor [:types :hegel/time :fields 3 :range] [0 999999])]
             [(str/replace raw "nanosecond" "microsecond") descriptor]
             [(str/replace raw "[0, 999999999]" "[0, 999999]") descriptor]]]
      (is (thrown? clojure.lang.ExceptionInfo (units/check! header data))))))
