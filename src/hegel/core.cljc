(ns hegel.core
  "The property-test run loop and dynamic test-case API."
  (:require [clojure.string :as str]
            [hegel.ffi :as hffi]
            [hegel.host :as host]))

(def ^:dynamic *test-case*
  "The test case currently being executed by run-test!."
  nil)

#?(:jank
   ;; jank does not implement records yet. Test cases are intentionally used
   ;; through associative lookup, so a plain map preserves the public behavior
   ;; without pushing a host distinction into generators or stateful testing.
   (defn ->TestCase [context handle final? verbosity]
     {:context context
      :handle handle
      :final? final?
      :verbosity verbosity})
   :default
   (defrecord TestCase [context handle final? verbosity]))

(defn register-native-cleanup!
  "Register a no-argument native cleanup for the active test case.

  This is an integration seam for engine-owned objects whose public API does
  not expose manual lifetime management. Cleanups run once in reverse creation
  order before the test-case handle is released."
  [test-case cleanup]
  (when-not (and test-case (fn? cleanup) (:native-cleanups test-case))
    (throw
     (ex-info "native cleanup requires a test case and function"
              {:type ::invalid-native-cleanup})))
  (swap! (:native-cleanups test-case) conj cleanup)
  nil)

(defn- release-test-case! [test-case]
  (let [first-error (atom nil)]
    (doseq [cleanup (reverse @(:native-cleanups test-case))]
      (host/try-catch-all
       (cleanup)
       error
       (when-not @first-error
         (reset! first-error error))))
    (hffi/test-case-free! (:context test-case) (:handle test-case))
    (when-let [error @first-error]
      (throw error))))

(defn- test-case [ctx handle final? verbosity]
  (assoc (->TestCase ctx handle final? verbosity)
         :native-cleanups (atom [])))

(def ^:private max-observed-failure-origins 16)
(def ^:private max-int64 9223372036854775807)

(def ^:private mode-values
  {:test-run 0
   :single-test-case 1})

(def ^:private backend-values
  {:auto 0
   :default 1
   :urandom 2})

(def ^:private verbosity-values
  {:quiet 0
   :normal 1
   :verbose 2
   :debug 3})

(def ^:private phase-values
  {:explicit 1
   :reuse 2
   :generate 4
   :target 8
   :shrink 16})

(def ^:private health-check-values
  {:filter-too-much 1
   :too-slow 2
   :test-cases-too-large 4
   :large-initial-test-case 8})

(defn- deterministic-seed [opts]
  ;; Clojure's collection/string hash is stable across processes. Mix two
  ;; independently hashed strings into a non-negative 63-bit seed so named
  ;; tests remain distinct when :derandomize? is requested.
  (let [key (str (or (:database-key opts) (:name opts) "jolt-hegel"))
        high (bit-and 0xffffffff (hash key))
        low (bit-and 0xffffffff (hash (str key "\u0000seed")))]
    (bit-and max-int64
             (bit-or (bit-shift-left high 32) low))))

(defn- fresh-seed []
  ;; Jolt does not currently provide java.util.Random, but its core rand source,
  ;; monotonic clock, and wall clock together give us a fresh, replayable seed.
  (bit-and max-int64
           (bit-xor (host/nano-time)
                    (host/current-time-millis)
                    (rand-int 2147483647))))

(defn- resolve-seed [opts]
  (if (some? (:seed opts))
    (:seed opts)
    (if (:derandomize? opts)
      (deterministic-seed opts)
      (fresh-seed))))

(defn- enum-value! [kind values value]
  (if (contains? values value)
    (get values value)
    (throw
     (ex-info (str "unknown " (name kind) " " (pr-str value))
              {:type ::invalid-option
               :option kind
               :value value
               :allowed (vec (keys values))}))))

(defn- enum-mask! [kind values selected]
  (reduce bit-or 0 (map #(enum-value! kind values %) selected)))

(defn current-test-case!
  "Return the active TestCase, or throw when called outside run-test!."
  []
  (or *test-case*
      (throw
       (ex-info "no Hegel test case is currently bound"
                {:type ::no-test-case}))))

(defn final?
  "True only while replaying a minimal failing example."
  []
  (boolean (:final? (current-test-case!))))

(defmacro when-final
  "Evaluate body only during the final replay of a failing example."
  [& body]
  `(when (final?)
     ~@body))

(defmacro fprn
  "Print values to stderr only during the final replay."
  [& values]
  `(when-final
     (binding [*out* *err*]
       (prn ~@values))))

(defn- should-log? [test-case]
  (case (:verbosity test-case)
    :quiet false
    :normal (:final? test-case)
    :verbose true
    :debug true
    (:final? test-case)))

(defn note!
  "Print a diagnostic on final replay (or every case at verbose/debug levels)."
  [message & more]
  (let [test-case (current-test-case!)]
    (when (should-log? test-case)
      (binding [*out* *err*]
        (println (str/join " " (map str (cons message more)))))))
  nil)

(defn draw!
  "Draw a value from a generator function `(fn [test-case] value)`."
  ([generator]
   (generator (current-test-case!)))
  ([generator label]
   (let [test-case (current-test-case!)
         value (generator test-case)]
     (when (should-log? test-case)
       (binding [*out* *err*]
         (prn label value)))
     value)))

(defn assume!
  "Reject the current test case unless condition is truthy."
  ([condition]
   (assume! (current-test-case!) condition))
  ([test-case condition]
   (when-not test-case
     (throw (ex-info "assume! requires a test case"
                     {:type ::no-test-case})))
   (when-not condition
     (throw (ex-info "Hegel assumption rejected"
                     {:type ::assumption-rejected})))
   nil))

(defn target!
  "Report a finite score for Hegel's targeting phase."
  ([value]
   (target! value ""))
  ([value label]
   (let [test-case (current-test-case!)]
     (hffi/target! (:context test-case)
                   (:handle test-case)
                   (double value)
                   (if (string? label) label (pr-str label))))))

(defn- exception-type-name [error]
  #?(:jank (str (or (:type (ex-data error)) :jank/error))
     :default (str (class error))))

(defn- exception-origin [error]
  ;; :hegel/origin is reserved for integrations such as hegel.clojure-test,
  ;; which can supply a stable assertion-site origin. The fallback mirrors the
  ;; C++ binding: exception class, never exception message or drawn data.
  (or (:hegel/origin (ex-data error))
      (exception-type-name error)))

(defn- usage-error? [error]
  (true? (:hegel/usage-error? (ex-data error))))

(defn- run-body [test-case case-fn]
  (host/try-catch-all
   {:status :valid
    :value (binding [*test-case* test-case]
             (case-fn test-case))}
   error
   (cond
     (hffi/stop-test? error)
     {:status :overrun}

     (or (hffi/assumption-rejected? error)
         (= ::assumption-rejected (:type (ex-data error))))
     {:status :invalid}

     (or (usage-error? error)
         (hffi/error? error))
     (throw error)

     :else
     {:status :interesting
      :origin (exception-origin error)
      :exception error})))

(defn- native-status [status]
  (case status
    :valid hffi/status-valid
    :invalid hffi/status-invalid
    :overrun hffi/status-overrun
    :interesting hffi/status-interesting))

(defn- mark-outcome! [test-case outcome]
  (hffi/mark-complete! (:context test-case)
                       (:handle test-case)
                       (native-status (:status outcome))
                       (:origin outcome)))

(defn- count-outcome [counts outcome]
  (-> counts
      (update :test-cases inc)
      (update (case (:status outcome)
                :valid :valid-test-cases
                :invalid :invalid-test-cases
                :overrun :overrun-test-cases
                :interesting :interesting-test-cases)
              inc)))

(defn- throwable-observation [error]
  {:type (exception-type-name error)
   :message (ex-message error)
   :data (ex-data error)})

(defn- observation-index [observed origin]
  (first
   (keep-indexed (fn [index observation]
                   (when (= origin (:origin observation))
                     index))
                 observed)))

(defn- record-observed-failure [observed outcome]
  (if (not= :interesting (:status outcome))
    observed
    (let [origin (:origin outcome)
          details (throwable-observation (:exception outcome))]
      (if-let [index (observation-index observed origin)]
        (-> observed
            (update-in [index :count] inc)
            (assoc-in [index :last] details))
        (if (< (count observed) max-observed-failure-origins)
          (conj observed
                {:origin origin
                 :count 1
                 :first details
                 :last details})
          observed)))))

(defn- configure-settings! [ctx settings opts]
  (when (contains? opts :mode)
    (hffi/settings-set-mode!
     ctx settings (enum-value! :mode mode-values (:mode opts))))
  (when (contains? opts :backend)
    (hffi/settings-set-backend!
     ctx settings (enum-value! :backend backend-values (:backend opts))))
  (when (contains? opts :test-cases)
    (hffi/settings-set-test-cases! ctx settings (:test-cases opts)))
  (when (contains? opts :stateful-step-count)
    (let [value (:stateful-step-count opts)]
      (when-not (and (integer? value) (pos? value))
        (throw
         (ex-info ":stateful-step-count must be a positive integer"
                  {:type ::invalid-option
                   :option :stateful-step-count
                   :value value})))
      (hffi/settings-set-stateful-step-count! ctx settings value)))
  (when (contains? opts :verbosity)
    (hffi/settings-set-verbosity!
     ctx settings
     (enum-value! :verbosity verbosity-values (:verbosity opts))))
  ;; run-test! always resolves a seed before reaching this function. Passing it
  ;; explicitly lets callers replay every run, including one started without a
  ;; :seed option.
  (hffi/settings-set-seed! ctx settings (:seed opts) true)
  (when (contains? opts :derandomize?)
    (hffi/settings-set-derandomize!
     ctx settings (:derandomize? opts)))
  (when (contains? opts :report-multiple-failures?)
    (hffi/settings-set-report-multiple-failures!
     ctx settings (:report-multiple-failures? opts)))
  (when (contains? opts :database)
    (hffi/settings-set-database! ctx settings (:database opts)))
  (when (or (contains? opts :database-key)
            (contains? opts :name))
    (hffi/settings-set-database-key!
     ctx settings (or (:database-key opts) (:name opts))))
  (when (contains? opts :phases)
    (hffi/settings-set-phases!
     ctx settings (enum-mask! :phase phase-values (:phases opts))))
  (when (contains? opts :suppress-health-checks)
    (hffi/settings-set-suppress-health-check!
     ctx settings
     (enum-mask! :health-check
                 health-check-values
                 (:suppress-health-checks opts))))
  nil)

(defn- drive-run! [ctx run verbosity case-fn]
  (loop [counts {:test-cases 0
                 :valid-test-cases 0
                 :invalid-test-cases 0
                 :overrun-test-cases 0
                 :interesting-test-cases 0}
         observed []]
    (if-let [handle (hffi/next-test-case! ctx run)]
      (let [test-case (test-case ctx handle false verbosity)
            {next-counts :counts next-observed :observed}
            (try
              (let [outcome (run-body test-case case-fn)]
                (mark-outcome! test-case outcome)
                {:counts (count-outcome counts outcome)
                 :observed (record-observed-failure observed outcome)})
              (finally
                (release-test-case! test-case)))]
        (recur next-counts next-observed))
      (assoc counts :observed-failures observed))))

(defn- snapshot-failure! [ctx result index]
  (let [failure (hffi/run-result-failure! ctx result index)]
    (try
      {:origin (hffi/failure-origin! ctx failure)
       :reproduction-blob (hffi/failure-reproduction-blob! ctx failure)}
      (finally
        (hffi/failure-free! ctx failure)))))

(defn- snapshot-failures! [ctx result]
  (let [n (hffi/run-result-failure-count! ctx result)]
    (mapv #(snapshot-failure! ctx result %) (range n))))

(defn- replay-failure! [ctx settings verbosity case-fn failure]
  (if-let [blob (:reproduction-blob failure)]
    (let [handle (hffi/test-case-from-blob! ctx settings blob)
          test-case (test-case ctx handle true verbosity)]
      (try
        (let [outcome (run-body test-case case-fn)
              expected-origin (:origin failure)
              replay-origin (:origin outcome)]
          (mark-outcome! test-case outcome)
          (merge failure
                 outcome
                 {:origin expected-origin
                  :replay-origin replay-origin
                  :reproduced? (and (= :interesting (:status outcome))
                                    (= expected-origin replay-origin))}))
        (finally
          (release-test-case! test-case))))
    (assoc failure
           :status :missing-reproduction-blob
           :reproduced? false)))

(defn- run-status [native]
  (case native
    0 :passed
    1 :failed
    2 :error
    3 :failed-nondeterministic
    (throw (ex-info (str "unknown libhegel run status " native)
                    {:type ::unknown-run-status
                     :status native}))))

(defn- nondeterministic-run-error? [message]
  (and (string? message)
       (or (str/starts-with? message "Flaky test detected:")
           (str/starts-with?
            message
            "Your data generation is non-deterministic:"))))

(defn- public-final [replayed]
  (mapv #(select-keys % [:status :value :origin :replay-origin :exception])
        replayed))

(defn ^{:jolt.aspects/id :hegel.core/run-test
        :jolt.aspects/role :test/property-run}
  run-test!
  "Run `case-fn` under libhegel and return an aggregate result map.

  The function receives a TestCase and also runs with `*test-case*` bound, so
  generators can be drawn with `draw!`. Property failures are ordinary thrown
  exceptions. Use a stable `:hegel/origin` in ex-data when distinct assertion
  sites need distinct failure identities.

  Supported options are :mode, :backend, :test-cases, :stateful-step-count,
  :verbosity, :seed,
  :derandomize?, :report-multiple-failures?, :database, :database-key/:name,
  :phases, and :suppress-health-checks. The C ABI does not expose an
  automatically chosen seed, so this wrapper always chooses and supplies one.
  When :derandomize? is true and no seed was supplied, it derives a stable seed
  from :database-key/:name. Property verdicts and libhegel-detected
  nondeterminism return result maps; the latter has :status :error, :flaky?
  true, and an :error explanation. Setup, health-check, and unexpected engine
  errors throw."
  [opts case-fn]
  (hffi/ensure-compatible-version!)
  (let [opts (assoc opts :seed (resolve-seed opts))
        ctx (hffi/context-new!)]
    (try
      (let [settings (hffi/settings-new! ctx)]
        (try
          (configure-settings! ctx settings opts)
          (let [run (hffi/run-start! ctx settings)]
            (try
              (let [counts (drive-run! ctx run (or (:verbosity opts) :normal)
                                       case-fn)
                    result (hffi/run-result! ctx run)]
                (try
                  (let [status (run-status (hffi/run-result-status! ctx result))
                        run-error (when (= :error status)
                                    (or (hffi/run-result-error! ctx result)
                                        "unknown error"))]
                    (when (= :failed-nondeterministic status)
                      (throw
                       (ex-info
                        (str "libhegel reported a concurrent state-machine "
                             "failure, but jolt-hegel exposes only the "
                             "sequential state-machine protocol")
                        {:type ::unsupported-concurrent-state-machine
                         :seed (str (:seed opts))})))
                    (if (nondeterministic-run-error? run-error)
                      ;; libhegel reports nondeterminism as a run-level error,
                      ;; before a counterexample is available to replay. Keep
                      ;; that distinct from replay-time flakiness while still
                      ;; returning data that a counting runner can record.
                      (merge
                       counts
                       {:passed? false
                        :status :error
                        :seed (str (:seed opts))
                        :flaky? true
                        :health-check-failure? nil
                        :error run-error
                        :n-failures 0
                        :failures []
                        :final []})
                      (do
                        (when run-error
                          (throw
                           (ex-info
                            (str "Hegel run error: " run-error)
                            {:type ::run-error
                             :seed (str (:seed opts))})))
                        (let [failures (if (= :failed status)
                                         (snapshot-failures! ctx result)
                                         [])
                              replayed (mapv #(replay-failure!
                                              ctx settings
                                              (or (:verbosity opts) :normal)
                                              case-fn %)
                                             failures)]
                          (merge
                           counts
                           {:passed? (= :passed status)
                            :status status
                            :seed (str (:seed opts))
                            :flaky?
                            (boolean
                             (some (comp not :reproduced?) replayed))
                            :health-check-failure? nil
                            :error nil
                            :n-failures (count failures)
                            :failures replayed
                            :final (public-final replayed)})))))
                  (finally
                    (hffi/run-result-free! ctx result))))
              (finally
                (hffi/run-free! ctx run))))
          (finally
            (hffi/settings-free! ctx settings))))
      (finally
        (hffi/context-free! ctx)))))

(def test-fn!
  "Compatibility name for run-test!."
  run-test!)

(defmacro test!
  "Macro form of run-test! for an inline property body."
  [opts & body]
  `(run-test! ~opts (fn [~'_] ~@body)))

(defn sample
  "Generate up to n values for interactive inspection."
  [n generator]
  (let [values (atom [])]
    (run-test! {:test-cases n
                :database ""
                :report-multiple-failures? false
                :verbosity :quiet}
               (fn [_]
                 (swap! values conj (draw! generator))))
    @values))
