(ns hegel.suites.runner
  "Core runner contract scenarios, loaded only when selected."
  (:require [clojure.string :as str]
            [hegel.core :as h]
            [hegel.ffi :as hffi]
            [hegel.generator :as g]
            [hegel.host :as host]
            [hegel.internal.portable-data :as portable-data]
            [hegel.internal.render :as render]
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

(defn- capture-err [thunk]
  (let [value (atom nil)
        output (with-out-str
                 (binding [*err* *out*]
                   (reset! value (thunk))))]
    [@value output]))

(defn- counterexample-normal-only-final-replay [context]
  (let [[result output]
        (capture-err
         #(h/run-test!
                  {:test-cases 200
                   :seed 1777986545686
                   :database ""
                   :report-multiple-failures? false
                   :verbosity :normal}
                  (fn [_]
                    (let [x (h/draw! (g/integer 0 1000) :x)]
                      (h/note! "checking" x)
                      (when (>= x 500)
                        (throw
                         (ex-info "threshold violated"
                                  {:hegel/origin "hegel.test-runner:counterexample-normal"
                                   :x x})))))))
        failure (first (:failures result))]
    (support/check! context "normal verbosity emits only the final-replay counterexample text"
           (and (not (:passed? result))
                (str/includes? output ":x 500")
                (str/includes? output "checking 500")))
    (support/check! context "the final replay snapshot is attached to the failure and to public-final"
           (and (map? (:counterexample failure))
                (string? (:text (:counterexample failure)))
                (str/includes? (:text (:counterexample failure)) "checking 500")
                (= (:counterexample failure)
                   (:counterexample (first (:final result))))))))

(defn- counterexample-quiet-emits-nothing [context]
  (let [[result output]
        (capture-err
         #(h/run-test!
                  {:test-cases 50
                   :seed 1777986545686
                   :database ""
                   :report-multiple-failures? false
                   :verbosity :quiet}
                  (fn [_]
                    (let [x (h/draw! (g/integer 0 1000) :x)]
                      (h/note! "checking" x)
                      (when (>= x 500)
                        (throw
                         (ex-info "threshold violated"
                                  {:hegel/origin "hegel.test-runner:counterexample-quiet"
                                   :x x})))))))]
    (support/check! context "quiet verbosity emits nothing to stderr"
           (= "" output))
    (support/check! context "quiet verbosity produces no counterexample snapshot"
           (nil? (:counterexample (first (:failures result)))))))

(defn- counterexample-draw-and-note-ordering [context]
  (let [result (h/run-test!
                {:test-cases 1
                 :seed 5
                 :database ""
                 :verbosity :normal}
                (fn [_]
                  (h/draw! (g/integer 1 1) :first)
                  (h/note! "middle")
                  (h/draw! (g/integer 2 2) :second)
                  (throw
                   (ex-info "ordering failure"
                            {:hegel/origin "hegel.test-runner:counterexample-ordering"}))))
        entries (-> result :failures first :counterexample :entries)]
    (support/check! context "labelled draw and note entries preserve call order and bounded shape"
           (= [{:kind :draw :label ":first" :text ":first 1\n"}
               {:kind :note :text "middle\n"}
               {:kind :draw :label ":second" :text ":second 2\n"}]
              entries))))

(defn- counterexample-custom-redaction [context]
  (let [secret "s3cr3t-value"
        result (h/run-test!
                {:test-cases 1
                 :seed 7
                 :database ""
                 :verbosity :normal
                 :counterexample {:redact-fn (fn [_] :REDACTED)}}
                (fn [_]
                  (h/draw! (g/just secret) :password)
                  (throw
                   (ex-info "secret drawn"
                            {:hegel/origin "hegel.test-runner:counterexample-redaction"}))))
        snapshot (-> result :failures first :counterexample)]
    (support/check! context "redaction runs before rendering and reaches the rendered text"
           (str/includes? (:text snapshot) "REDACTED"))
    (support/check! context "the unredacted secret never reaches the rendered text or stored entries"
           (and (not (str/includes? (:text snapshot) secret))
                (not-any? #(str/includes? (:text %) secret) (:entries snapshot))))))

(defn- counterexample-oversize-rejection [context]
  (let [big (apply str (repeat 100 "x"))
        result (h/run-test!
                {:test-cases 1
                 :seed 9
                 :database ""
                 :verbosity :normal
                 :counterexample {:max-output-units 40}}
                (fn [_]
                  (dotimes [i 5]
                    (h/note! big i))
                  (throw
                   (ex-info "oversize"
                            {:hegel/origin "hegel.test-runner:counterexample-oversize"}))))
        snapshot (-> result :failures first :counterexample)]
    (support/check! context "an entry that would exceed the output budget is rejected wholesale"
           (true? (:truncated? snapshot)))
    (support/check! context "at most one truncation marker is appended and total output stays bounded"
           (and (= 1 (count (filter #(= :truncated (:kind %)) (:entries snapshot))))
                (<= (portable-data/text-size (:text snapshot)) 40)))))

(defn- counterexample-renderer-error-safety [context]
  (let [result (h/run-test!
                {:test-cases 5
                 :seed 13
                 :database ""
                 :verbosity :debug
                 :counterexample {:render-fn (fn [_] (throw (ex-info "boom" {})))}}
                (fn [_]
                  (h/draw! (g/integer 0 10) :x)
                  nil))]
    (support/check! context "a renderer failure never turns a passing property into a failure"
           (:passed? result)))
  (let [result (h/run-test!
                {:test-cases 5
                 :seed 17
                 :database ""
                 :verbosity :normal
                 :counterexample {:render-fn (fn [_] (throw (ex-info "boom" {})))}}
                (fn [_]
                  (h/draw! (g/integer 0 10) :x)
                  (throw
                   (ex-info "real failure"
                            {:hegel/origin "hegel.test-runner:counterexample-render-error"}))))
        failure (first (:failures result))]
    (support/check! context "a renderer failure does not conceal the original property exception"
           (and (not (:passed? result))
                (= "hegel.test-runner:counterexample-render-error" (:origin failure))
                (= "real failure" (ex-message (:exception failure)))))
    (support/check! context "a renderer failure is recorded as a bounded diagnostic, not a crash"
           (some #(= :render-error (:kind %)) (-> failure :counterexample :errors)))))

(defn- counterexample-cleanup-order [context]
  (let [events (atom [])
        real-printer-free hffi/printer-free!
        real-test-case-free hffi/test-case-free!]
    (with-redefs [hffi/printer-free! (fn [ctx printer]
                                       (swap! events conj :printer-free)
                                       (real-printer-free ctx printer))
                  hffi/test-case-free! (fn [ctx handle]
                                         (swap! events conj :test-case-free)
                                         (real-test-case-free ctx handle))]
      (h/run-test!
       {:test-cases 1
        :seed 21
        :database ""
        :verbosity :normal}
       (fn [_]
         (h/draw! (g/integer 0 1) :x)
         (throw
          (ex-info "cleanup order"
                   {:hegel/origin "hegel.test-runner:counterexample-cleanup-order"})))))
    (support/check! context "the native printer for a case is freed immediately before its test-case handle"
           (and (= 1 (count (filter #{:printer-free} @events)))
                (some #(= [:printer-free :test-case-free] (vec %))
                      (partition 2 1 @events))))))

(defn- counterexample-checkpoint-rollback [context]
  (with-redefs [hffi/note! (fn [& _] nil)]
    (let [state {:ctx ::ctx :handle ::handle :printer nil :enabled? true
                 :native-disabled? (atom true)
                 :render-fn pr-str :redact-fn identity
                 :max-output-units 65536
                 :units (atom 0) :entries (atom []) :truncated? (atom false)
                 :errors (atom []) :checkpoints (atom [])}]
      (render/record-note! state "before" [])
      (render/begin-attempt! state)
      (render/record-note! state "inside" [])
      (support/check! context "begin-attempt! precedes entries recorded during the attempt"
             (= 2 (count @(:entries state))))
      (render/abort-attempt! state)
      (support/check! context "abort-attempt! rolls entries and units back to the checkpoint"
             (and (= 1 (count @(:entries state)))
                  (= (portable-data/text-size "before\n") @(:units state))
                  (false? @(:truncated? state))
                  (empty? @(:errors state))))
      (render/begin-attempt! state)
      (render/record-note! state "kept" [])
      (render/commit-attempt! state)
      (support/check! context "commit-attempt! keeps entries recorded during the attempt"
             (= 2 (count @(:entries state))))
      (support/check! context "matched begin/commit and begin/abort leave no dangling checkpoints"
             (empty? @(:checkpoints state))))))

(defn counterexample-printing
  "Structured counterexample printing (Stage 1): should-log?-gated native
  test-case printers, redact-before-render, bounded output with whole-entry
  truncation, error containment, cleanup ordering, and the internal
  begin/commit/abort-attempt! checkpoint API."
  [context]
  (counterexample-normal-only-final-replay context)
  (counterexample-quiet-emits-nothing context)
  (counterexample-draw-and-note-ordering context)
  (counterexample-custom-redaction context)
  (counterexample-oversize-rejection context)
  (counterexample-renderer-error-safety context)
  (counterexample-cleanup-order context)
  (counterexample-checkpoint-rollback context))
