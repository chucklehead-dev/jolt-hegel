(ns hegel.header-audit
  "Offline header/descriptor audit plus one compiler-target layout probe."
  (:require [clojure.edn :as edn]
            [clojure.test :as test]
            [hegel.header-snapshot :as header]
            [hegel.header-check :as signatures]
            [hegel.header-constants :as constants]
            [hegel.header-layout :as compiler]
            [hegel.header-layout-check :as layouts]
            [hegel.header-units :as units]
            [hegel.header-snapshot-test]
            [hegel.header-check-test]
            [hegel.header-constants-test]
            [hegel.header-layout-test]
            [hegel.header-layout-check-test]
            [hegel.header-units-test]))

(defn -main [& _]
  (let [snapshot (header/snapshot)
        descriptor (edn/read-string (slurp "resources/hegel/abi.edn"))]
    (prn {:signatures (signatures/check! snapshot descriptor)
          :constants (constants/compare-constants snapshot (constants/source-constants))
          :layouts (layouts/check! snapshot descriptor (compiler/measure! snapshot))
          :units (units/check! (header/read-utf8 (str header/fixture-dir "/hegel.h")) descriptor)})
    (let [result (test/run-tests 'hegel.header-snapshot-test 'hegel.header-check-test
                                 'hegel.header-constants-test 'hegel.header-layout-test
                                 'hegel.header-layout-check-test 'hegel.header-units-test)]
      (when-not (zero? (+ (:fail result) (:error result)))
        (throw (ex-info "Header audit regression controls failed" result))))))
