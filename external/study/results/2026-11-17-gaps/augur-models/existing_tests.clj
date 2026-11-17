(ns existing-tests (:require [clojure.test :as t] [augur.calibrate-test] [augur.beta-test] [augur.gamma-test]))
(defn -main [& _]
  (let [r (t/run-tests 'augur.calibrate-test 'augur.beta-test 'augur.gamma-test)]
    (prn r) (assert (zero? (+ (:fail r) (:error r))))))
