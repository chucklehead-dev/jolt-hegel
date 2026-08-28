(ns hegel.test-runner
  (:require [clojure.string :as str]
            [clojure.test :as t]
            [hegel.clojure-test :as ht]
            [hegel.core :as h]
            [hegel.ffi :as hffi]
            [hegel.generator :as g]
            [hegel.host :as host]
            [hegel.install :as install]
            [hegel.malli :as hm]
            [hegel.native :as native]
            [hegel.report :as report]
            [hegel.stateful :as hs]
            [hegel.trace :as htrace]
            [hegel.version :as version]
            [malli.core :as m]))

(def failures (atom 0))
(def progress-file
  ;; Jolt 0.7.5's atomic spit path currently treats a Windows drive-rooted
  ;; target as relative and prefixes the launch directory a second time. Keep
  ;; this harness-owned progress file relative on Windows until that runtime
  ;; boundary is fixed; release consumers never use this path.
  (if (= :windows (:os (native/platform)))
    "jolt-hegel-test-progress.log"
    (native/join-path
     (or (native/nonblank-env "RUNNER_TEMP")
         (native/nonblank-env "TMPDIR")
         (native/nonblank-env "TEMP")
         (native/nonblank-env "TMP")
         (System/getProperty "java.io.tmpdir")
         ".")
     "jolt-hegel-test-progress.log")))

(defn reset-progress! []
  ;; jolt's atomic spit uses rename. POSIX rename replaces an existing target,
  ;; but Windows rename does not, so remove this harness-owned file first.
  (let [file (java.io.File. progress-file)]
    (when (and (.exists file) (not (.delete file)))
      (throw (ex-info (str "could not reset test progress file " progress-file)
                      {:path progress-file}))))
  (spit progress-file "jolt-hegel test run\n"))

(defn progress! [message]
  (spit progress-file (str message "\n") :append true))

(defn check [description condition]
  (if condition
    (println "PASS" description)
    (do
      (swap! failures inc)
      (println "FAIL" description))))

(defn scenario [description body]
  (progress! (str "START " description))
  (let [result (deref
                (future
                  (try
                    (body)
                    {:ok? true}
                    (catch Throwable error
                      {:ok? false :error error})))
                60000
                ::timeout)]
    (cond
      (= ::timeout result)
      (do
        (swap! failures inc)
        (println "FAIL" description "timed out"))

      (:ok? result)
      (println "SCENARIO" description "completed")

      :else
      (do
        (swap! failures inc)
        (println "FAIL" description (ex-message (:error result)))))
    (progress! (str "END " description))))

(defn passing-run []
  (let [calls (atom 0)
        result (h/run-test!
                {:test-cases 12
                 :seed 11
                 :database ""
                 :verbosity :quiet}
                (fn [_]
                  (swap! calls inc)))]
    (check "passing property reports passed" (:passed? result))
    ;; A property with no draws exhausts its choice tree after one case; the
    ;; configured count is a maximum, not a promise of duplicate executions.
    (check "passing property accounts for every executed valid case"
           (and (pos? @calls)
                (<= @calls 12)
                (= @calls (:valid-test-cases result))))
    (check "explicit seed is reflected in the result"
           (= "11" (:seed result)))
    (check "passing property has no final replay"
           (empty? (:final result)))))

(defn shrinking-run []
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
    (check "failing property reports failed" (not (:passed? result)))
    (check "one stable origin produces one distinct failure"
           (= 1 (:n-failures result) (count (:failures result))))
    (check "failure origin is stable"
           (= "hegel.test-runner:threshold" (:origin failure)))
    (check "shrinker produces the known minimal reproduction blob"
           (= "AAEAAAAACgIAAAD0AQ==" (:reproduction-blob failure)))
    (check "minimal counterexample is replayed in final phase"
           (= [500] @final-values))
    (check "final replay preserves the minimal drawn value"
           (= 500 (-> final-outcome :exception ex-data :x)))
    (check "final replay reproduced the property failure"
           (:reproduced? failure))
    (check "reproduced failure is not flaky"
           (false? (:flaky? result)))))

(defn engine-nondeterminism []
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
    (check "engine outcome flakiness returns a countable failure result"
           (and (not (:passed? result))
                (= :error (:status result))
                (= "17" (:seed result))
                (true? (:flaky? result))
                (str/starts-with? (:error result) "Flaky test detected:")
                (zero? (:n-failures result))
                (empty? (:failures result))
                (empty? (:final result))))
    (let [observed (first (:observed-failures result))]
      (check "engine flakiness retains the structured observed failure"
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
    (check "non-deterministic generation returns a countable failure result"
           (and (not (:passed? result))
                (= :error (:status result))
                (= "19" (:seed result))
                (true? (:flaky? result))
                (str/starts-with?
                 (:error result)
                 "Your data generation is non-deterministic:")
                (zero? (:n-failures result))))
    (let [observed (first (:observed-failures result))]
      (check "observed failures aggregate repeated stable origins"
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
    (check "non-flakiness engine errors still abort the run"
           (= ::h/run-error (:type (ex-data error))))))

(defn counting-reporting []
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
    (check "counting runner returns normal property results"
           (and (:passed? pass-result)
                (not (:passed? fail-result))
                (nil? error-result)))
    (check "counting runner tracks returned failures and thrown errors"
           (and passed-after-first?
                (= 3 (report/run-count runner))
                (= 2 (report/failure-count runner))
                (not (report/passed? runner))))
    (check "counting runner emits structured continuation events"
           (and (= [:pass :fail :error] (mapv :type @events))
                (= "37" (-> @events second :result :seed))
                (= {:phase :setup}
                   (-> @events (nth 2) :exception ex-data))))))

(defn cleanup-and-version []
  ;; A second run after the failed/replayed run exercises all cleanup paths well
  ;; enough to catch double-free/use-after-free regressions in the basic loop.
  (let [result (h/run-test!
                {:test-cases 3
                 :database ""
                 :verbosity :quiet}
                (fn [_] nil))]
    (check "a new run succeeds after failed-run cleanup" (:passed? result))
    (check "loaded libhegel matches the bound ABI"
           (= hffi/libhegel-version (hffi/version)))))

(defn installer-source-identity []
  (check "installer recognizes POSIX and Windows absolute paths"
         (and (native/absolute-path? "/tmp/jolt-hegel")
              (native/absolute-path? "C:\\src\\jolt-hegel")
              (native/absolute-path? "D:/src/jolt-hegel")
              (not (native/absolute-path? "src/jolt-hegel"))))
  (check "installer verifies the loaded release against current source"
         (= version/jolt-hegel-version
            (install/verify-source-version!)))
  (if (= :jolt (host/runtime))
    (let [error
          (with-redefs [version/jolt-hegel-version "0.0.0-stale"]
            (try
              (install/verify-source-version!)
              nil
              (catch Throwable error
                error)))]
      (check "installer rejects a stale Jolt AOT namespace"
             (and (= ::install/stale-aot-cache (:type (ex-data error)))
                  (= "0.0.0-stale" (:loaded-version (ex-data error)))
                  (= version/jolt-hegel-version
                     (:source-version (ex-data error)))
                  (str/includes? (ex-message error) "JOLT_CACHE_DIR"))))
    (check "installer skips Jolt-only source identity checks on this host"
           (= "0.0.0-stale"
              (with-redefs [version/jolt-hegel-version "0.0.0-stale"]
                (install/verify-source-version!))))))

(defn generated-seed []
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
    (check "a run without :seed returns its generated seed"
           (some? (:seed result)))
    (check "an auto-generated seed can be supplied for exact replay"
           (and (= (:seed result) (:seed replay))
                (= @first-values @replay-values))))
  (let [opts {:test-cases 1
              :derandomize? true
              :name "generated-seed-test"
              :database ""
              :verbosity :quiet}
        first-run (h/run-test! opts (fn [_] nil))
        second-run (h/run-test! opts (fn [_] nil))]
    (check "derandomized runs derive the same known seed"
           (= (:seed first-run) (:seed second-run)))))

(defn controls-and-sample []
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
    (check "assume! classifies a rejected test case as invalid"
           (= 1 (:invalid-test-cases result)))
    (check "an assumption rejection is not a property failure"
           (:passed? result)))
  (let [result (h/run-test!
                {:test-cases 5
                 :seed 31
                 :database ""
                 :verbosity :quiet}
                (fn [_]
                  (let [x (h/draw! (g/integer 0 100))]
                    (h/target! x :drawn-integer))))]
    (check "target! participates in a passing run" (:passed? result)))
  (let [values (h/sample 5 (g/integer 7 7))]
    (check "sample returns generated values"
           (and (seq values)
                (<= (count values) 5)
                (every? #{7} values)))))

(defn- valid-ipv4? [value]
  (let [parts (str/split value #"\.")]
    (and (= 4 (count parts))
         (every? (fn [part]
                   (let [number (parse-long part)]
                     (and (some? number) (<= 0 number 255))))
                 parts))))

(defn- valid-ipv6? [value]
  (let [compressed? (str/includes? value "::")
        first-compression (str/index-of value "::")
        last-compression (str/last-index-of value "::")
        groups (remove empty? (str/split value #":"))]
    (and (not (str/includes? value ":::"))
         (= first-compression last-compression)
         (every? #(some? (re-matches #"[0-9a-f]{1,4}" %)) groups)
         (if compressed?
           (< (count groups) 8)
           (= 8 (count groups))))))

(defn- codepoint-count [value]
  ;; Both Java's regex engine and Jolt's irregex iterate this pattern by Unicode
  ;; code point, unlike JVM String/count which counts UTF-16 code units.
  (count (re-seq #"(?s)." value)))

(defn primitive-generators []
  (let [values (atom {:true [] :false [] :octets [] :doubles [] :bytes []
                      :empty-bytes [] :uuids [] :ipv4 [] :ipv6 []})
        result
        (h/run-test!
         {:test-cases 40
          :seed 20260722
          :database ""
          :verbosity :quiet}
         (fn [_]
           (let [true-value (h/draw! (g/boolean 1.0))
                 false-value (h/draw! (g/boolean 0.0))
                 octet-value (h/draw! (g/octet))
                 double-value (h/draw! (g/double 0.0 1.0))
                 bytes-value (h/draw! (g/bytes 8 8))
                 empty-bytes-value (h/draw! (g/bytes 0 0))
                 uuid-value (h/draw! (g/uuid 4))
                 ipv4-value (h/draw! (g/ipv4))
                 ipv6-value (h/draw! (g/ipv6))]
             (swap! values
                    (fn [seen]
                      (-> seen
                          (update :true conj true-value)
                          (update :false conj false-value)
                          (update :octets conj octet-value)
                          (update :doubles conj double-value)
                          (update :bytes conj bytes-value)
                          (update :empty-bytes conj empty-bytes-value)
                          (update :uuids conj uuid-value)
                          (update :ipv4 conj ipv4-value)
                          (update :ipv6 conj ipv6-value)))))))]
    (check "primitive generator run passes" (:passed? result))
    (check "probability endpoints force booleans"
           (and (seq (:true @values))
                (every? true? (:true @values))
                (every? false? (:false @values))))
    (check "bounded doubles stay in range"
           (every? #(<= 0.0 % 1.0) (:doubles @values)))
    (check "octets are unsigned-comparable integers"
           (and (seq (:octets @values))
                (every? #(and (integer? %) (<= 0 % 255))
                        (:octets @values))))
    (check "fixed-size bytes are copied into jolt byte arrays"
           (and (every? (fn [data]
                          (and (= 8 (alength data))
                               (every? #(<= -128 % 127) (seq data))
                               (every? #(<= 0 (bit-and % 0xff) 255)
                                       (seq data))))
                        (:bytes @values))
                (every? #(zero? (alength %)) (:empty-bytes @values))))
    (check "versioned UUIDs use canonical RFC 4122 text"
           (every? (fn [value]
                     (and (some? (re-matches
                                  #"[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
                                  value))
                          (= value (str (java.util.UUID/fromString value)))))
                   (:uuids @values)))
    (check "IPv4 draws use valid dotted-quad text"
           (every? valid-ipv4? (:ipv4 @values)))
    (check "IPv6 draws use valid canonical colon-hex text"
           (every? valid-ipv6? (:ipv6 @values)))
    (let [formatted
          (fn [data]
            (with-redefs [hffi/generate-ipv6! (fn [& _] (byte-array data))]
              ((g/ipv6) {:context nil :handle nil})))]
      (check "IPv6 formatting compresses the longest zero run"
             (and (= "2001:db8::1"
                     (formatted [0x20 0x01 0x0d 0xb8 0 0 0 0
                                 0 0 0 0 0 0 0 1]))
                  (= "::" (formatted (repeat 16 0))))))))

(defn temporal-generators []
  (let [fixed-date {:year 2024 :month 2 :day 29}
        fixed-time {:hour 14 :minute 30 :second 15 :microsecond 123456}
        fixed-datetime {:date fixed-date :time fixed-time}
        minimum-date {:year 1 :month 1 :day 1}
        maximum-date {:year 9999 :month 12 :day 31}
        minimum-time {:hour 0 :minute 0 :second 0 :microsecond 0}
        maximum-time {:hour 23 :minute 59 :second 59 :microsecond 999999}
        minimum-datetime {:date minimum-date :time minimum-time}
        maximum-datetime {:date maximum-date :time maximum-time}
        values (atom {:dates [] :times [] :datetimes []
                      :fixed-dates [] :fixed-times [] :fixed-datetimes []
                      :minimum-dates [] :maximum-dates []
                      :minimum-times [] :maximum-times []
                      :minimum-datetimes [] :maximum-datetimes []})
        result
        (h/run-test!
         {:test-cases 30
          :seed 20260723
          :database ""
          :verbosity :quiet}
         (fn [_]
           (let [date-value (h/draw! (g/date))
                 time-value (h/draw! (g/time))
                 datetime-value (h/draw! (g/datetime))
                 fixed-date-value (h/draw! (g/date fixed-date fixed-date))
                 fixed-time-value (h/draw! (g/time fixed-time fixed-time))
                 fixed-datetime-value
                 (h/draw! (g/datetime fixed-datetime fixed-datetime))
                 minimum-date-value
                 (h/draw! (g/date minimum-date minimum-date))
                 maximum-date-value
                 (h/draw! (g/date maximum-date maximum-date))
                 minimum-time-value
                 (h/draw! (g/time minimum-time minimum-time))
                 maximum-time-value
                 (h/draw! (g/time maximum-time maximum-time))
                 minimum-datetime-value
                 (h/draw! (g/datetime minimum-datetime minimum-datetime))
                 maximum-datetime-value
                 (h/draw! (g/datetime maximum-datetime maximum-datetime))]
             (swap! values
                    (fn [seen]
                      (-> seen
                          (update :dates conj date-value)
                          (update :times conj time-value)
                          (update :datetimes conj datetime-value)
                          (update :fixed-dates conj fixed-date-value)
                          (update :fixed-times conj fixed-time-value)
                          (update :fixed-datetimes conj
                                  fixed-datetime-value)
                          (update :minimum-dates conj minimum-date-value)
                          (update :maximum-dates conj maximum-date-value)
                          (update :minimum-times conj minimum-time-value)
                          (update :maximum-times conj maximum-time-value)
                          (update :minimum-datetimes conj
                                  minimum-datetime-value)
                          (update :maximum-datetimes conj
                                  maximum-datetime-value)))))))]
    (check "temporal generator run passes through direct aggregate bindings"
           (:passed? result))
    (check "date draws use conventional ISO 8601 text"
           (and (seq (:dates @values))
                (every? #(some? (re-matches #"[0-9]{4}-[0-9]{2}-[0-9]{2}" %))
                        (:dates @values))))
    (check "time draws use ISO 8601 text with optional microseconds"
           (every? #(some? (re-matches
                            #"[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]{6})?" %))
                   (:times @values)))
    (check "datetime draws combine the date and time layouts"
           (every? #(some? (re-matches
                            #"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]{6})?"
                            %))
                   (:datetimes @values)))
    (check "fixed leap-day bounds round-trip through hegel_date_t"
           (every? #{"2024-02-29"} (:fixed-dates @values)))
    (check "fixed microsecond bounds round-trip through hegel_time_t"
           (every? #{"14:30:15.123456"} (:fixed-times @values)))
    (check "fixed nested bounds round-trip through hegel_datetime_t"
           (every? #{"2024-02-29T14:30:15.123456"}
                   (:fixed-datetimes @values)))
    (check "minimum temporal bounds round-trip through aggregate layouts"
           (and (every? #{"0001-01-01"} (:minimum-dates @values))
                (every? #{"00:00:00"} (:minimum-times @values))
                (every? #{"0001-01-01T00:00:00"}
                        (:minimum-datetimes @values))))
    (check "maximum temporal bounds round-trip through aggregate layouts"
           (and (every? #{"9999-12-31"} (:maximum-dates @values))
                (every? #{"23:59:59.999999"} (:maximum-times @values))
                (every? #{"9999-12-31T23:59:59.999999"}
                        (:maximum-datetimes @values))))
    (check "invalid calendar bounds fail before entering native code"
           (try
             (g/date {:min {:year 2023 :month 2 :day 29}})
             false
             (catch Throwable _ true))))
  (let [minimum {:year 2024 :month 2 :day 28}
        maximum {:year 2024 :month 3 :day 2}
        final-dates (atom [])
        result
        (h/run-test!
         {:test-cases 50
          :seed 20260724
          :database ""
          :report-multiple-failures? false
          :verbosity :quiet}
         (fn [_]
           (let [value (h/draw! (g/date minimum maximum))]
             (when (h/final?)
               (swap! final-dates conj value))
             (when (not (neg? (compare value "2024-02-29")))
               (throw
                (ex-info "date threshold violated"
                         {:hegel/origin "hegel.test-runner:date-threshold"
                          :date value}))))))
        failure (first (:failures result))]
    (check "date failures shrink through direct aggregate bindings"
           (not (:passed? result)))
    (check "date shrinking finds and replays the minimal leap-day failure"
           (= ["2024-02-29"] @final-dates))
    (check "the temporal counterexample is reproduced, not flaky"
           (and (:reproduced? failure) (false? (:flaky? result))))))

(defn- throws? [f]
  (try
    (f)
    false
    (catch Throwable _
      true)))

(defn harness-integrity []
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
    (check "combinator spans close exactly once when mapping throws"
           (and (= marker error)
                (= [[:start hffi/label-mapped] [:stop false]] @events))))
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
    (check "filter spans close exactly once when predicates throw"
           (and (= marker error)
                (= [[:start hffi/label-filter] [:stop false]] @events))))
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
    (check "native harness errors abort instead of becoming counterexamples"
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
    (check "final replay requires the original failure origin"
           (and (true? (:flaky? result))
                (false? (:reproduced? failure))
                (= "hegel.test-runner:original-origin" (:origin failure))
                (= "hegel.test-runner:replay-origin"
                   (:replay-origin failure))))))

(defn string-generators []
  (let [text-gen (g/string {:min-size 2 :max-size 5
                            :alphabet "ab😀"})
        nul-gen (g/string {:min-size 1 :max-size 1 :alphabet "\u0000"})
        character-gen
        (g/character {:codec :ascii
                      :min-codepoint 97
                      :max-codepoint 122
                      :exclude-characters "aeiou"})
        strict-regex-gen (g/regex-str "[A-Z]{2}-[0-9]{4}")
        loose-regex-gen (g/regex-str "[A-Z]{2}" {:full-match? false})
        email-gen (g/email)
        url-gen (g/url-str)
        domain-gen (g/domain {:max-length 30})
        values (atom [])
        result
        (h/run-test!
         {:test-cases 50
          :seed 20260725
          :database ""
          :verbosity :quiet}
         (fn [_]
           (swap! values conj
                  {:text (h/draw! text-gen)
                   :nul (h/draw! nul-gen)
                   :character (h/draw! character-gen)
                   :strict (h/draw! strict-regex-gen)
                   :loose (h/draw! loose-regex-gen)
                   :email (h/draw! email-gen)
                   :url (h/draw! url-gen)
                   :domain (h/draw! domain-gen)})))]
    (check "string and format generators run through owned native handles"
           (:passed? result))
    (check "text sizes count Unicode code points"
           (every? #(<= 2 (codepoint-count (:text %)) 5) @values))
    (check "length-delimited UTF-8 preserves an embedded NUL"
           (every? #(= "\u0000" (:nul %)) @values))
    (check "character filters reach the native text generator"
           (every? #(some? (re-matches #"[b-df-hj-np-tv-z]"
                                       (:character %)))
                   @values))
    (check "regex generation defaults to full matching"
           (every? #(some? (re-matches #"[A-Z]{2}-[0-9]{4}" (:strict %)))
                   @values))
    (check "non-full regex generation always contains a match"
           (every? #(some? (re-find #"[A-Z]{2}" (:loose %))) @values))
    (check "email, URL, and bounded domain generators produce their formats"
           (every?
            (fn [{:keys [email url domain]}]
              (and (str/includes? email "@")
                   (or (str/starts-with? url "http://")
                       (str/starts-with? url "https://"))
                   (not (empty? domain))
                   (<= (count domain) 30)))
            @values)))
  (check "alphabet cannot be combined with character filters"
         (throws? #(g/string {:alphabet "abc" :codec :ascii})))
  (let [error (try
                (g/string {:alphabet (vec "abc")})
                nil
                (catch Throwable error
                  error))]
    (check "alphabet rejects character collections with a useful message"
           (and (= "string alphabet must be a string" (ex-message error))
                (= ::g/invalid-option (:type (ex-data error))))))
  (check "invalid regexes fail when the generator is constructed"
         (throws? #(g/regex-str "(")))
  (check "unknown codecs fail when the generator is constructed"
         (throws? #(g/string {:codec :not-a-codec})))
  (check "domain length is validated before native construction"
         (and (throws? #(g/domain {:max-length 3}))
              (throws? #(g/domain {:max-length 256})))))

(defn collection-combinators []
  (let [dependent
        (g/bind
         (fn [size]
           (g/tuple (g/vector {:size size} (g/integer 0 9))
                    (g/vector {:size size} (g/boolean))))
         (g/integer 1 5))
        values (atom [])
        result
        (h/run-test!
         {:test-cases 40
          :seed 20260726
          :database ""
          :verbosity :quiet}
         (fn [_]
           (g/let [drawn (g/integer 1 5)
                   dependent-value (+ drawn 2)
                   ordinary-fn identity]
             (swap!
              values conj
              {:dependent [drawn dependent-value]
               :ordinary-fn? (fn? ordinary-fn)
               :mapped (h/draw! (g/fmap #(* 2 %) (g/integer 0 20)))
               :bound (h/draw! dependent)
               :filtered (h/draw! (g/filter even? (g/integer 0 100)))
               :sampled (h/draw! (g/sampled-from [:a :b :c]))
               :chosen (h/draw! (g/one-of [(g/just :left)
                                            (g/just :right)]))
               :optional (h/draw! (g/optional (g/just 1)))
               :chunks (h/draw! (g/chunkings [0 1 2 3 4 5]))
               :empty-chunks (h/draw! (g/chunkings []))
               :single-chunks (h/draw! (g/chunkings [7]))
               :vector (h/draw! (g/vector {:min-size 3 :max-size 5
                                            :unique? true}
                                           (g/integer 0 20)))
               :list (h/draw! (g/list {:size 3} (g/boolean)))
               :set (h/draw! (g/set {:min-size 2 :max-size 4}
                                     (g/integer 0 20)))
               :sorted-set (h/draw! (g/sorted-set {:size 3}
                                                   (g/integer 0 20)))
               :map (h/draw! (g/map {:min-size 2 :max-size 4}
                                     (g/integer 0 20) (g/boolean)))
               :sorted-map (h/draw! (g/sorted-map {:size 2}
                                                   (g/integer 0 20)
                                                   (g/boolean)))
               :tuple (h/draw! (g/tuple (g/boolean) (g/integer 0 9)))
               :hmap (h/draw! (g/hmap {:name (g/just "Ada")
                                       :age (g/integer 0 100)}))}))))]
    (check "collection and composition generators pass together"
           (:passed? result))
    (check "g/let draws tagged generators and keeps dependent values ordinary"
           (every? (fn [{:keys [dependent ordinary-fn?]}]
                     (and (= (+ (first dependent) 2) (second dependent))
                          ordinary-fn?))
                   @values))
    (check "fmap, filter, sampled-from, and one-of preserve their contracts"
           (every? (fn [{:keys [mapped filtered sampled chosen]}]
                     (and (even? mapped)
                          (even? filtered)
                          (contains? #{:a :b :c} sampled)
                          (contains? #{:left :right} chosen)))
                   @values))
    (check "bind supports dependent fixed-size collection draws"
           (every? (fn [{:keys [bound]}]
                     (and (<= 1 (count (first bound)) 5)
                          (= (count (first bound))
                             (count (second bound)))))
                   @values))
    (check "chunkings preserve payloads with nonempty chunks"
           (every?
            (fn [{:keys [chunks empty-chunks single-chunks]}]
              (and (vector? chunks)
                   (every? #(and (vector? %) (pos? (count %))) chunks)
                   (= [0 1 2 3 4 5] (vec (mapcat identity chunks)))
                   (= [] empty-chunks)
                   (= [[7]] single-chunks)))
            @values))
    (check "vector, list, set, and map shapes and bounds are enforced"
           (every?
            (fn [{:keys [vector list set map]}]
              (and (vector? vector)
                   (<= 3 (count vector) 5)
                   (= (count vector) (count (distinct vector)))
                   (list? list) (= 3 (count list))
                   (set? set) (<= 2 (count set) 4)
                   (map? map) (<= 2 (count map) 4)))
            @values))
    (check "sorted collections, tuples, hmaps, and optional values work"
           (every?
            (fn [{:keys [sorted-set sorted-map tuple hmap optional]}]
              (and (sorted? sorted-set) (set? sorted-set)
                   (sorted? sorted-map) (map? sorted-map)
                   (= 2 (count tuple))
                   (= "Ada" (:name hmap))
                   (<= 0 (:age hmap) 100)
                   (contains? #{nil 1} optional)))
            @values)))
  (check "empty sampled-from and one-of inputs are rejected"
         (and (throws? #(g/sampled-from []))
              (throws? #(g/one-of []))))
  (check "inverted collection bounds are rejected"
         (throws? #(g/vector {:min-size 3 :max-size 2} (g/boolean)))))

(defn- minimal-value [origin generator interesting?]
  (let [final-values (atom [])
        result
        (h/run-test!
         {:test-cases 300
          :seed 1
          :database ""
          :report-multiple-failures? false
          :verbosity :quiet}
         (fn [_]
           (let [value (h/draw! generator)]
             (when (h/final?)
               (swap! final-values conj value))
             (when (interesting? value)
               (throw
                (ex-info "shrink-quality predicate matched"
                         {:hegel/origin origin
                          :value value}))))))]
    {:result result
     :value (first @final-values)
     :failure (first (:failures result))}))

(defn combinator-shrink-quality []
  (let [string-case
        (minimal-value "hegel.test-runner:string-length"
                       (g/string)
                       #(>= (codepoint-count %) 10))
        vector-case
        (minimal-value "hegel.test-runner:vector-containment"
                       (g/vector (g/integer 0 100))
                       #(some #{42} %))
        bind-case
        (minimal-value
         "hegel.test-runner:bind-trues"
         (g/bind (fn [size]
                   (g/vector {:size size} (g/boolean)))
                 (g/integer 0 20))
         #(>= (count (clojure.core/filter true? %)) 3))
        set-case
        (minimal-value "hegel.test-runner:set-size"
                       (g/set (g/integer))
                       #(>= (count %) 3))
        one-of-case
        (minimal-value
         "hegel.test-runner:one-of"
         (g/one-of
          [(g/fmap (fn [value] [:boolean value]) (g/boolean))
           (g/fmap (fn [value] [:integer value]) (g/integer -100 100))])
         (fn [[kind value]]
           (if (= kind :boolean) value (not (zero? value)))))]
    (check "text shrinking matches Hegel's ten-zero minimum"
           (= "0000000000" (:value string-case)))
    (check "collection shrinking keeps only the required contained value"
           (= [42] (:value vector-case)))
    (check "flat-map shrinking minimizes both dependent sides"
           (= [true true true] (:value bind-case)))
    (check "set shrinking finds one of Hegel's canonical adjacent triples"
           (contains? #{#{0 1 2} #{-1 0 1}} (:value set-case)))
    (check "one-of shrinking prefers the simpler failing branch"
           (= [:boolean true] (:value one-of-case)))
    (check "all cross-binding shrink cases reproduce without flakiness"
           (every? (fn [{:keys [result failure]}]
                     (and (not (:passed? result))
                          (:reproduced? failure)
                          (false? (:flaky? result))))
                   [string-case vector-case bind-case set-case one-of-case]))))

(defn stateful-pools-and-models []
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
    (check "stateful pools round-trip through reusable and consumed draws"
           (:passed? result))
    (check "reusable pool draws retain values and consumed draws remove them"
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
    (check "empty pool draws skip stateful rules without failing the case"
           (:passed? result))
    (check "skipped rules do not mutate state or rerun invariants"
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
    (check "stateful model tests execute generated rule sequences"
           (:passed? result))
    (check "stateful invariants hold on every returned model state"
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

(defn stateful-shrink-quality []
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
    (check "stateful shrinking minimizes a counter failure to two increments"
           (= [:inc :inc] (:trace counter)))
    (check "stateful shrinking preserves the required open-close transition"
           (= [:open :close] (:trace transition)))
    (check "minimal stateful traces replay without flakiness"
           (every?
            (fn [{:keys [result failure]}]
              (and (not (:passed? result))
                   (:reproduced? failure)
                   (false? (:flaky? result))))
            [counter transition]))
    (check "stateful invariant names provide stable failure origins"
           (and (= "hegel.stateful/invariant:below-two"
                   (-> counter :failure :origin))
                (= "hegel.stateful/invariant:never-reclose"
                   (-> transition :failure :origin))))
    (check "genuine rule failures remain interesting with a stable trace"
           (and (= [:explode] (:trace rule-failure))
                (= "hegel.stateful/rule:explode"
                   (-> rule-failure :failure :origin))
                (:reproduced? (:failure rule-failure))))
    (check "stateful invariants are checked before the first rule"
           (and (= [] (:trace initial-invariant))
                (= "hegel.stateful/invariant:initially-valid"
                   (-> initial-invariant :failure :origin))
                (:reproduced? (:failure initial-invariant))))
    (check "pool shrinking removes unrelated counter insertions"
           (= [:new-counter :increment :increment]
              (:trace pool-double-increment)))
    (check "pool shrinking preserves the minimal distinct pair"
           (= [:new-object :new-object :pair]
              (:trace pool-distinct-pair)))
    (check "pool shrink regressions replay without flakiness"
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

(defn stateful-swarm-and-control-flow []
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
    (check "libhegel performs swarm rule selection for stateful tests"
           (and (:passed? result)
                (>= (count (filter #(>= % 20) repeated)) 10)))
    (check "engine-managed stateful runs respect the 50-attempt normal cap"
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
    (check "stateful-step-count configures Hegel's round budget"
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
    (check "running out of draw data inside a rule remains an overrun"
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
    (check "h/assume! inside a stateful rule skips only that rule"
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
    (check "h/assume! inside an invariant rejects the whole test case"
           (and (:passed? result)
                (= 1 (:invalid-test-cases result))
                (zero? (:interesting-test-cases result)))))
  (check "state machines reject invalid rule declarations"
         (and (throws? #(hs/run! {:initial-state nil :rules []}))
              (throws?
               #(hs/run!
                 {:initial-state nil
                  :rules [(hs/rule :same identity)
                          (hs/rule :same identity)]}))
              (throws? #(hs/rule :bad {:precondition nil} identity))
              (throws? #(hs/rule :bad {:precondition false} identity))
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
    (check "stateful configuration errors abort instead of shrinking"
           (= ::hs/invalid-argument (:type (ex-data error))))))

(defn latest-stateful-abi []
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
    (check "latest stateful step count controls successful rule steps"
           (and (:passed? result)
                (seq @states)
                (every? #(= 7 %) @states)))
    (check "rejected stateful rules are reported without consuming steps"
           (pos? (:rule-rejected @counts)))
    (check "latest opaque collection handles are freed exactly once"
           (and (pos? (:collection-new @counts))
                (= (:collection-new @counts)
                   (:collection-free @counts))))
    (check "latest opaque pool handles are freed exactly once"
           (and (pos? (:pool-new @counts))
                (= (:pool-new @counts) (:pool-free @counts))))
    (check "latest opaque state-machine handles are freed exactly once"
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
    (check "stateful step count rejects zero before a native run"
           (= ::h/invalid-option (:type (ex-data error)))))
  ;; This is a fixed 51-step failure blob produced by libhegel 0.32.3. The
  ;; 0.32.3 regression was that blob replay ignored :stateful-step-count and
  ;; stopped at the old default of 50, so the invariant never failed.
  (let [ctx (hffi/context-new!)]
    (try
      (let [settings (hffi/settings-new! ctx)]
        (try
          (hffi/settings-set-stateful-step-count! ctx settings 52)
          (let [handle
                (hffi/test-case-from-blob!
                 ctx settings
                 "AXic7cihDQAACAPBr2V/xbRgCKobVDS9fAOC2nGnL2FoOU7+Az0=")
                test-case (h/->TestCase ctx handle true :quiet)]
            (try
              (let [error
                    (binding [h/*test-case* test-case]
                      (try
                        (hs/run!
                         {:initial-state {:count 0}
                          :rules [(hs/rule :inc #(update % :count inc))]
                          :invariants
                          [(hs/invariant :below-fifty-one
                                         #(< (:count %) 51))]})
                        nil
                        (catch Throwable error
                          error)))]
                (check "stateful blobs replay failures beyond fifty steps"
                       (and error
                            (= 51
                               (count (::hs/trace (ex-data error)))))))
              (finally
                (hffi/test-case-free! ctx handle))))
          (finally
            (hffi/settings-free! ctx settings))))
      (finally
        (hffi/context-free! ctx)))))
(defn caught-error [f]
  (try
    (f)
    nil
    (catch Throwable error
      error)))

(defn malli-adapter-construction []
  (let [cases
        [{:description "rejects intersection schemas"
          :form [:and :int [:> 0]]
          :type :hegel.malli/unsupported-schema
          :path []}
         {:description "rejects regex schemas"
          :form [:* :int]
          :type :hegel.malli/unsupported-schema
         :path []}
         {:description "rejects predicate schemas"
          :form 'string?
          :type :hegel.malli/unsupported-schema
          :path []}
         {:description "rejects references"
          :form [:ref {:registry {::node :int}} ::node]
          :type :hegel.malli/unsupported-schema
          :path []}
         {:description "rejects custom generator properties"
          :form [:vector [:string {:gen/elements ["x"]}]]
          :type :hegel.malli/unsupported-property
          :path [:child :properties :gen/elements]}
         {:description "rejects registries on otherwise supported schemas"
          :form [:int {:registry {::value :int}}]
          :type :hegel.malli/unsupported-property
          :path [:properties :registry]}
         {:description "rejects open maps"
          :form [:map [:x :int]]
          :type :hegel.malli/unsupported-schema
          :path []}
         {:description "rejects default map entries"
          :form [:map {:closed true} [::m/default :int]]
          :type :hegel.malli/unsupported-schema
          :path [:keys ::m/default]}]]
    (doseq [{:keys [description form type path]} cases]
      (let [error (caught-error #(hm/generator form))
            data (ex-data error)]
        (check description
               (and error
                    (= type (:type data))
                    (= path (:path data))
                    (= form (:form data)))))))
  (let [form [:vector :int]
        error (caught-error #(hm/generator form {:size 4}))]
    (check "rejects unknown adapter config"
           (= {:type :hegel.malli/invalid-config
               :path [:config :size]
               :form form}
              (select-keys (ex-data error) [:type :path :form]))))
  (let [generator (hm/generator [:vector :boolean]
                                {:default-max-size 3})
        result
        (h/run-test!
         {:test-cases 25 :seed 20260804 :database "" :verbosity :quiet}
         (fn [_]
           (let [value (h/draw! generator)]
             (when (> (count value) 3)
               (throw (ex-info "adapter fallback bound was violated"
                               {:hegel/origin
                                "hegel.test-runner:malli-config-bound"}))))))]
    (check "applies the configured fallback collection bound"
           (:passed? result)))
  (let [form [:int {:min 0 :max 9223372036854775808N}]
        error (caught-error #(hm/generator form))]
    (check "rejects integer bounds outside Hegel's int64 domain"
           (= {:type :hegel.malli/invalid-property
               :path []
               :form form}
              (select-keys (ex-data error) [:type :path :form]))))
  (let [forms [[:string {:max 18446744073709551616N}]
               [:vector {:min 18446744073709551616N} :boolean]]]
    (doseq [form forms]
      (let [error (caught-error #(hm/generator form))]
        (check "rejects collection bounds outside Hegel's uint64 domain"
               (= {:type :hegel.malli/invalid-property
                   :path []
                   :form form}
                  (select-keys (ex-data error) [:type :path :form]))))))
  (let [form [:vector :boolean]
        uint64-max 18446744073709551615N
        error (caught-error
               #(hm/generator form {:default-max-size (inc uint64-max)}))]
    (check "rejects adapter fallback outside Hegel's uint64 domain"
           (= {:type :hegel.malli/invalid-config
               :path [:config :default-max-size]
               :form form}
              (select-keys (ex-data error) [:type :path :form])))))

(defn malli-adapter-generation []
  (let [schema
        [:map {:closed true}
         [:id [:int {:min 1 :max 9}]]
         [:payload
          [:tuple
           [:enum :left :right]
           [:vector {:min 1 :max 4}
            [:or :nil [:string {:min 1 :max 5}]]]]]
         [:flags [:set {:min 1 :max 2} :boolean]]
         [:attributes {:optional true}
          [:map-of {:max 2}
           [:enum :x :y]
           [:double {:min -1.0 :max 1.0}]]]]
        valid? (m/validator schema)
        generator (hm/generator schema)
        seen (atom 0)
        result
        (h/run-test!
         {:test-cases 100 :seed 20260805 :database "" :verbosity :quiet}
         (fn [_]
           (let [value (h/draw! generator)]
             (swap! seen inc)
             (when-not (valid? value)
               (throw (ex-info "nested Malli value was invalid"
                               {:hegel/origin
                                "hegel.test-runner:malli-nested-validity"}))))))]
    (check "generates valid nested values from the supported Malli subset"
           (and (:passed? result) (pos? @seen))))
  (let [schema
        [:tuple
         [:int {:min -3 :max 7}]
         [:double {:min -2.5 :max 4.5}]
         [:string {:min 2 :max 6}]
         [:sequential {:min 1 :max 3} :boolean]
         [:map-of {:min 1 :max 2} [:enum :a :b] :nil]]
        generator (hm/generator schema)
        result
        (h/run-test!
         {:test-cases 100 :seed 20260806 :database "" :verbosity :quiet}
         (fn [_]
           (let [[integer double string sequential map]
                 (h/draw! generator)]
             (when-not (and (<= -3 integer 7)
                            (<= -2.5 double 4.5)
                            (<= 2 (codepoint-count string) 6)
                            (<= 1 (count sequential) 3)
                            (<= 1 (count map) 2))
               (throw (ex-info "Malli bounds were violated"
                               {:hegel/origin
                                "hegel.test-runner:malli-bounds"}))))))]
    (check "honors numeric, string, and collection bounds"
           (:passed? result)))
  (let [seen (atom #{})
        schema [:map {:closed true}
                [:value {:optional true} [:maybe [:= :present]]]]
        generator (hm/generator schema)
        result
        (h/run-test!
         {:test-cases 100 :seed 20260807 :database "" :verbosity :quiet}
         (fn [_]
           (let [value (h/draw! generator)]
             (swap! seen conj
                    (cond
                      (not (contains? value :value)) :absent
                      (nil? (:value value)) :present-nil
                      :else :present-value)))))]
    (check "distinguishes an absent optional key from a present nil"
           (and (:passed? result)
                (= #{:absent :present-nil :present-value} @seen))))
  (let [final-values (atom [])
        generator (hm/generator [:int {:min 0 :max 100}])
        result
        (h/run-test!
         {:test-cases 200
          :seed 1777986545686
          :database ""
          :report-multiple-failures? false
          :verbosity :quiet}
         (fn [_]
           (let [value (h/draw! generator)]
             (when (h/final?)
               (swap! final-values conj value))
             (when (>= value 10)
               (throw (ex-info "Malli shrink threshold violated"
                               {:hegel/origin
                                "hegel.test-runner:malli-native-shrink"
                                :value value}))))))]
    (check "retains native Hegel shrinking through the Malli adapter"
           (and (not (:passed? result))
                (= [10] @final-values)
                (= 10 (-> result :final first :exception ex-data :value))))))
(defn semantic-trace-rules []
  (let [events [{:seq 1 :operation-id 1 :parent-operation-id nil
                 :phase :enter :role :agent/run}
                {:seq 2 :operation-id 2 :parent-operation-id 1
                 :phase :enter :role :agent/model}
                {:seq 3 :operation-id 2 :parent-operation-id 1
                 :phase :return :role :agent/model}
                {:seq 4 :operation-id 1 :parent-operation-id nil
                 :phase :return :role :agent/run}]
        checked (htrace/check!
                 events
                 [(htrace/contiguous-sequence)
                  (htrace/closed-lifecycles)
                  (htrace/synchronous-parentage)
                  (htrace/every-eventually
                   :model-terminates
                   #(and (= :agent/model (:role %))
                         (= :enter (:phase %)))
                   #(contains? #{:return :throw} (:phase %)))])]
    (check "semantic trace rules accept a complete nested aspect trace"
           (= events checked)))
  (let [failure
        (try
          (htrace/check!
           [{:seq 2 :operation-id 1 :phase :enter}
            {:seq 3 :operation-id 1 :phase :return}]
           [(htrace/contiguous-sequence :journal-not-truncated)])
          nil
          (catch Throwable error error))]
    (check "trace-rule failures expose a stable Hegel origin and bounded evidence"
           (and (= "hegel.trace/journal-not-truncated"
                   (:hegel/origin (ex-data failure)))
                (= 2 (:hegel.trace/event-count (ex-data failure)))
                (= [2 3]
                   (mapv :seq (:hegel.trace/events (ex-data failure)))))))
  (let [events [{:partition :a :cursor 1}
                {:partition :b :cursor 1}
                {:partition :a :cursor 3}
                {:partition :b :cursor 2}]
        checked (htrace/check!
                 events
                 [(htrace/ordered-sequence
                   :partition-cursors-increase
                   {:value :cursor :scope :partition
                    :order :strictly-increasing :start 1})])
        gap-failure
        (try
          (htrace/check!
           events
           [(htrace/ordered-sequence
             :partition-cursors-contiguous
             {:value :cursor :scope :partition
              :order :contiguous :start 1})])
          nil
          (catch Throwable error error))]
    (check "sequence rules distinguish scoped strict increase from continuity"
           (and (= events checked)
                (= "hegel.trace/partition-cursors-contiguous"
                   (:hegel/origin (ex-data gap-failure))))))
  (let [events [{:seq 1} {:seq 1} {:seq 4}]
        checked (htrace/check!
                 events
                 [(htrace/ordered-sequence
                   :delivery-watermark-does-not-decrease
                   {:order :nondecreasing})])]
    (check "nondecreasing sequence rules permit duplicates and gaps"
           (= events checked)))
  (let [transition (fn [state event]
                     (case [state (:event event)]
                       [nil :open] :open
                       [:open :use] :open
                       [:open :close] :closed
                       :invalid))
        fd-rule (htrace/event-model
                 :fd-linear-lifecycle
                 {:scope :fd
                  :initial nil
                  :step transition
                  :invariant (fn [state _event] (not= :invalid state))
                  :final #(= :closed %)})
        valid [{:fd 3 :event :open}
               {:fd 4 :event :open}
               {:fd 3 :event :use}
               {:fd 4 :event :close}
               {:fd 3 :event :close}]
        invalid (conj valid {:fd 3 :event :use})
        failure (try
                  (htrace/check! invalid [fd-rule])
                  nil
                  (catch Throwable error error))]
    (check "scoped event models express linear resource lifecycles"
           (and (= valid (htrace/check! valid [fd-rule]))
                (= "hegel.trace/fd-linear-lifecycle"
                   (:hegel/origin (ex-data failure))))))
  (let [final-values (atom [])
        result
        (h/run-test!
         {:test-cases 100
          :seed 20260828
          :database ""
          :verbosity :quiet
          :report-multiple-failures? false}
         (fn [_]
           (let [duplicate-at (h/draw! (g/integer 0 20))
                 events (cond->
                         [{:seq 1 :operation-id 1 :phase :enter}
                          {:seq 2 :operation-id 1 :phase :return}]
                          (>= duplicate-at 5)
                          (conj {:seq 3 :operation-id 1 :phase :return}))]
             (when (h/final?)
               (swap! final-values conj duplicate-at))
             (htrace/check! events
                            [(htrace/closed-lifecycles
                              :operation-has-one-terminal)]))))
        failure (first (:failures result))]
    (check "Hegel shrinks the input which produced an invalid semantic trace"
           (and (not (:passed? result))
                (:reproduced? failure)
                (= "hegel.trace/operation-has-one-terminal" (:origin failure))
                (= [5] @final-values)
                (= [:enter :return :return]
                   (mapv :phase
                         (-> result :final first :exception ex-data
                             :hegel.trace/events)))))))

(t/deftest embedded-hegel-property
  (ht/with {:test-cases 20
            :seed 20260727
            :database ""
            :verbosity :quiet}
    [xs (g/vector {:max-size 8} (g/integer -10 10))]
    (t/is (= xs (vec xs)))))

(defn clojure-test-integration []
  (let [events (atom [])]
    (with-redefs [t/report #(swap! events conj %)]
      (t/test-var #'embedded-hegel-property))
    (check "a real clojure.test deftest can host a passing Hegel property"
           (= [:pass]
              (into []
                    (comp (map :type)
                          (filter #{:pass :fail :error}))
                    @events))))
  (let [events (atom [])
        error
        (with-redefs [t/report #(swap! events conj %)
                      hffi/generate-integer!
                      (fn [& _]
                        (throw
                         (ex-info "native harness failed"
                                  {:type ::hffi/error
                                   :operation :generate-integer
                                   :result 3})))]
          (try
            (ht/with {:test-cases 1
                      :seed 20260818
                      :database ""
                      :verbosity :quiet}
              [x (g/integer 0 1)]
              (t/is (<= 0 x 1)))
            nil
            (catch Throwable error
              error)))]
    (check "clojure.test properties preserve native harness errors"
           (and (= ::hffi/error (:type (ex-data error)))
                (empty? @events))))
  (let [events (atom [])
        calls (atom 0)
        result
        (with-redefs [t/report #(swap! events conj %)]
          (ht/with {:test-cases 1
                    :seed 20260819
                    :database ""
                    :verbosity :quiet}
            []
            (h/assume! (> (swap! calls inc) 1))))]
    (check "clojure.test properties preserve assumption control flow"
           (and (:passed? result)
                (= 1 (:invalid-test-cases result))
                (= [:pass] (mapv :type @events)))))
  (let [events (atom [])
        final-values (atom [])
        result
        (with-redefs [t/report #(swap! events conj %)]
          (ht/with {:test-cases 100
                    :seed 1
                    :database ""
                    :report-multiple-failures? false
                    :verbosity :quiet}
            [x (g/integer 0 100)]
            (when (h/final?)
              (swap! final-values conj x))
            (t/is (< x 10))))
        failure (first (:failures result))]
    (check "a failing clojure.test assertion is shrunk and reproduced"
           (and (not (:passed? result))
                (:reproduced? failure)
                (= [10] @final-values)))
    (check "only the final minimal clojure.test failure is reported"
           (and (= [:fail] (mapv :type @events))
                (str/includes? (pr-str (:actual (first @events))) "10")
                (str/includes? (:message (first @events))
                               "Hegel seed: 1")))
    (check "clojure.test origins are stable and independent of drawn values"
           (and (str/includes? (:origin failure) "hegel/test_runner.clj:")
                (str/ends-with? (:origin failure) ":(< x 10)"))))
  (let [events (atom [])
        result
        (with-redefs [t/report #(swap! events conj %)]
          (ht/with {:test-cases 1
                    :seed 4242
                    :database ""
                    :verbosity :quiet
                    :suppress-health-checks [:large-initial-test-case]}
            []
            (aget (byte-array 0) 0)))
        event (first @events)]
    (check "blank native exception messages retain an identifiable cause"
           (and (not (:passed? result))
                (= [:fail] (mapv :type @events))
                (str/includes? (:actual event) "out of bounds")
                (str/includes? (:message event) "Hegel seed: 4242"))))
  (let [events (atom [])
        result
        (with-redefs [t/report #(swap! events conj %)]
          (ht/with {:test-cases 1
                    :seed 4244
                    :database ""
                    :verbosity :quiet
                    :suppress-health-checks [:large-initial-test-case]}
            []
            (throw (ex-info "" {:detail :present}))))
        event (first @events)
        failure-data (some-> result :failures first :exception ex-data)]
    (check "blank ex-info messages retain exception data"
           (and (not (:passed? result))
                (str/includes? (:actual event)
                               "ex-data: {:detail :present}")
                (= {:detail :present}
                   (::ht/cause-data failure-data)))))
  (let [events (atom [])
        calls (atom 0)
        result
        (with-redefs [t/report #(swap! events conj %)]
          (ht/with {:test-cases 1
                    :seed 17
                    :database ""
                    :verbosity :quiet
                    :suppress-health-checks [:large-initial-test-case]}
            []
            (h/draw! (g/integer 0 10))
            (when (= 1 (swap! calls inc))
              (throw (ex-info "transient" {})))))
        event (first @events)]
    (check "clojure.test reports engine flakiness without aborting the suite"
           (and (= :error (:status result))
                (true? (:flaky? result))
                (= [:fail] (mapv :type @events))
                (str/starts-with? (:actual event) "Flaky test detected:")
                (str/includes? (:message event) "Hegel seed: 17")))))

(defn -main [& _]
  (reset-progress!)
  (scenario "passing run" passing-run)
  (scenario "shrinking and final replay" shrinking-run)
  (scenario "engine nondeterminism" engine-nondeterminism)
  (scenario "framework-less counting reporting" counting-reporting)
  (scenario "cleanup and ABI version" cleanup-and-version)
  (scenario "installer source identity" installer-source-identity)
  (scenario "generated seed" generated-seed)
  (scenario "controls and sample" controls-and-sample)
  (scenario "primitive generators" primitive-generators)
  (scenario "temporal generators through direct aggregate bindings"
            temporal-generators)
  (scenario "harness and replay integrity" harness-integrity)
  (scenario "string and format generators" string-generators)
  (scenario "collection and composition generators" collection-combinators)
  (scenario "cross-binding combinator shrink quality"
            combinator-shrink-quality)
  (scenario "Malli adapter construction" malli-adapter-construction)
  (scenario "Malli adapter generation and shrinking"
            malli-adapter-generation)
  (scenario "stateful pools and model tests" stateful-pools-and-models)
  (scenario "stateful shrink quality" stateful-shrink-quality)
  (scenario "stateful swarm and control flow"
            stateful-swarm-and-control-flow)
  (scenario "latest stateful ABI and owned handles" latest-stateful-abi)
  (scenario "bounded semantic trace rules" semantic-trace-rules)
  (scenario "clojure.test integration" clojure-test-integration)
  (println "Ran jolt-hegel scenarios;" @failures "failures")
  (flush)
  (System/exit (if (zero? @failures) 0 1)))
