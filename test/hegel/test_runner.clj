(ns hegel.test-runner
  (:require [clojure.string :as str]
            [clojure.test :as t]
            [hegel.clojure-test :as ht]
            [hegel.core :as h]
            [hegel.ffi :as hffi]
            [hegel.generator :as g]
            [hegel.native :as native]
            [hegel.stateful :as hs]))

(def failures (atom 0))
(def progress-file
  (native/join-path
   (or (native/nonblank-env "RUNNER_TEMP")
       (native/nonblank-env "TMPDIR")
       (native/nonblank-env "TEMP")
       (native/nonblank-env "TMP")
       (System/getProperty "java.io.tmpdir")
       ".")
   "jolt-hegel-test-progress.log"))

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

(defn primitive-generators []
  (let [values (atom {:true [] :false [] :doubles [] :bytes [] :empty-bytes []
                      :uuids [] :ipv4 [] :ipv6 []})
        result
        (h/run-test!
         {:test-cases 40
          :seed 20260722
          :database ""
          :verbosity :quiet}
         (fn [_]
           (let [true-value (h/draw! (g/boolean 1.0))
                 false-value (h/draw! (g/boolean 0.0))
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
    (check "fixed-size bytes are copied into jolt byte arrays"
           (and (every? (fn [data]
                          (and (= 8 (alength data))
                               (every? #(<= 0 % 255) (seq data))))
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
        values (atom {:dates [] :times [] :datetimes []
                      :fixed-dates [] :fixed-times [] :fixed-datetimes []})
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
                 (h/draw! (g/datetime fixed-datetime fixed-datetime))]
             (swap! values
                    (fn [seen]
                      (-> seen
                          (update :dates conj date-value)
                          (update :times conj time-value)
                          (update :datetimes conj datetime-value)
                          (update :fixed-dates conj fixed-date-value)
                          (update :fixed-times conj fixed-time-value)
                          (update :fixed-datetimes conj
                                  fixed-datetime-value)))))))]
    (check "temporal generator run passes through the C shim"
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
    (check "date failures shrink through the C shim" (not (:passed? result)))
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
           (every? #(<= 2 (count (:text %)) 5) @values))
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
                       #(>= (count %) 10))
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
                   #{(h/draw! (hs/values-consumed special-pool))
                     (h/draw! (hs/values-consumed special-pool))}]
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
           (hs/run! config)))]
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
          :invariants [(hs/invariant :initially-valid (constantly false))]})]
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
                (:reproduced? (:failure initial-invariant))))))

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
           (= [:pass] (mapv :type @events))))
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
                (str/includes? (:message (first @events)) "10")))
    (check "clojure.test origins are stable and independent of drawn values"
           (and (str/includes? (:origin failure) "hegel/test_runner.clj:")
                (str/ends-with? (:origin failure) ":assertion-0")))))

(defn -main [& _]
  (reset-progress!)
  (scenario "passing run" passing-run)
  (scenario "shrinking and final replay" shrinking-run)
  (scenario "cleanup and ABI version" cleanup-and-version)
  (scenario "generated seed" generated-seed)
  (scenario "controls and sample" controls-and-sample)
  (scenario "primitive generators" primitive-generators)
  (scenario "temporal generators through C shim" temporal-generators)
  (scenario "string and format generators" string-generators)
  (scenario "collection and composition generators" collection-combinators)
  (scenario "cross-binding combinator shrink quality"
            combinator-shrink-quality)
  (scenario "stateful pools and model tests" stateful-pools-and-models)
  (scenario "stateful shrink quality" stateful-shrink-quality)
  (scenario "stateful swarm and control flow"
            stateful-swarm-and-control-flow)
  (scenario "clojure.test integration" clojure-test-integration)
  (println "Ran jolt-hegel scenarios;" @failures "failures")
  (flush)
  (System/exit (if (zero? @failures) 0 1)))
