(ns check-report-test
  (:require [check-report :as report]
            [clojure.test :refer [deftest is run-tests]]))

(def valid-report
  {:schema 1
   :weaver "jolt.aspect-ir/v1"
   :control-enabled? false
   :aspects
   [{:id :hegel.core/run-test
     :advice-role :test/property-run
     :match {:entry 'hegel.core/run-test! :arity 2}
     :library {:id 'chucklehead-dev/jolt-hegel
               :version "86a70acf7880184707a77069b60b2e8fd4acbbbb"}
     :resource "META-INF/jolt/aspects/hegel.edn"
     :consumers [{:advice 'fixture.provider/around :contract :args-v1 :ordinal 1
                  :provider 'fixture.provider/aspect-provider :roles :all
                  :selection-ordinal 1}]
     :sites [{:aspect :hegel.core/run-test :entry 'hegel.core/run-test!
              :arity 2 :site-id "property-site"}]}
    {:id :hegel.stateful/run
     :advice-role :test/state-machine-run
     :match {:entry 'hegel.stateful/run! :arity 1}
     :library {:id 'chucklehead-dev/jolt-hegel
               :version "86a70acf7880184707a77069b60b2e8fd4acbbbb"}
     :resource "META-INF/jolt/aspects/hegel.edn"
     :consumers [{:advice 'fixture.provider/around :contract :args-v1 :ordinal 1
                  :provider 'fixture.provider/aspect-provider :roles :all
                  :selection-ordinal 1}]
     :sites [{:aspect :hegel.stateful/run :entry 'hegel.stateful/run!
              :arity 1 :site-id "stateful-site"}]}]})

(deftest accepts-the-exact-report-and-rejects-all-mutants
  (is (nil? (report/check-report! valid-report)))
  (is (nil? (report/check-mutants! valid-report)))
  (is (= 10 (count (report/report-mutants valid-report)))))

(deftest mutant-loop-fails-closed-when-the-validator-is-bypassed
  (let [error (try
                (with-redefs [report/check-report! (fn [_] nil)]
                  (report/check-mutants! valid-report))
                nil
                (catch clojure.lang.ExceptionInfo error error))]
    (is (= "report validator accepted mutant" (ex-message error)))
    (is (= 0 (:mutant (ex-data error))))))

(deftest assertions-are-elided-during-babashka-evaluation
  (is (nil? (binding [*assert* false]
              (eval '(assert false))))))

(let [result (run-tests 'check-report-test)]
  (when (pos? (+ (:fail result) (:error result)))
    (throw (ex-info "join-point report fixture tests failed" result))))
