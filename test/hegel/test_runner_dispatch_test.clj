(ns hegel.test-runner-dispatch-test
  (:require [clojure.string]
            [clojure.test :refer [deftest is]]
            [hegel.scenario-manifest :as scenarios]
            [hegel.test-runner :as runner]
            [hegel.test-support :as support]))

(def pinned-manifest
  [{:id :bootstrap-load-isolation :suite :runner :description "bootstrap lazy Malli load isolation" :entrypoint 'hegel.suites.runner/bootstrap-load-isolation}
   {:id :host-exception-seam :suite :runner :description "cross-host exception seam" :entrypoint 'hegel.suites.runner/host-exception-seam}
   {:id :terminal-timeout :suite :timeout :description "terminal timeout isolation" :entrypoint 'hegel.suites.timeout/terminal-timeout-regression}
   {:id :passing-run :suite :runner :description "passing run" :entrypoint 'hegel.suites.runner/passing-run}
   {:id :shrinking-run :suite :runner :description "shrinking and final replay" :entrypoint 'hegel.suites.runner/shrinking-run}
   {:id :engine-nondeterminism :suite :runner :description "engine nondeterminism" :entrypoint 'hegel.suites.runner/engine-nondeterminism}
   {:id :counting-reporting :suite :runner :description "framework-less counting reporting" :entrypoint 'hegel.suites.runner/counting-reporting}
   {:id :cleanup-version :suite :runner :description "cleanup and ABI version" :entrypoint 'hegel.suites.runner/cleanup-and-version}
   {:id :ffi-nullable :suite :ffi :description "nullable FFI string results" :entrypoint 'hegel.suites.ffi/ffi-nullable-string-results}
   {:id :ffi-adapter :suite :ffi :description "upstream babashka.ffi adapter" :entrypoint 'hegel.suites.ffi/upstream-babashka-ffi-adapter}
   {:id :ffi-write-order :suite :ffi :description "Jolt 0.8 FFI write order" :entrypoint 'hegel.suites.ffi/jolt-ffi-write-order-contract}
   {:id :installer-source :suite :install :description "installer source identity" :entrypoint 'hegel.suites.install/installer-source-identity}
   {:id :portable-paths :suite :install :description "portable path contracts" :entrypoint 'hegel.suites.install/portable-path-contracts}
   {:id :installer-checksum :suite :install :description "installer checksum contract" :entrypoint 'hegel.suites.install/installer-checksum-contract}
   {:id :installer-publication :suite :install :description "installer publication ownership" :entrypoint 'hegel.suites.install/installer-publication-contract}
   {:id :validation :suite :validation :description "public option validation" :entrypoint 'hegel.suites.validation/public-option-validation}
   {:id :generated-seed :suite :runner :description "generated seed" :entrypoint 'hegel.suites.runner/generated-seed}
   {:id :controls-sample :suite :runner :description "controls and sample" :entrypoint 'hegel.suites.runner/controls-and-sample}
   {:id :primitive-generators :suite :generators :description "primitive generators" :entrypoint 'hegel.suites.generators/primitive-generators}
   {:id :temporal-generators :suite :generators :description "temporal generators through direct aggregate bindings" :entrypoint 'hegel.suites.generators/temporal-generators}
   {:id :harness-integrity :suite :runner :description "harness and replay integrity" :entrypoint 'hegel.suites.runner/harness-integrity}
   {:id :string-generators :suite :generators :description "string and format generators" :entrypoint 'hegel.suites.generators/string-generators}
   {:id :collection-generators :suite :generators :description "collection and composition generators" :entrypoint 'hegel.suites.generators/collection-combinators}
   {:id :combinator-shrinking :suite :generators :description "cross-binding combinator shrink quality" :entrypoint 'hegel.suites.generators/combinator-shrink-quality}
   {:id :recursive-retry :suite :generators :description "recursive generator retry protocol" :entrypoint 'hegel.suites.generators/recursive-retry-protocol}
   {:id :recursive-generators :suite :generators :description "recursive generator bounds and shrinking" :entrypoint 'hegel.suites.generators/recursive-generators}
   {:id :malli-construction :suite :malli :description "Malli adapter construction" :entrypoint 'hegel.suites.malli/malli-adapter-construction}
   {:id :malli-generation :suite :malli :description "Malli adapter generation and shrinking" :entrypoint 'hegel.suites.malli/malli-adapter-generation}
   {:id :stateful-pools :suite :stateful :description "stateful pools and model tests" :entrypoint 'hegel.suites.stateful/stateful-pools-and-models}
   {:id :stateful-shrinking :suite :stateful :description "stateful shrink quality" :entrypoint 'hegel.suites.stateful/stateful-shrink-quality}
   {:id :stateful-swarm :suite :stateful :description "stateful swarm and control flow" :entrypoint 'hegel.suites.stateful/stateful-swarm-and-control-flow}
   {:id :stateful-abi :suite :stateful :description "latest stateful ABI and owned handles" :entrypoint 'hegel.suites.stateful/latest-stateful-abi}
   {:id :trace-rules :suite :trace-history :description "bounded semantic trace rules" :entrypoint 'hegel.suites.trace-history/semantic-trace-rules}
   {:id :linearizability :suite :trace-history :description "bounded linearizability" :entrypoint 'hegel.suites.trace-history/bounded-linearizability}
   {:id :history-budget :suite :trace-history :description "bounded linearizability search budget" :entrypoint 'hegel.suites.trace-history/history-budget-contract}
   {:id :history-oracle :suite :trace-history :description "independent exhaustive history oracle" :entrypoint 'hegel.suites.trace-history/exhaustive-history-oracle}
   {:id :clojure-test :suite :clojure-test :description "clojure.test integration" :entrypoint 'hegel.suites.clojure-test/clojure-test-integration}])

(defn pass! [_] nil)
(defn fail! [_] (throw (ex-info "expected scenario failure" {})))
(defn bootstrap-probe! [context]
  (support/check! context "pre-existing optional consumer state is preserved"
                  (= (:optional-namespaces context)
                     {'malli.core (boolean (find-ns 'malli.core))
                      'hegel.malli (boolean (find-ns 'hegel.malli))})))

(def fixture
  [{:id :pass :suite :alpha :description "pass"
    :entrypoint 'hegel.test-runner-dispatch-test/pass!}
   {:id :fail :suite :beta :description "fail"
    :entrypoint 'hegel.test-runner-dispatch-test/fail!}])

(defn- dispatch [args]
  (runner/run-dispatch! args {:fixture fixture :manifest fixture
                               :progress-path nil :exit! (fn [_])}))

(deftest independently-pinned-manifest-rejects-semantic-mutations
  (is (= pinned-manifest scenarios/fixture scenarios/manifest))
  (doseq [mutant [(vec (butlast pinned-manifest))
                  (vec (reverse pinned-manifest))
                  (conj (vec (butlast pinned-manifest)) (first pinned-manifest))
                  (assoc-in pinned-manifest [0 :entrypoint]
                            'hegel.suites.runner/passing-run)]]
    (is (thrown? clojure.lang.ExceptionInfo
                 (support/validate-manifest! pinned-manifest mutant)))))

(deftest manifest-dispatch-is-selected-and-invocation-isolated
  (is (= [:alpha :beta] (:suites (dispatch ["--list-suites"]))))
  (let [first-run (dispatch ["--suite" "alpha"])
        failed-run (dispatch ["--suite" "beta"])
        second-run (dispatch ["--suite" "alpha"])]
    (is (zero? (:status first-run)))
    (is (= 1 (:status failed-run)))
    (is (= 1 (support/failure-count (:context failed-run))))
    (is (zero? (:status second-run)))
    (is (zero? (support/failure-count (:context second-run))))))

(deftest malformed-dispatch-never-runs-the-aggregate
  (doseq [args [["--suite"] ["--suite" "missing" "extra"] ["--unknown"]]]
    (is (= 2 (:status (dispatch args))))))

(deftest unknown-suite-is-an-argument-error
  (is (= 2 (:status (dispatch ["--suite" "missing"])))))

(deftest harness-setup-failure-is-not-an-argument-error
  (with-redefs [support/reset-progress! (fn [_] (throw (ex-info "setup failed" {})))]
    (is (= 1 (:status (dispatch ["--suite" "alpha"]))))))

(deftest runner-suite-accepts-a-prior-optional-consumer-load
  (let [created (atom [])
        ensure! (fn [namespace]
                  (when-not (find-ns namespace)
                    (create-ns namespace)
                    (swap! created conj namespace)))
        runner-only [{:id :bootstrap :suite :runner :description "bootstrap"
                      :entrypoint 'hegel.test-runner-dispatch-test/bootstrap-probe!}]]
    (try
      (ensure! 'malli.core)
      (ensure! 'hegel.malli)
      (is (zero? (:status
                   (runner/run-dispatch!
                    ["--suite" "runner"]
                    {:fixture runner-only :manifest runner-only
                     :progress-path nil :exit! (fn [_])}))))
      (finally
        (doseq [namespace @created]
          (remove-ns namespace))))))

(deftest timeout-exit-survives-reporting-failure
  (let [context (support/new-context {:progress-path nil :timeout-ms 1
                                      :await-result! (fn [_ _] ::support/timeout)})
        exits (atom [])]
    (with-redefs [support/progress! (fn [_ message]
                                      (when (clojure.string/starts-with? message "TIMEOUT")
                                        (throw (ex-info "reporting failed" {}))))]
      (try
        (support/scenario! context "bounded timeout"
                           (fn [] nil)
                           #(swap! exits conj %))
        (catch Throwable _)))
    (is (= [1] @exits))))
