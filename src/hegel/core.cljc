(ns hegel.core
  "The property-test run loop and dynamic test-case API."
  (:require [clojure.string :as str]
            [hegel.ffi :as hffi]
            [hegel.host :as host]
            [hegel.internal.observation-policy :as observation-policy]
            [hegel.internal.observations :as observations]
            [hegel.internal.render :as render]
            [hegel.replay-bundle :as replay-bundle]
            [hegel.validation :as validation]
            [hegel.version :as version]))

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

(defn- should-log? [verbosity final?]
  (case verbosity
    :quiet false
    :normal final?
    :verbose true
    :debug true
    final?))

(defn- observed-test-case [ctx handle final? verbosity run-observations render-opts]
  (let [render-state (render/start! ctx handle (should-log? verbosity final?) render-opts)
        tc (cond-> (test-case ctx handle final? verbosity)
             run-observations (assoc :observations (atom (observations/empty-case)))
             true (assoc :render render-state))]
    (when (render/printer-handle render-state)
      (register-native-cleanup!
       tc #(hffi/printer-free! ctx (render/printer-handle render-state))))
    tc))

(defn- record-observations! [run-observations test-case outcome]
  (when run-observations
    (swap! run-observations update
           (if (:final? test-case) :final-replay :exploration)
           observations/record-case (:status outcome) @(:observations test-case))))

(def ^:private max-observed-failure-origins 16)
(def ^:private max-int64 9223372036854775807)
(def ^:private max-uint64 18446744073709551615N)

(def ^:private backend-values
  {:default 1
   :urandom 2})

(def ^:private verbosity-values
  {:quiet 1
   :normal 0
   :verbose 2
   :debug 3})

(def ^:private nondeterminism-values {:quiet 0 :warn 1 :error 2})

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
    (validation/usage-error!
     ::invalid-option
     (str "unknown " (name kind) " " (pr-str value))
     {:option kind :value value :allowed (vec (keys values))})))

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
  "True for an engine-stamped capture attempt, not necessarily the last attempt.
  The engine may request several confirmations; only the freshest failing
  capture per reported origin is retained in the result."
  []
  (boolean (:final? (current-test-case!))))

(defmacro when-final
  "Evaluate diagnostics during engine-stamped capture attempts."
  [& body]
  `(when (final?)
     ~@body))

(defmacro fprn
  "Record values for the deferred failure diagnostic."
  [& values]
  `(when-final
     (note! (str/join " " (map pr-str [~@values])))))

(defn note!
  "Record a diagnostic into the active render state. Normal output selects
  the freshest failure capture; verbose/debug output includes every case."
  [message & more]
  (render/record-note! (:render (current-test-case!)) message more)
  nil)

(defn draw!
  "Draw a value from a generator function `(fn [test-case] value)`."
  ([generator]
   (when-not (fn? generator)
     (validation/usage-error! ::invalid-generator
                              "draw! requires a generator function"
                              {:generator generator}))
   (generator (current-test-case!)))
  ([generator label]
   (when-not (fn? generator)
     (validation/usage-error! ::invalid-generator
                              "draw! requires a generator function"
                              {:generator generator}))
   (let [test-case (current-test-case!)
         value (generator test-case)]
     (render/record-draw! (:render test-case) label value)
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

(defn event!
  "Record a categorical observation under a stable string label.

  Repeated occurrences count once per case. Native :show-statistics? output
  and opt-in :observations? / :coverage result data are separate facilities."
  [label]
  (let [test-case (current-test-case!)
        label (observation-policy/label! label)
        data (:observations test-case)
        next-data (when data (observations/event @data label))]
    ;; Validate capacity before native work, but publish the frontend event
    ;; only after the native call succeeds. Caught native errors are not hits.
    (hffi/event! (:context test-case) (:handle test-case) label)
    (when data (reset! data next-data)))
  nil)

(defn observe!
  "Record a finite numeric observation under a stable string label.

  Repeated observations are retained as count/min/max, not raw samples, in
  opt-in frontend data. This does not guide targeting; use target! for that."
  [value label]
  (let [test-case (current-test-case!)
        label (observation-policy/label! label)
        value (observation-policy/finite-value! value)
        data (:observations test-case)
        next-data (when data (observations/observe @data label value))]
    (hffi/event-value! (:context test-case) (:handle test-case) value label)
    (when data (reset! data next-data)))
  nil)

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

(def ^:private run-option-keys
  #{:mode :backend :test-cases :stateful-step-count :verbosity :seed
    :derandomize? :report-multiple-failures? :database :database-key :name
    :phases :suppress-health-checks :show-statistics? :observations? :coverage
    :counterexample :profile :nondeterminism-strictness :unbounded-choices?
    :print-blob? :test-location})

(defn- require-string! [option value]
  (when-not (string? value)
    (validation/usage-error!
     ::invalid-option (str (name option) " must be a string") {option value}))
  value)

(defn- require-option-keywords! [option values allowed]
  (when-not (and (coll? values) (not (string? values)))
    (validation/usage-error!
     ::invalid-option (str (name option) " must be a collection") {option values}))
  (doseq [value values]
    (enum-value! (if (= option :phases) :phase :health-check) allowed value))
  values)

(defn- require-profile-name! [value]
  (when-not (and (string? value) (re-matches #"[A-Za-z0-9_-]+" value))
    (validation/usage-error! ::invalid-option "profile must be an ASCII profile name" {}))
  value)

(defn- validate-run-options! [opts case-fn]
  (validation/reject-unknown-keys! ::invalid-option "run-test! options"
                                   run-option-keys opts)
  (when-not (fn? case-fn)
    (validation/usage-error! ::invalid-option "run-test! case-fn must be a function"
                             {:case-fn case-fn}))
  (when (contains? opts :mode)
    (validation/usage-error!
     ::removed-mode
     "libhegel removed :mode; omit it for normal runs. :test-cases 1 sets a valid-case budget, not the former no-shrink single-test-case mode."
     {:mode (:mode opts)}))
  (when (contains? opts :backend)
    (enum-value! :backend backend-values (:backend opts)))
  (when (contains? opts :verbosity)
    (enum-value! :verbosity verbosity-values (:verbosity opts)))
  (when (contains? opts :nondeterminism-strictness)
    (enum-value! :nondeterminism-strictness nondeterminism-values
                 (:nondeterminism-strictness opts)))
  (when (contains? opts :profile)
    (require-profile-name! (:profile opts)))
  (when (contains? opts :test-location)
    (let [location (:test-location opts)]
      (validation/reject-unknown-keys! ::invalid-option "test-location"
                                       #{:file :line :class-name :function} location)
      (doseq [key [:file :class-name :function]]
        (when-not (and (string? (get location key))
                       (not (str/includes? (get location key) "\u0000")))
          (validation/usage-error! ::invalid-option "test-location strings must be NUL-free" {})))
      (validation/require-integer-range! ::invalid-option :line (:line location)
                                         0 4294967295)))
  (when (contains? opts :test-cases)
    (validation/require-integer-range! ::invalid-option :test-cases
                                       (:test-cases opts) 1 max-uint64))
  (when (contains? opts :stateful-step-count)
    (validation/require-integer-range! ::invalid-option :stateful-step-count
                                       (:stateful-step-count opts) 1 max-int64))
  (when (contains? opts :seed)
    (validation/require-integer-range! ::invalid-option :seed (:seed opts)
                                       0 max-uint64))
  (doseq [option [:derandomize? :report-multiple-failures?
                  :show-statistics? :observations? :unbounded-choices? :print-blob?]
          :when (contains? opts option)]
    (validation/require-boolean! ::invalid-option option (get opts option)))
  (doseq [option [:database :database-key :name]
          :when (contains? opts option)]
    (require-string! option (get opts option)))
  (when (contains? opts :phases)
    (require-option-keywords! :phases (:phases opts) phase-values))
  (when (contains? opts :suppress-health-checks)
    (require-option-keywords! :suppress-health-checks
                              (:suppress-health-checks opts)
                              health-check-values))
  (observation-policy/validate-coverage! opts)
  ;; Validated before native setup, purely for its usage-error effect: the
  ;; resolved map is recomputed where it is actually needed.
  (render/resolve-options (:counterexample opts))
  opts)

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
         (:hegel/inconclusive? (ex-data error))
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
  (when (contains? opts :backend)
    (hffi/settings-set-backend!
     ctx settings (enum-value! :backend backend-values (:backend opts))))
  (when (contains? opts :test-cases)
    (hffi/settings-set-test-cases! ctx settings (:test-cases opts)))
  (when (contains? opts :nondeterminism-strictness)
    (hffi/settings-set-nondeterminism-strictness!
     ctx settings (get nondeterminism-values (:nondeterminism-strictness opts))))
  (when (contains? opts :unbounded-choices?)
    (hffi/settings-set-unbounded-choices! ctx settings (:unbounded-choices? opts)))
  (when (contains? opts :print-blob?)
    (hffi/settings-set-print-blob! ctx settings (:print-blob? opts)))
  (when (contains? opts :test-location)
    (hffi/settings-set-test-location! ctx settings (:test-location opts)))
  (when (contains? opts :verbosity)
    (hffi/settings-set-verbosity!
     ctx settings
     (enum-value! :verbosity verbosity-values (:verbosity opts))))
  ;; Explicit seeds override profiles/environment. run-test! later records and
  ;; installs an effective seed, including when the caller leaves it unset.
  (when (contains? opts :seed)
    (hffi/settings-set-seed! ctx settings (:seed opts) true))
  (when (contains? opts :derandomize?)
    (hffi/settings-set-derandomize!
     ctx settings (:derandomize? opts)))
  (when (contains? opts :report-multiple-failures?)
    (hffi/settings-set-report-multiple-failures!
     ctx settings (:report-multiple-failures? opts)))
  (when (contains? opts :show-statistics?)
    (hffi/settings-set-show-statistics! ctx settings (:show-statistics? opts)))
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

(defn- drive-run! [ctx run verbosity case-fn run-observations render-opts step-count]
  (loop [counts {:test-cases 0 :valid-test-cases 0 :invalid-test-cases 0
                 :overrun-test-cases 0 :interesting-test-cases 0}
         observed [] captures {}]
    (if-let [handle (hffi/next-test-case! ctx run)]
      (let [capture? (hffi/test-case-should-capture? ctx handle)
            test-case (assoc (observed-test-case ctx handle capture? verbosity
                                                run-observations render-opts)
                             :stateful-step-count step-count
                             :assertion-reports (atom []))
            next-result
            (try
              (let [outcome (run-body test-case case-fn)]
                (mark-outcome! test-case outcome)
                (let [snapshot (render/finish! (:render test-case))
                      capture (assoc outcome :counterexample snapshot
                                             :reports @(:assertion-reports test-case)
                                             :observations (when (:observations test-case)
                                                             @(:observations test-case)))]
                  (when (#{:verbose :debug} verbosity) (render/emit! snapshot))
                  ;; Stamped confirmations cannot supply exploration coverage.
                  (when-not capture? (record-observations! run-observations test-case outcome))
                  {:counts (count-outcome counts outcome)
                   :observed (record-observed-failure observed outcome)
                   :captures (if (and capture? (= :interesting (:status outcome))
                                      (or (contains? captures (:origin outcome))
                                          (< (count captures) max-observed-failure-origins)))
                               (assoc captures (:origin outcome) capture)
                               captures)}))
              (finally (release-test-case! test-case)))]
        (recur (:counts next-result) (:observed next-result) (:captures next-result)))
      (assoc counts :observed-failures observed :captures captures))))

(defn- captured-failures [counts failures run-observations verbosity]
  (mapv
   (fn [failure]
     (if-let [capture (get (:captures counts) (:origin failure))]
       (do
         (when-not (#{:quiet :verbose :debug} verbosity)
           (render/emit! (:counterexample capture)))
         (when run-observations
           (swap! run-observations update :final-replay
                  observations/record-case :interesting (:observations capture)))
         (merge failure (dissoc capture :observations)
                {:replay-origin (:origin capture)
                 :reproduced? (nil? (:caveat failure))}))
       (assoc failure :status :missing-capture :reproduced? false
                      :counterexample nil)))
   failures))

(defn- enum-key! [option values value]
  (or (first (keep (fn [[key code]] (when (= code value) key)) values))
      (throw (ex-info "unknown resolved libhegel setting"
                      {:type ::invalid-native-settings :option option :value value}))))

(defn- native-options! [ctx settings]
  (let [native (hffi/settings-snapshot! ctx settings)]
    (-> native
        (update :backend #(enum-key! :backend backend-values %))
        (update :verbosity #(enum-key! :verbosity verbosity-values %))
        (update :nondeterminism-strictness
                #(enum-key! :nondeterminism-strictness nondeterminism-values %))
        (update :phases (fn [mask] (vec (for [[key code] phase-values
                                            :when (not (zero? (bit-and mask code)))] key))))
        (update :suppress-health-checks
                (fn [mask] (vec (for [[key code] health-check-values
                                     :when (not (zero? (bit-and mask code)))] key)))))))

(defn resolved-options
  "Return effective profile/environment settings with explicit options applied.
  This loads the verified native engine; it does not start a property run."
  ([] (resolved-options {}))
  ([opts]
   (validate-run-options! opts (fn [_]))
   (hffi/ensure-compatible-version!)
   (let [ctx (hffi/context-new!)]
     (try
       (let [settings (if-let [profile (:profile opts)]
                        (hffi/settings-new! ctx profile) (hffi/settings-new! ctx))]
         (try
           (configure-settings! ctx settings opts)
           (merge opts (native-options! ctx settings)
                  {:stateful-step-count (get opts :stateful-step-count 50)})
           (finally (hffi/settings-free! ctx settings))))
       (finally (hffi/context-free! ctx))))))

(defn register-profile!
  "Register a process-wide settings snapshot. Existing handles are unchanged.
  Options layer over :profile (default base); database keys/locations are not
  part of the native snapshot."
  [name opts]
  (require-profile-name! name)
  (validate-run-options! opts (fn [_]))
  (hffi/ensure-compatible-version!)
  (let [ctx (hffi/context-new!)]
    (try
      (let [settings (hffi/settings-new! ctx (get opts :profile "base"))]
        (try
          (configure-settings! ctx settings opts)
          (hffi/settings-register-profile! ctx name settings)
          (finally (hffi/settings-free! ctx settings))))
      (finally (hffi/context-free! ctx))))
  nil)

(defn set-default-profile!
  "Set the process-wide default profile; nil clears this override."
  [name]
  (when (some? name) (require-profile-name! name))
  (hffi/ensure-compatible-version!)
  (let [ctx (hffi/context-new!)]
    (try (hffi/set-default-profile! ctx name)
         (finally (hffi/context-free! ctx))))
  nil)

(defn- snapshot-failure! [ctx result index]
  (let [failure (hffi/run-result-failure! ctx result index)]
    (try
      {:origin (hffi/failure-origin! ctx failure)
       :reproduction-blob (hffi/failure-reproduction-blob! ctx failure)
       :caveat (hffi/failure-caveat! ctx failure)}
      (finally
        (hffi/failure-free! ctx failure)))))

(defn- snapshot-failures! [ctx result]
  (let [n (hffi/run-result-failure-count! ctx result)]
    (mapv #(snapshot-failure! ctx result %) (range n))))


(defn- run-status [native]
  (case native
    0 :passed
    1 :failed
    2 :error
    (throw (ex-info (str "unknown libhegel run status " native)
                    {:type ::unknown-run-status
                     :status native}))))

(defn- nondeterministic-run-error? [message]
  (and (string? message)
       (or (str/starts-with? message "Flaky test detected:")
           (str/starts-with? message "Your test is non-deterministic:")
           (str/starts-with?
            message
            "Your data generation is non-deterministic:"))))

(defn- public-final [replayed]
  (mapv #(select-keys % [:status :value :origin :replay-origin :exception
                         :counterexample :reports :caveat])
        replayed))

(defn- capture-replay-options [opts]
  ;; These are already validated run options. Capture without applying export
  ;; bounds: a valid ordinary run must not fail merely because its eventual
  ;; artifact would exceed the deliberately smaller transport limits.
  (let [options (select-keys opts
                             [:backend :test-cases :stateful-step-count
                              :verbosity :derandomize? :report-multiple-failures?
                              :phases :suppress-health-checks
                              :nondeterminism-strictness :unbounded-choices?])]
    (cond-> options
      (contains? options :phases) (update :phases vec)
      (contains? options :suppress-health-checks)
      (update :suppress-health-checks vec))))

(defn- execute-run! [ctx run opts case-fn run-observations render-opts]
  (try
    (let [counts (drive-run! ctx run (:verbosity opts) case-fn run-observations
                            render-opts (:stateful-step-count opts))
          result (hffi/run-result! ctx run)]
      (try
        (let [status (run-status (hffi/run-result-status! ctx result))
              error (when (= :error status)
                      (or (hffi/run-result-error! ctx result) "unknown error"))]
          (when (and error (not (nondeterministic-run-error? error)))
            (throw (ex-info (str "Hegel run error: " error)
                            {:type ::run-error :seed (str (:seed opts))})))
          (let [failures (if (= :failed status) (snapshot-failures! ctx result) [])
                captured (captured-failures counts failures run-observations (:verbosity opts))]
            (observation-policy/finish
             (merge (dissoc counts :captures)
                    {:passed? (= :passed status) :status status
                     :seed (str (:seed opts))
                     :replay-options (capture-replay-options opts)
                     :flaky? (boolean (or error (some (comp not :reproduced?) captured)))
                     :health-check-failure? nil :error error
                     :n-failures (count captured) :failures captured
                     :final (public-final captured)})
             opts (when run-observations @run-observations))))
        (finally (hffi/run-result-free! ctx result))))
    (finally (hffi/run-free! ctx run))))

(defn replay-bundle!
  "Replay trusted failure blobs as bounded engine runs, never seed generation.
  Compare independently supplied provenance before allocation. Caveated
  failures remain untrusted; a stale or changed origin does not reproduce."
  [expected-provenance bundle case-fn]
  (let [{:keys [mismatches]} (replay-bundle/compatibility expected-provenance bundle)
        mismatches
        (cond-> (mapv #(assoc % :source :bundle) mismatches)
          (not= (host/runtime) (get-in expected-provenance [:runtime :host]))
          (conj {:path [:runtime :host] :source :runtime
                 :expected (get-in expected-provenance [:runtime :host]) :actual (host/runtime)})
          (not= version/libhegel-version (:libhegel-version expected-provenance))
          (conj {:path [:libhegel-version] :source :native-binding
                 :expected (:libhegel-version expected-provenance)
                 :actual version/libhegel-version}))
        opts (assoc (:options bundle) :seed (bigint (:seed bundle)) :database ""
                                     :print-blob? false)]
    (if (seq mismatches)
      {:status :incompatible :reproduced? false :mismatches mismatches}
      (do
        (validate-run-options! opts case-fn)
        (hffi/ensure-compatible-version!)
        (let [ctx (hffi/context-new!)]
          (try
            (let [settings (hffi/settings-new! ctx "base")]
              (try
                (configure-settings! ctx settings opts)
                (let [opts (merge opts (native-options! ctx settings)
                                  {:stateful-step-count (get opts :stateful-step-count 50)})
                      render-opts (render/resolve-options nil)
                      failures
                      (mapv
                       (fn [failure]
                         (let [result (execute-run! ctx
                                                    (hffi/run-start-blob! ctx settings
                                                                          (:reproduction-blob failure))
                                                    opts case-fn nil render-opts)
                               matching (first (filter #(= (:origin failure) (:origin %))
                                                       (:failures result)))]
                           (if matching
                             (assoc matching :reproduction-blob (:reproduction-blob failure))
                             (assoc failure :status :not-reproduced :reproduced? false
                                            :counterexample nil :error (:error result)))))
                       (:failures bundle))
                      reproduced? (every? :reproduced? failures)]
                  {:status (if reproduced? :reproduced :not-reproduced)
                   :reproduced? reproduced? :flaky? (not reproduced?)
                   :seed (:seed bundle) :replay-options (capture-replay-options opts)
                   :n-failures (count failures) :failures failures :final (public-final failures)})
                (finally (hffi/settings-free! ctx settings))))
            (finally (hffi/context-free! ctx))))))))

(defn ^{:jolt.aspects/id :hegel.core/run-test
        :jolt.aspects/role :test/property-run}
  run-test!
  "Run a property under the verified libhegel engine.
  Profile and environment defaults are resolved by the engine; explicit
  options win. State-machine budgets are frontend defaults or per-machine
  overrides. Failures retain the engine's freshest capture and :caveat;
  any caveated or missing capture is :flaky? and cannot become a stable
  replay bundle. Coverage excludes stamped confirmation attempts.
  Setup, native and inconclusive errors propagate with resource cleanup."
  [opts case-fn]
  (validate-run-options! opts case-fn)
  (hffi/ensure-compatible-version!)
  (let [ctx (hffi/context-new!)]
    (try
      (let [settings (if-let [profile (:profile opts)]
                       (hffi/settings-new! ctx profile) (hffi/settings-new! ctx))]
        (try
          (configure-settings! ctx settings opts)
          (let [opts (merge opts (native-options! ctx settings))
                opts (assoc opts :seed (resolve-seed opts)
                                 :stateful-step-count (get opts :stateful-step-count 50))
                render-opts (render/resolve-options (:counterexample opts))
                run-observations (some-> (observation-policy/initial opts) atom)]
            (hffi/settings-set-seed! ctx settings (:seed opts) true)
            (execute-run! ctx (hffi/run-start! ctx settings) opts case-fn
                          run-observations render-opts))
          (finally (hffi/settings-free! ctx settings))))
      (finally (hffi/context-free! ctx)))))

(def test-fn!
  "Compatibility name for run-test!."
  run-test!)

(defmacro test!
  "Macro form of run-test! for an inline property body."
  [opts & body]
  `(run-test! ~opts (fn [~'_] ~@body)))

(defn- sample-failure! [run]
  ;; A failed property is normally returned by run-test!, rather than thrown.
  ;; Keep both the complete run and a portable summary of its original cause so
  ;; interactive callers never mistake a partial sample for a successful one.
  (let [cause (or (-> run :final first :exception)
                  (-> run :failures first :exception))]
    (throw
     (ex-info
      "Hegel sample failed"
      {:type ::sample-failed
       :run run
       :cause (when cause (throwable-observation cause))}
      cause))))

(defn sample
  "Generate up to n values for interactive inspection.

  Throws with the complete run result and original cause data when the
  underlying property fails or is flaky; it never returns a partial sample as
  a successful result."
  [n generator]
  (when-not (and (integer? n) (pos? n))
    (validation/usage-error! ::invalid-sample
                             "sample count must be a positive integer" {:n n}))
  (when-not (fn? generator)
    (validation/usage-error! ::invalid-sample
                             "sample requires a generator function"
                             {:generator generator}))
  (let [values (atom [])
        run (run-test! {:test-cases n
                        :database ""
                        :report-multiple-failures? false
                        :verbosity :quiet}
                       (fn [_]
                         (swap! values conj (draw! generator))))]
    (if (and (:passed? run) (not (:flaky? run)))
      @values
      (sample-failure! run))))
