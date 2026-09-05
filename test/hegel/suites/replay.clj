(ns hegel.suites.replay
  "Replay-bundle contract scenarios, loaded only when their suite is selected."
  (:require [clojure.test :as t]
            [hegel.portable-data-test]
            [hegel.replay-bundle-codec-test]
            [hegel.replay-bundle-native-test]
            [hegel.replay-bundle-test]
            [hegel.test-support :as support]))

(defn schema-contract [context]
  (let [result (t/run-tests 'hegel.replay-bundle-test)]
    (support/check! context "replay bundle schema contract suite"
                    (zero? (+ (:fail result) (:error result))))))

(defn codec-contract [context]
  (let [result (t/run-tests 'hegel.portable-data-test
                            'hegel.replay-bundle-codec-test)]
    (support/check! context "replay bundle codec contract suite"
                    (zero? (+ (:fail result) (:error result))))))

(defn native-contract [context]
  (let [result (t/run-tests 'hegel.replay-bundle-native-test)]
    (support/check! context "replay bundle native contract suite"
                    (zero? (+ (:fail result) (:error result))))))
