(ns hegel.suites.corpus
  (:require [clojure.test :as t]
            [hegel.corpus-contract-runner :as contracts]
            [hegel.materialize-test]
            [hegel.test-support :as support]))

(defn portable-contract [context]
  (contracts/run-checks!)
  (support/check! context "materialized corpus portable and digest contracts" true))

(defn native-contract [context]
  (let [{:keys [fail error]} (t/run-tests 'hegel.materialize-test)]
    (support/check! context "materialized corpus native generation contracts"
                    (zero? (+ fail error)))))
