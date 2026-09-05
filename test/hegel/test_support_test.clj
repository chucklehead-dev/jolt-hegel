(ns hegel.test-support-test
  (:require [clojure.test :refer [deftest is]]
            [hegel.test-support :as support]))

(def fixture [{:id :one :suite :alpha} {:id :two :suite :beta}])

(deftest manifest-controls-fail-closed
  (is (= fixture (support/validate-manifest! fixture fixture)))
  (doseq [mutant [[(first fixture)]
                  [(second fixture) (first fixture)]
                  [(first fixture) (first fixture)]]]
    (is (thrown? clojure.lang.ExceptionInfo
                 (support/validate-manifest! fixture mutant)))))

(deftest invocation-state-is-isolated
  (let [a (support/new-context {:suite :alpha :progress-path nil})
        b (support/new-context {:suite :beta :progress-path nil})]
    (support/check! a "only a fails" false)
    (is (= 1 (support/failure-count a)))
    (is (zero? (support/failure-count b)))
    (is (not (identical? (:failures a) (:failures b))))))
