(require (quote [clojure.test :as t]) (quote instaparse.core-test) (quote instaparse.repeat-test))
(let [r (t/run-tests (quote instaparse.core-test) (quote instaparse.repeat-test))]
 (assert (zero? (+ (:fail r) (:error r)))))
