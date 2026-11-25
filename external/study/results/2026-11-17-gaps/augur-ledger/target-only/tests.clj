(require '[augur.ledger-test] '[clojure.test :as t])
(let [r (t/run-tests 'augur.ledger-test)] (assert (zero? (+ (:fail r) (:error r)))))
