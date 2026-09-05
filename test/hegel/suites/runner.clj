(ns hegel.suites.runner
  "Core runner contract scenarios, loaded only when selected."
  (:require [clojure.string :as str]
            [hegel.core :as h]
            [hegel.ffi :as hffi]
            [hegel.generator :as g]
            [hegel.host :as host]
            [hegel.report :as report]
            [hegel.test-support :as support]))

(defn bootstrap-load-isolation [context]
  ;; Fresh-process controls prove absence. Here preserve the state captured
  ;; before lazy dispatch so a consumer's earlier Malli load is not blamed on
  ;; bootstrap in a multi-invocation process.
  (support/check! context "bootstrap does not eagerly load optional Malli namespaces"
                  (= (:optional-namespaces context)
                     {'malli.core (boolean (find-ns 'malli.core))
                      'hegel.malli (boolean (find-ns 'hegel.malli))})))
(defn host-exception-seam [context]
  (let [handler-calls (atom 0)
        value (host/try-catch-all
               :completed
               error
               (swap! handler-calls inc)
               error)]
    (support/check! context "host catch seam preserves successful values without handling"
           (and (= :completed value)
                (zero? @handler-calls))))
  (let [handler-calls (atom 0)
        thrown (ex-info "expected host exception" {:phase :host-seam})
        caught (host/try-catch-all
                (throw thrown)
                error
                (swap! handler-calls inc)
                error)]
    (support/check! context "host catch seam catches broadly and preserves the throwable"
           (and (= 1 @handler-calls)
                (identical? thrown caught)
                (= {:phase :host-seam} (ex-data caught))))))

(defn passing-run [context]
  (let [calls (atom 0)
        result (h/run-test!
                {:test-cases 12
                 :seed 11
                 :database ""
                 :verbosity :quiet}
                (fn [_]
                  (swap! calls inc)))]
    (support/check! context "passing property reports passed" (:passed? result))
    ;; A property with no draws exhausts its choice tree after one case; the
    ;; configured count is a maximum, not a promise of duplicate executions.
    (support/check! context "passing property accounts for every executed valid case"
           (and (pos? @calls)
                (<= @calls 12)
                (= @calls (:valid-test-cases result))))
    (support/check! context "explicit seed is reflected in the result"
           (= "11" (:seed result)))
    (support/check! context "passing property has no final replay"
           (empty? (:final result)))))

(defn shrinking-run [context]
  (let [final-values (atom [])
        result
        (h/run-test!
         {:test-cases 200
          :seed 1777986545686
          :database ""
          :report-multiple-failures? false
          :verbosity :quiet}
         (fn [_]
           (let [x (h/draw! (g/integer 0 1000))]
             (when (h/final?)
               (swap! final-values conj x))
             (when (>= x 500)
               (throw
                (ex-info "threshold violated"
                         {:hegel/origin "hegel.test-runner:threshold"
                          :x x}))))))
        failure (first (:failures result))
        final-outcome (first (:final result))]
    (support/check! context "failing property reports failed" (not (:passed? result)))
    (support/check! context "one stable origin produces one distinct failure"
           (= 1 (:n-failures result) (count (:failures result))))
    (support/check! context "failure origin is stable"
           (= "hegel.test-runner:threshold" (:origin failure)))
    (support/check! context "shrinker produces the known minimal reproduction blob"
           (= "AAEAAAAACgIAAAD0AQ==" (:reproduction-blob failure)))
    (support/check! context "minimal counterexample is replayed in final phase"
           (= [500] @final-values))
    (support/check! context "final replay preserves the minimal drawn value"
           (= 500 (-> final-outcome :exception ex-data :x)))
    (support/check! context "final replay reproduced the property failure"
           (:reproduced? failure))
    (support/check! context "reproduced failure is not flaky"
           (false? (:flaky? result)))))

(defn engine-nondeterminism [context]
  (let [calls (atom 0)
        result
        (h/run-test!
         {:test-cases 1
          :seed 17
          :database ""
          :verbosity :quiet
          :suppress-health-checks [:large-initial-test-case]}
         (fn [_]
           (h/draw! (g/integer 0 10))
           (when (= 1 (swap! calls inc))
             (throw
              (ex-info "transient property failure"
                       {:hegel/origin
                        "hegel.test-runner:engine-outcome-flakiness"
                        :attempt 1
                        :operation :read})))))]
    (support/check! context "engine outcome flakiness returns a countable failure result"
           (and (not (:passed? result))
                (= :error (:status result))
                (= "17" (:seed result))
                (true? (:flaky? result))
                (str/starts-with? (:error result) "Flaky test detected:")
                (zero? (:n-failures result))
                (empty? (:failures result))
                (empty? (:final result))))
    (let [observed (first (:observed-failures result))]
      (support/check! context "engine flakiness retains the structured observed failure"
             (and (= "hegel.test-runner:engine-outcome-flakiness"
                     (:origin observed))
                  (= 1 (:count observed))
                  (= {:hegel/origin
                      "hegel.test-runner:engine-outcome-flakiness"
                      :attempt 1
                      :operation :read}
                     (-> observed :first :data))
                  (= (:first observed) (:last observed))))))
  (let [calls (atom 0)
        result
        (h/run-test!
         {:test-cases 1
          :seed 19
          :database ""
          :verbosity :quiet
          :suppress-health-checks [:large-initial-test-case]}
         (fn [_]
           (let [call (swap! calls inc)]
             (h/draw! (g/integer 0 (+ 10 call)))
             (throw
              (ex-info "stable failure with unstable generator"
                       {:hegel/origin
                        "hegel.test-runner:generator-nondeterminism"
                        :call call})))))]
    (support/check! context "non-deterministic generation returns a countable failure result"
           (and (not (:passed? result))
                (= :error (:status result))
                (= "19" (:seed result))
                (true? (:flaky? result))
                (str/starts-with?
                 (:error result)
                 "Your data generation is non-deterministic:")
                (zero? (:n-failures result))))
    (let [observed (first (:observed-failures result))]
      (support/check! context "observed failures aggregate repeated stable origins"
             (and (= "hegel.test-runner:generator-nondeterminism"
                     (:origin observed))
                  (< 1 (:count observed))
                  (= 1 (-> observed :first :data :call))
                  (= (:count observed)
                     (-> observed :last :data :call))))))
  (let [error
        (try
          (h/run-test!
           {:test-cases 5 :seed 23 :database "" :verbosity :quiet}
           (fn [_]
             (dotimes [_ 10000]
               (h/draw! (g/integer)))))
          nil
          (catch Throwable error
            error))]
    (support/check! context "non-flakiness engine errors still abort the run"
           (= ::h/run-error (:type (ex-data error))))))

(defn counting-reporting [context]
  (let [events (atom [])
        runner (report/counting-runner
                {:reporter #(swap! events conj %)})
        pass-result
        (report/run!
         runner "passing property"
         #(h/run-test!
           {:test-cases 3 :seed 31 :database "" :verbosity :quiet}
           (fn [_] (h/draw! (g/integer 0 3)))))
        passed-after-first? (report/passed? runner)
        fail-result
        (report/run!
         runner "failing property"
         #(h/run-test!
           {:test-cases 1 :seed 37 :database "" :verbosity :quiet}
           (fn [_]
             (h/draw! (g/integer 0 0))
             (throw
              (ex-info "expected report failure"
                       {:hegel/origin
                        "hegel.test-runner:counting-reporting"})))))
        error-result
        (report/run!
         runner "setup error"
         #(throw (ex-info "expected setup error" {:phase :setup})))]
    (support/check! context "counting runner returns normal property results"
           (and (:passed? pass-result)
                (not (:passed? fail-result))
                (nil? error-result)))
    (support/check! context "counting runner tracks returned failures and thrown errors"
           (and passed-after-first?
                (= 3 (report/run-count runner))
                (= 2 (report/failure-count runner))
                (not (report/passed? runner))))
    (support/check! context "counting runner emits structured continuation events"
           (and (= [:pass :fail :error] (mapv :type @events))
                (= "37" (-> @events second :result :seed))
                (= {:phase :setup}
                   (-> @events (nth 2) :exception ex-data))))))

(defn cleanup-and-version [context]
  ;; A second run after the failed/replayed run exercises all cleanup paths well
  ;; enough to catch double-free/use-after-free regressions in the basic loop.
  (let [result (h/run-test!
                {:test-cases 3
                 :database ""
                 :verbosity :quiet}
                (fn [_] nil))]
    (support/check! context "a new run succeeds after failed-run cleanup" (:passed? result))
    (support/check! context "loaded libhegel matches the bound ABI"
           (= hffi/libhegel-version (hffi/version)))))

(defn generated-seed [context]
  (let [first-values (atom [])
        replay-values (atom [])
        result (h/run-test!
                {:test-cases 10
                 :database ""
                 :verbosity :quiet}
                (fn [_]
                  (swap! first-values conj
                         (h/draw! (g/integer 0 1000000)))))
        replay (h/run-test!
                {:test-cases 10
                 :seed (parse-long (:seed result))
                 :database ""
                 :verbosity :quiet}
                (fn [_]
                  (swap! replay-values conj
                         (h/draw! (g/integer 0 1000000)))))]
    (support/check! context "a run without :seed returns its generated seed"
           (some? (:seed result)))
    (support/check! context "an auto-generated seed can be supplied for exact replay"
           (and (= (:seed result) (:seed replay))
                (= @first-values @replay-values))))
  (let [opts {:test-cases 1
              :derandomize? true
              :name "generated-seed-test"
              :database ""
              :verbosity :quiet}
        first-run (h/run-test! opts (fn [_] nil))
        second-run (h/run-test! opts (fn [_] nil))]
    (support/check! context "derandomized runs derive the same known seed"
           (= (:seed first-run) (:seed second-run)))))

(defn controls-and-sample [context]
  (let [calls (atom 0)
        result (h/run-test!
                {:test-cases 1
                 :seed 27
                 :database ""
                 :verbosity :quiet}
                (fn [_]
                  ;; Force one rejection without depending on a particular
                  ;; generator distribution, then allow the next case through.
                  (h/assume! (> (swap! calls inc) 1))))]
    (support/check! context "assume! classifies a rejected test case as invalid"
           (= 1 (:invalid-test-cases result)))
    (support/check! context "an assumption rejection is not a property failure"
           (:passed? result)))
  (let [result (h/run-test!
                {:test-cases 5
                 :seed 31
                 :database ""
                 :verbosity :quiet}
                (fn [_]
                  (let [x (h/draw! (g/integer 0 100))]
                    (h/target! x :drawn-integer))))]
    (support/check! context "target! participates in a passing run" (:passed? result)))
  (let [values (h/sample 5 (g/integer 7 7))]
    (support/check! context "sample returns generated values"
           (and (seq values)
                (<= (count values) 5)
                (every? #{7} values))))
  (let [error
        (try
          (h/sample
           5
           (g/composite-fn
            (fn [_]
              (throw
               (ex-info "sample generator failed"
                        {:hegel/origin "hegel.test-runner:sample-failure"
                         :sample :original-cause})))))
          nil
          (catch Throwable error
            error))
        data (ex-data error)
        run (:run data)]
    (support/check! context "sample throws instead of returning a partial failed sample"
           (and (= ::h/sample-failed (:type data))
                (not (:passed? run))
                (= :failed (:status run))
                (= 1 (:n-failures run))))
    (support/check! context "sample failure retains the original cause data"
           (= {:message "sample generator failed"
               :data {:hegel/origin "hegel.test-runner:sample-failure"
                      :sample :original-cause}}
              (select-keys (:cause data) [:message :data]))))
  (let [run {:passed? false
             :status :error
             :flaky? true
             :error "Flaky test detected: sample"
             :failures []
             :final []}
        error (try
                (with-redefs [h/run-test! (fn [& _] run)]
                  (h/sample 5 (g/just :never-returned)))
                nil
                (catch Throwable error
                  error))
        data (ex-data error)]
    (support/check! context "sample rejects a flaky run without returning values"
           (and (= ::h/sample-failed (:type data))
                (= run (:run data))
                (not (:passed? (:run data)))
                (true? (:flaky? (:run data)))
                (= "Flaky test detected: sample"
                   (-> data :run :error))))))

(defn harness-integrity [context]
  (let [events (atom [])
        marker (ex-info "mapping failed" {:marker :mapping})
        generator (g/fmap (fn [_] (throw marker)) (g/just :value))
        error
        (with-redefs [hffi/start-span!
                      (fn [_ _ label] (swap! events conj [:start label]))
                      hffi/stop-span!
                      (fn
                        ([_ _] (swap! events conj [:stop false]))
                        ([_ _ discard?]
                         (swap! events conj [:stop discard?])))]
          (try
            (generator {:context :context :handle :test-case})
            nil
            (catch Throwable error
              error)))]
    (support/check! context "combinator spans close exactly once when mapping throws"
           (and (= marker error)
                (= [[:start hffi/label-mapped] [:stop false]] @events))))
  (let [stop-calls (atom 0)
        marker (ex-info "stopping mapped span failed" {:marker :stop})
        generator (g/fmap identity (g/just :value))
        error
        (with-redefs [hffi/start-span! (fn [& _])
                      hffi/stop-span!
                      (fn [& _]
                        (swap! stop-calls inc)
                        (throw marker))]
          (try
            (generator {:context :context :handle :test-case})
            nil
            (catch Throwable error
              error)))]
    (support/check! context "combinator stop failures are not retried against the same span"
           (and (= marker error) (= 1 @stop-calls))))
  (let [events (atom [])
        marker (ex-info "predicate failed" {:marker :predicate})
        generator (g/filter (fn [_] (throw marker)) (g/just :value))
        error
        (with-redefs [hffi/start-span!
                      (fn [_ _ label] (swap! events conj [:start label]))
                      hffi/stop-span!
                      (fn
                        ([_ _] (swap! events conj [:stop false]))
                        ([_ _ discard?]
                         (swap! events conj [:stop discard?])))]
          (try
            (generator {:context :context :handle :test-case})
            nil
            (catch Throwable error
              error)))]
    (support/check! context "filter spans close exactly once when predicates throw"
           (and (= marker error)
                (= [[:start hffi/label-filter] [:stop false]] @events))))
  (let [stop-calls (atom 0)
        marker (ex-info "stopping filter span failed" {:marker :filter-stop})
        generator (g/filter (constantly true) (g/just :value))
        error
        (with-redefs [hffi/start-span! (fn [& _])
                      hffi/stop-span!
                      (fn [& _]
                        (swap! stop-calls inc)
                        (throw marker))]
          (try
            (generator {:context :context :handle :test-case})
            nil
            (catch Throwable error
              error)))]
    (support/check! context "filter stop failures are not retried against the same span"
           (and (= marker error) (= 1 @stop-calls))))
  (let [error
        (with-redefs [hffi/generate-integer!
                      (fn [& _]
                        (throw
                         (ex-info "native harness failed"
                                  {:type ::hffi/error
                                   :operation :generate-integer
                                   :result 3})))]
          (try
            (h/run-test!
             {:test-cases 1 :seed 41 :database "" :verbosity :quiet}
             (fn [_] (h/draw! (g/integer 0 1))))
            nil
            (catch Throwable error
              error)))]
    (support/check! context "native harness errors abort instead of becoming counterexamples"
           (= ::hffi/error (:type (ex-data error)))))
  (let [result
        (h/run-test!
         {:test-cases 10
          :seed 43
          :database ""
          :report-multiple-failures? false
          :verbosity :quiet}
         (fn [_]
           (h/draw! (g/integer 0 10))
           (throw
            (ex-info "origin changed during final replay"
                     {:hegel/origin
                      (if (h/final?)
                        "hegel.test-runner:replay-origin"
                        "hegel.test-runner:original-origin")}))))
        failure (first (:failures result))]
    (support/check! context "final replay requires the original failure origin"
           (and (true? (:flaky? result))
                (false? (:reproduced? failure))
                (= "hegel.test-runner:original-origin" (:origin failure))
                (= "hegel.test-runner:replay-origin"
                   (:replay-origin failure))))))
