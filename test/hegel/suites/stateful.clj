(ns hegel.suites.stateful
  "Stateful contract scenarios, loaded only when their suite is selected."
  (:require [clojure.test :as t]
            [hegel.core :as h]
            [hegel.ffi :as hffi]
            [hegel.generator :as g]
            [hegel.libhegel-upgrade-test]
            [hegel.stateful.concurrent :as concurrent]
            [hegel.stateful :as hs]
            [hegel.test-support :as support]))

(defn stateful-pools-and-models [context]
  (let [observations (atom [])
        result
        (h/run-test!
         {:test-cases 40
          :seed 20260728
          :database ""
          :verbosity :quiet}
         (fn [_]
           (let [elements
                 (h/draw!
                  (g/vector {:min-size 1 :max-size 8 :unique? true}
                            (g/integer 0 50)))
                 pool (hs/pool)]
             (doseq [value elements]
               (hs/add! pool value))
             (let [original (set elements)
                   reusable (h/draw! (hs/values-reusable pool))
                   size-before (hs/pool-size pool)
                   consumed
                   (loop [remaining (count elements)
                          values []]
                     (if (zero? remaining)
                       values
                       (recur (dec remaining)
                              (conj values
                                    (h/draw! (hs/values-consumed pool))))))
                   special-pool (hs/pool)
                   _ (do
                       (hs/add! special-pool nil)
                       (hs/add! special-pool false))
                   special-values
                   (set [(h/draw! (hs/values-consumed special-pool))
                         (h/draw! (hs/values-consumed special-pool))])]
               (swap! observations conj
                      {:original original
                       :reusable reusable
                       :size-before size-before
                       :consumed (set consumed)
                       :empty? (hs/pool-empty? pool)
                       :special-values special-values})))))]
    (support/check! context "stateful pools round-trip through reusable and consumed draws"
           (:passed? result))
    (support/check! context "reusable pool draws retain values and consumed draws remove them"
           (and (seq @observations)
                (every?
                 (fn [{:keys [original reusable size-before consumed empty?
                              special-values]}]
                   (and (contains? original reusable)
                        (= (count original) size-before)
                        (= original consumed)
                        empty?
                        (= #{nil false} special-values)))
                 @observations))))
  (let [result
        (h/run-test!
         {:test-cases 30
          :seed 20260729
          :database ""
          :verbosity :quiet}
         (fn [_]
           (let [invariant-runs (atom 0)
                 pool (hs/pool)
                 final-state
                 (hs/run!
                  {:initial-state {:applied 0}
                   :rules
                   [(hs/rule
                     :draw-empty
                     (fn [state]
                       (h/draw! (hs/values-consumed pool))
                       (update state :applied inc)))]
                   :invariants
                   [(hs/invariant
                     :unchanged
                     (fn [state]
                       (swap! invariant-runs inc)
                       (zero? (:applied state))))]})]
             (when-not (and (= {:applied 0} final-state)
                            (= 1 @invariant-runs))
               (throw
                (ex-info "skipped stateful rule changed state or ran invariants"
                         {:hegel/origin
                          "hegel.test-runner:stateful-skipped-rule"}))))))]
    (support/check! context "empty pool draws skip stateful rules without failing the case"
           (:passed? result))
    (support/check! context "skipped rules do not mutate state or rerun invariants"
           (zero? (:interesting-test-cases result))))
  (let [states (atom [])
        result
        (h/run-test!
         {:test-cases 60
          :seed 20260730
          :database ""
          :verbosity :quiet}
         (fn [_]
           (swap!
            states conj
            (hs/run!
             {:initial-state {:model [] :sut []}
              :rules
              [(hs/rule
                :push
                (fn [state]
                  (let [value (h/draw! (g/integer -20 20))]
                    (-> state
                        (update :model conj value)
                        (update :sut conj value)))))
               (hs/rule
                :pop
                {:precondition #(seq (:model %))}
                (fn [state]
                  (-> state
                      (update :model pop)
                      (update :sut pop))))]
              :invariants
              [(hs/invariant :model-matches-sut
                             #(= (:model %) (:sut %)))]}))))]
    (support/check! context "stateful model tests execute generated rule sequences"
           (:passed? result))
    (support/check! context "stateful invariants hold on every returned model state"
           (and (seq @states)
                (every? #(= (:model %) (:sut %)) @states)))))

(defn- stateful-failure [seed config]
  (let [result
        (h/run-test!
         {:test-cases 300
          :seed seed
          :database ""
          :report-multiple-failures? false
          :verbosity :quiet}
         (fn [_]
           (hs/run! (if (fn? config) (config) config))))]
    {:result result
     :failure (first (:failures result))
     :trace (some-> result :failures first :exception ex-data ::hs/trace)}))

(defn stateful-shrink-quality [context]
  (let [counter
        (stateful-failure
         1
         {:initial-state {:count 0}
          :rules [(hs/rule :inc #(update % :count inc))]
          :invariants [(hs/invariant :below-two #(< (:count %) 2))]})
        transition
        (stateful-failure
         1
         {:initial-state {:open? false :opened? false}
          :rules
          [(hs/rule :open
                    {:precondition #(not (:open? %))}
                    #(assoc % :open? true :opened? true))
           (hs/rule :close
                    {:precondition #(:open? %)}
                    #(assoc % :open? false))]
          :invariants
          [(hs/invariant
            :never-reclose
            #(not (and (:opened? %) (not (:open? %)))))]})
        rule-failure
        (stateful-failure
         1
         {:initial-state nil
          :rules
          [(hs/rule
            :explode
            (fn [_]
              (throw (ex-info "rule exploded" {:type ::expected-rule-failure}))))]})
        initial-invariant
        (stateful-failure
         1
         {:initial-state 0
          :rules [(hs/rule :unused identity)]
          :invariants [(hs/invariant :initially-valid (constantly false))]})
        pool-double-increment
        (stateful-failure
         1
         (fn []
           (let [handles (hs/pool)]
             {:initial-state {:counters []}
              :rules
              [(hs/rule
                :new-counter
                (fn [state]
                  (let [id (count (:counters state))]
                    (hs/add! handles id)
                    (update state :counters conj 0))))
               (hs/rule
                :increment
                {:precondition (fn [_] (not (hs/pool-empty? handles)))}
                (fn [state]
                  (let [id (h/draw! (hs/values-reusable handles))]
                    (update-in state [:counters id] inc))))]
              :invariants
              [(hs/invariant :below-two
                             #(every? (fn [n] (< n 2)) (:counters %)))]})))
        pool-distinct-pair
        (stateful-failure
         2
         (fn []
           (let [handles (hs/pool)]
             {:initial-state {:next-id 0}
              :rules
              [(hs/rule
                :new-object
                (fn [state]
                  (hs/add! handles (:next-id state))
                  (update state :next-id inc)))
               (hs/rule
                :pair
                {:precondition (fn [_] (<= 2 (hs/pool-size handles)))}
                (fn [state]
                  (let [a (h/draw! (hs/values-reusable handles))
                        b (h/draw! (hs/values-reusable handles))]
                    (when-not (= a b)
                      (throw (ex-info "distinct pair"
                                      {:type ::distinct-pair})))
                    state)))]})))]
    (support/check! context "stateful shrinking minimizes a counter failure to two increments"
           (= [:inc :inc] (:trace counter)))
    (support/check! context "stateful shrinking preserves the required open-close transition"
           (= [:open :close] (:trace transition)))
    (support/check! context "minimal stateful traces replay without flakiness"
           (every?
            (fn [{:keys [result failure]}]
              (and (not (:passed? result))
                   (:reproduced? failure)
                   (false? (:flaky? result))))
            [counter transition]))
    (support/check! context "stateful invariant names provide stable failure origins"
           (and (= "hegel.stateful/invariant:below-two"
                   (-> counter :failure :origin))
                (= "hegel.stateful/invariant:never-reclose"
                   (-> transition :failure :origin))))
    (support/check! context "genuine rule failures remain interesting with a stable trace"
           (and (= [:explode] (:trace rule-failure))
                (= "hegel.stateful/rule:explode"
                   (-> rule-failure :failure :origin))
                (:reproduced? (:failure rule-failure))))
    (support/check! context "stateful invariants are checked before the first rule"
           (and (= [] (:trace initial-invariant))
                (= "hegel.stateful/invariant:initially-valid"
                   (-> initial-invariant :failure :origin))
                (:reproduced? (:failure initial-invariant))))
    (support/check! context "pool shrinking removes unrelated counter insertions"
           (= [:new-counter :increment :increment]
              (:trace pool-double-increment)))
    (support/check! context "pool shrinking preserves the minimal distinct pair"
           (= [:new-object :new-object :pair]
              (:trace pool-distinct-pair)))
    (support/check! context "pool shrink regressions replay without flakiness"
           (every? (fn [{:keys [result failure]}]
                     (and (not (:passed? result))
                          (:reproduced? failure)
                          (false? (:flaky? result))))
                   [pool-double-increment pool-distinct-pair]))))

(defn- longest-run [values]
  (loop [values values
         previous ::none
         current 0
         longest 0]
    (if-let [value (first values)]
      (let [current (if (= previous value) (inc current) 1)]
        (recur (next values) value current (max longest current)))
      longest)))

(defn stateful-swarm-and-control-flow [context]
  (let [runs (atom [])
        result
        (h/run-test!
         {:test-cases 100
          :seed 20260728
          :derandomize? true
          :database ""
          :verbosity :quiet}
         (fn [_]
           (swap!
            runs conj
            (hs/run!
             {:initial-state []
              :rules [(hs/rule :rule-0 #(conj % 0))
                      (hs/rule :rule-1 #(conj % 1))
                      (hs/rule :rule-2 #(conj % 2))]}))))
        lengths (mapv count @runs)
        repeated (mapv longest-run @runs)]
    (support/check! context "libhegel performs swarm rule selection for stateful tests"
           (and (:passed? result)
                (>= (count (filter #(>= % 20) repeated)) 10)))
    (support/check! context "engine-managed stateful runs respect the 50-attempt normal cap"
           (and (seq lengths)
                (every? #(<= 1 % 50) lengths)
                (> (count (filter #(= 50 %) lengths))
                   (/ (count lengths) 2)))))
  (let [lengths (atom [])
        result
        (h/run-test!
         {:test-cases 40
          :stateful-step-count 7
          :seed 20260822
          :database ""
          :verbosity :quiet}
         (fn [_]
           (swap! lengths conj
                  (count
                   (hs/run!
                    {:initial-state []
                     :rules [(hs/rule :step #(conj % :step))]})))))]
    (support/check! context "stateful-step-count configures Hegel's round budget"
           (and (:passed? result)
                (seq @lengths)
                (every? #(<= 1 % 7) @lengths)
                (some #(= 7 %) @lengths))))
  (let [first-rule? (atom true)
        result
        (h/run-test!
         {:test-cases 5
          :seed 17
          :database ""
          :verbosity :quiet
          :suppress-health-checks [:large-initial-test-case
                                   :test-cases-too-large
                                   :too-slow]}
         (fn [_]
           (hs/run!
            {:initial-state 0
             :rules
             [(hs/rule
               :hungry
               (fn [state]
                 (when (compare-and-set! first-rule? true false)
                   (dotimes [_ 10000]
                     (h/draw! (g/integer))))
                 (inc state)))]})))]
    (support/check! context "running out of draw data inside a rule remains an overrun"
           (and (:passed? result)
                (= 1 (:overrun-test-cases result))
                (pos? (:valid-test-cases result)))))
  (let [result
        (h/run-test!
         {:test-cases 20 :seed 29 :database "" :verbosity :quiet}
         (fn [_]
           (let [invariant-runs (atom 0)
                 state
                 (hs/run!
                  {:initial-state 0
                   :rules
                   [(hs/rule
                     :skip
                     (fn [state]
                       (h/assume! false)
                       (inc state)))]
                   :invariants
                   [(hs/invariant
                     :unchanged
                     (fn [state]
                       (swap! invariant-runs inc)
                       (zero? state)))]})]
             (when-not (and (zero? state) (= 1 @invariant-runs))
               (throw
                (ex-info "rule assumption did not skip cleanly"
                         {:hegel/origin
                          "hegel.test-runner:stateful-rule-assumption"}))))))]
    (support/check! context "h/assume! inside a stateful rule skips only that rule"
           (:passed? result)))
  (let [first-invariant? (atom true)
        result
        (h/run-test!
         {:test-cases 5 :seed 31 :database "" :verbosity :quiet}
         (fn [_]
           (hs/run!
            {:initial-state 0
             :rules [(hs/rule :step identity)]
             :invariants
             [(hs/invariant
               :domain
               (fn [_]
                 (when (compare-and-set! first-invariant? true false)
                   (h/assume! false))
                 true))]})))]
    (support/check! context "h/assume! inside an invariant rejects the whole test case"
           (and (:passed? result)
                (= 1 (:invalid-test-cases result))
                (zero? (:interesting-test-cases result)))))
  (support/check! context "state machines reject invalid rule declarations"
         (and (support/throws? #(hs/run! {:initial-state nil :rules []}))
              (support/throws?
               #(hs/run!
                 {:initial-state nil
                  :rules [(hs/rule :same identity)
                          (hs/rule :same identity)]}))
              (support/throws? #(hs/rule :bad {:precondition nil} identity))
              (support/throws? #(hs/rule :bad {:precondition false} identity))
              (hs/rule? (hs/rule :default identity))))
  (let [error
        (try
          (h/run-test!
           {:test-cases 1 :seed 37 :database "" :verbosity :quiet}
           (fn [_]
             (hs/run! {:initial-state nil :rules []})))
          nil
          (catch Throwable error
            error))]
    (support/check! context "stateful configuration errors abort instead of shrinking"
           (= ::hs/invalid-argument (:type (ex-data error))))))

(defn latest-stateful-abi [context]
  (let [counts (atom {:collection-new 0
                      :collection-free 0
                      :pool-new 0
                      :pool-free 0
                      :machine-new 0
                      :machine-free 0
                      :rule-rejected 0})
        states (atom [])
        new-collection! hffi/new-collection!
        collection-free! hffi/collection-free!
        new-pool! hffi/new-pool!
        pool-free! hffi/pool-free!
        new-state-machine! hffi/new-state-machine!
        state-machine-free! hffi/state-machine-free!
        state-machine-rule-rejected! hffi/state-machine-rule-rejected!
        result
        (with-redefs
          [hffi/new-collection!
           (fn [& args]
             (swap! counts update :collection-new inc)
             (apply new-collection! args))
           hffi/collection-free!
           (fn [& args]
             (swap! counts update :collection-free inc)
             (apply collection-free! args))
           hffi/new-pool!
           (fn [& args]
             (swap! counts update :pool-new inc)
             (apply new-pool! args))
           hffi/pool-free!
           (fn [& args]
             (swap! counts update :pool-free inc)
             (apply pool-free! args))
           hffi/new-state-machine!
           (fn [& args]
             (swap! counts update :machine-new inc)
             (apply new-state-machine! args))
           hffi/state-machine-free!
           (fn [& args]
             (swap! counts update :machine-free inc)
             (apply state-machine-free! args))
           hffi/state-machine-rule-rejected!
           (fn [& args]
             (swap! counts update :rule-rejected inc)
             (apply state-machine-rule-rejected! args))]
          (h/run-test!
           {:test-cases 6
            :stateful-step-count 7
            :seed 20260811
            :database ""
            :verbosity :quiet}
           (fn [_]
             (h/draw! (g/vector {:size 2} (g/integer 0 10)))
             (let [pool (hs/pool)
                   attempts (atom 0)]
               (hs/add! pool :owned)
               (h/draw! (hs/values-reusable pool))
               (swap!
                states conj
                (hs/run!
                 {:initial-state 0
                  :rules
                  [(hs/rule
                    :alternating
                    {:precondition
                     (fn [_]
                       (odd? (swap! attempts inc)))}
                    inc)]}))))))]
    (support/check! context "latest stateful step count controls successful rule steps"
           (and (:passed? result)
                (seq @states)
                ;; Native 0.36.1 fixes continuation draws: 7 is an upper
                ;; bound, not a promise that every generated trace is full.
                ;; The upgrade suite separately requires shorter and full
                ;; traces with seeded positive coverage controls.
                (every? #(<= 1 % 7) @states)))
    (support/check! context "rejected stateful rules are reported without consuming steps"
           (pos? (:rule-rejected @counts)))
    (support/check! context "latest opaque collection handles are freed exactly once"
           (and (pos? (:collection-new @counts))
                (= (:collection-new @counts)
                   (:collection-free @counts))))
    (support/check! context "latest opaque pool handles are freed exactly once"
           (and (pos? (:pool-new @counts))
                (= (:pool-new @counts) (:pool-free @counts))))
    (support/check! context "latest opaque state-machine handles are freed exactly once"
           (and (pos? (:machine-new @counts))
                (= (:machine-new @counts) (:machine-free @counts)))))
  (let [error
        (try
          (h/run-test!
           {:test-cases 1
            :stateful-step-count 0
            :seed 1
            :database ""
            :verbosity :quiet}
           (fn [_] nil))
          nil
          (catch Throwable error
            error))]
    (support/check! context "stateful step count rejects zero before a native run"
           (= ::h/invalid-option (:type (ex-data error)))))
  ;; Exact-version replay and the fixed-50 negative control now live in
  ;; libhegel-upgrade-test. The old blob is retained under fixtures/hegel-0.32.3.
  nil)

(defn- concurrent-private [name]
  (or (ns-resolve 'hegel.stateful.concurrent name)
      (throw (ex-info "concurrent validation helper is missing"
                      {:name name}))))

(defn- concurrent-error-of [thunk]
  (try
    (thunk)
    nil
    (catch Throwable error error)))

(defn- concurrent-usage-error? [expected-type thunk]
  (let [error (concurrent-error-of thunk)]
    (and error
         (= expected-type (:type (ex-data error)))
         (true? (:hegel/usage-error? (ex-data error))))))

(defn concurrent-api-declarations
  "Pure declaration/validation coverage for the future concurrent executor.

  This scenario intentionally calls only the new native-free namespace.  The
  stateful suite itself loads the existing native suite, but no native function
  is entered by this scenario."
  [context]
  (let [rule (concurrent/rule :put :z (fn [_] :applied))
        invariant (concurrent/invariant :consistent (fn [_] true))
        group-plan (concurrent-private 'group-plan)
        validate-options (concurrent-private 'validate-options!)
        validate-config (concurrent-private 'validate-config!)
        callback-contract (get rule ::concurrent/callback-contract)]
    (support/check! context "concurrent rule and invariant predicates identify declarations"
           (and (concurrent/rule? rule)
                (concurrent/invariant? invariant)
                (not (concurrent/rule? invariant))
                (not (concurrent/invariant? rule))))
    (support/check! context "concurrent worker callback contract is explicit"
           (= {:context-keys #{:shared :worker-index :round :group :cancelled?}
               :result-values #{:applied :rejected}}
              callback-contract))
    (let [rules [(concurrent/rule :write :z identity)
                 (concurrent/rule "read" :a identity)
                 (concurrent/rule 'delete 'z identity)]
          plan (group-plan rules)]
      (support/check! context "concurrent groups normalize, sort, and collide deterministically"
             (and (= ["a" "z"] (:names plan))
                  (= {"a" 0 "z" 1} (:ids plan))
                  (= [1 0 1] (:rule-groups plan))
                  (every? #(and (integer? %) (<= 0 %)) (:rule-groups plan)))))
    (let [options (validate-options {:workers 3})
          complete (validate-options
                    {:workers 3
                     :test-cases 4
                     :stateful-step-count 5
                     :seed 6
                     :verbosity :quiet
                     :derandomize? true
                     :suppress-health-checks [:too-slow]
                     :max-diagnostic-events 7})]
      (support/check! context "concurrent options default the diagnostic bound"
             (= 1024 (:max-diagnostic-events options)))
      (support/check! context "concurrent options accept the complete v1 surface"
             (= complete
                {:workers 3
                 :test-cases 4
                 :stateful-step-count 5
                 :seed 6
                 :verbosity :quiet
                 :derandomize? true
                 :suppress-health-checks [:too-slow]
                 :max-diagnostic-events 7})))
    (doseq [[label options]
            [["non-map" []]
             ["missing workers" {}]
             ["workers below two" {:workers 1}]
             ["workers fractional" {:workers 2.0}]
             ["workers above int64" {:workers 9223372036854775808N}]
             ["test cases below one" {:workers 2 :test-cases 0}]
             ["test cases above uint64"
              {:workers 2 :test-cases 18446744073709551616N}]
             ["stateful steps below one" {:workers 2 :stateful-step-count 0}]
             ["stateful steps above int64"
              {:workers 2 :stateful-step-count 9223372036854775808N}]
             ["seed below zero" {:workers 2 :seed -1}]
             ["seed above uint64"
              {:workers 2 :seed 18446744073709551616N}]
             ["diagnostic bound below one" {:workers 2 :max-diagnostic-events 0}]
             ["diagnostic bound above int64"
              {:workers 2 :max-diagnostic-events 9223372036854775808N}]
             ["verbosity type" {:workers 2 :verbosity :missing}]
             ["derandomize type" {:workers 2 :derandomize? :yes}]
             ["health collection type" {:workers 2 :suppress-health-checks :too-slow}]
             ["health member" {:workers 2 :suppress-health-checks [:missing]}]
             ["unknown option" {:workers 2 :unknown true}]
             ["forbidden backend" {:workers 2 :backend :default}]
             ["forbidden database" {:workers 2 :database ""}]
             ["forbidden database key" {:workers 2 :database-key "x"}]
             ["forbidden name" {:workers 2 :name "x"}]
             ["forbidden phases" {:workers 2 :phases [:generate]}]
             ["forbidden multiple failures"
              {:workers 2 :report-multiple-failures? false}]
             ["forbidden statistics" {:workers 2 :show-statistics? false}]
             ["forbidden observations" {:workers 2 :observations? false}]
             ["forbidden coverage" {:workers 2 :coverage {}}]
             ["forbidden counterexample" {:workers 2 :counterexample {}}]
             ["forbidden targeting" {:workers 2 :targeting true}]]]
      (support/check! context (str "concurrent option rejects " label)
             (concurrent-usage-error? ::concurrent/invalid-option
                                       #(validate-options options))))
    (doseq [[label thunk]
            [["rule NUL name" #(concurrent/rule "bad\u0000" :group identity)]
             ["rule name type" #(concurrent/rule 42 :group identity)]
             ["rule blank group" #(concurrent/rule :rule "  " identity)]
             ["rule group type" #(concurrent/rule :rule {} identity)]
             ["rule callback" #(concurrent/rule :rule :group :not-callable)]
             ["invariant NUL name"
              #(concurrent/invariant (str "bad" (char 0)) (constantly true))]
             ["invariant name type"
              #(concurrent/invariant [:bad] (constantly true))]
             ["invariant callback" #(concurrent/invariant :bad true)]]]
      (support/check! context (str "concurrent declaration rejects " label)
             (concurrent-usage-error? ::concurrent/invalid-argument thunk)))
    (let [error (concurrent-error-of #(concurrent/rule 42 :group identity))]
      (support/check! context "concurrent name errors preserve the rejected value"
             (= 42 (:name (ex-data error)))))
    (let [same-rule (concurrent/rule :same :group identity)
          same-string-rule (concurrent/rule "same" :other identity)
          same-invariant (concurrent/invariant :same (constantly true))
          same-string-invariant (concurrent/invariant "same" (constantly true))
          valid-config (validate-config
                        {:shared nil
                         :rules [(concurrent/rule :write :z identity)
                                 (concurrent/rule :read :a identity)]
                         :invariants [(concurrent/invariant :ok (constantly true))]
                         :close! (fn [_] nil)})]
      (support/check! context "concurrent config accepts shared state and close callback"
             (and (= ["a" "z"] (get-in valid-config [::concurrent/group-plan :names]))
                  (= [1 0] (get-in valid-config [::concurrent/group-plan :rule-groups]))))
      (support/check! context "concurrent rule and invariant names are unique within kind"
             (map? (validate-config
                    {:shared nil
                     :rules [(concurrent/rule :same :group identity)]
                     :invariants [(concurrent/invariant :same
                                                       (constantly true))]})))
      (doseq [[label config]
              [["non-map" []]
               ["missing shared" {:rules [same-rule]}]
               ["missing rules" {:shared nil}]
               ["empty rules" {:shared nil :rules []}]
               ["rules type" {:shared nil :rules :rules}]
               ["unordered rules" {:shared nil :rules #{same-rule}}]
               ["wrong rule declaration"
                {:shared nil :rules [same-invariant]}]
               ["invariants type" {:shared nil :rules [same-rule]
                                    :invariants :invariants}]
               ["false invariants" {:shared nil :rules [same-rule]
                                    :invariants false}]
               ["nil invariants" {:shared nil :rules [same-rule]
                                  :invariants nil}]
               ["unordered invariants" {:shared nil :rules [same-rule]
                                        :invariants #{same-invariant}}]
               ["wrong invariant declaration"
                {:shared nil :rules [same-rule] :invariants [same-rule]}]
               ["nil close callback" {:shared nil :rules [same-rule] :close! nil}]
               ["close callback" {:shared nil :rules [same-rule] :close! false}]
               ["unknown key" {:shared nil :rules [same-rule] :unknown true}]
               ["duplicate normalized rules"
                {:shared nil :rules [same-rule same-string-rule]}]
               ["duplicate normalized invariants"
                {:shared nil :rules [same-rule]
                 :invariants [same-invariant same-string-invariant]}]]]
        (support/check! context (str "concurrent config rejects " label)
               (concurrent-usage-error? ::concurrent/invalid-argument
                                         #(validate-config config)))))))

(defn libhegel-upgrade-contract [context]
  (let [result (t/run-tests 'hegel.libhegel-upgrade-test)]
    (support/check! context "libhegel upgrade contract suite"
                    (zero? (+ (:fail result) (:error result))))))
