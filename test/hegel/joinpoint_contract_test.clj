(ns hegel.joinpoint-contract-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is]]
            [hegel.host :as host]))

(defn- manifest []
  (edn/read-string (host/resource-text "META-INF/jolt/aspects/hegel.edn")))

(deftest published-aspect-manifest-has-the-exact-library-identity-and-schema
  (let [actual (manifest)]
    (is (= 1 (:schema actual)))
    (is (= {:id 'chucklehead-dev/jolt-hegel
            :version "86a70acf7880184707a77069b60b2e8fd4acbbbb"}
           (:library actual)))
    (is (= #{:schema :library :aspects} (set (keys actual))))))

(deftest published-join-points-have-exact-selectors-roles-and-cardinality
  (let [aspects (:aspects (manifest))]
    (is (= [:hegel.core/run-test :hegel.stateful/run]
           (mapv :id aspects)))
    (is (= [{:arity 2 :entry 'hegel.core/run-test!}
            {:arity 1 :entry 'hegel.stateful/run!}]
           (mapv :match aspects)))
    (is (= [:test/property-run :test/state-machine-run]
           (mapv :advice-role aspects)))
    (is (= [{:matches 1} {:matches 1}]
           (mapv :expect aspects)))))
