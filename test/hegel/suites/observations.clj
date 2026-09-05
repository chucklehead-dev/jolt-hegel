(ns hegel.suites.observations
  (:require [clojure.test :as t]
            [hegel.observation-ffi-test]
            [hegel.observation-reducer-test]
            [hegel.observation-test]
            [hegel.test-support :as support]))

(defn observation-contract [context]
  (let [{:keys [fail error]}
        (t/run-tests 'hegel.observation-ffi-test
                     'hegel.observation-reducer-test
                     'hegel.observation-test)]
    (support/check! context "observations and explicit coverage contracts"
                    (zero? (+ fail error)))))
