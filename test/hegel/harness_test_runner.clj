(ns hegel.harness-test-runner
  "Dedicated, repeatable contract-harness controls.  It is intentionally
  separate from the scenario aggregate so suite ordering remains stable."
  (:require [clojure.test :as test]
            [hegel.resource-test]
            [hegel.test-runner-dispatch-test]
            [hegel.test-support-test]))

(defn -main [& _]
  (let [result (test/run-tests 'hegel.test-runner-dispatch-test
                               'hegel.test-support-test
                               'hegel.resource-test)]
    (System/exit (if (zero? (+ (:fail result) (:error result))) 0 1))))
