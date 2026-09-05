(ns hegel.suites.validation
  "Public option-validation contract, loaded only when selected."
  (:require [clojure.test :as t]
            [hegel.test-support :as support]
            [hegel.validation-test]))

(defn public-option-validation [context]
  (let [result (t/run-tests 'hegel.validation-test)]
    (support/check! context "public validation contract suite"
                    (zero? (+ (:fail result) (:error result))))))
