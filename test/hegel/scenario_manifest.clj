(ns hegel.scenario-manifest)

;; This fixture is deliberately ordered. Do not derive it from namespace load
;; order: ids are the stable review surface for aggregate coverage.
(def fixture
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
   {:id :temporal-precision :suite :generators :description "temporal precision compatibility" :entrypoint 'hegel.suites.generators/temporal-precision-contract}
   {:id :generator-cleanup :suite :generators :description "generator cleanup exception precedence" :entrypoint 'hegel.suites.generators/generator-cleanup-contract}
   {:id :sequence-generators :suite :generators :description "permutations subsequences and samples" :entrypoint 'hegel.suites.generators/sequence-generators-contract}
   {:id :generated-seed :suite :runner :description "generated seed" :entrypoint 'hegel.suites.runner/generated-seed}
   {:id :controls-sample :suite :runner :description "controls and sample" :entrypoint 'hegel.suites.runner/controls-and-sample}
   {:id :observations :suite :observations :description "observations and explicit coverage" :entrypoint 'hegel.suites.observations/observation-contract}
   {:id :primitive-generators :suite :generators :description "primitive generators" :entrypoint 'hegel.suites.generators/primitive-generators}
   {:id :temporal-generators :suite :generators :description "temporal generators through direct aggregate bindings" :entrypoint 'hegel.suites.generators/temporal-generators}
   {:id :harness-integrity :suite :runner :description "harness and replay integrity" :entrypoint 'hegel.suites.runner/harness-integrity}
   {:id :replay-bundle-schema :suite :replay :description "replay bundle schema" :entrypoint 'hegel.suites.replay/schema-contract}
   {:id :replay-bundle-codec :suite :replay :description "replay bundle codec" :entrypoint 'hegel.suites.replay/codec-contract}
   {:id :replay-bundle-native :suite :replay :description "replay bundle native integration" :entrypoint 'hegel.suites.replay/native-contract}
   {:id :corpus-portable :suite :corpus :description "materialized corpus portable contracts" :entrypoint 'hegel.suites.corpus/portable-contract}
   {:id :corpus-native :suite :corpus :description "materialized corpus native generation" :entrypoint 'hegel.suites.corpus/native-contract}
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
   {:id :libhegel-upgrade :suite :stateful :description "libhegel upgrade and versioned replay contracts" :entrypoint 'hegel.suites.stateful/libhegel-upgrade-contract}
   {:id :trace-rules :suite :trace-history :description "bounded semantic trace rules" :entrypoint 'hegel.suites.trace-history/semantic-trace-rules}
   {:id :linearizability :suite :trace-history :description "bounded linearizability" :entrypoint 'hegel.suites.trace-history/bounded-linearizability}
   {:id :history-budget :suite :trace-history :description "bounded linearizability search budget" :entrypoint 'hegel.suites.trace-history/history-budget-contract}
   {:id :event-contract :suite :trace-history :description "trace/history event domain characterization" :entrypoint 'hegel.suites.trace-history/event-contract-characterization}
   {:id :history-oracle :suite :trace-history :description "independent exhaustive history oracle" :entrypoint 'hegel.suites.trace-history/exhaustive-history-oracle}
   {:id :clojure-test :suite :clojure-test :description "clojure.test integration" :entrypoint 'hegel.suites.clojure-test/clojure-test-integration}])

(def manifest fixture)
