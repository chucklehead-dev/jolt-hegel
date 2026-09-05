(ns hegel.suites.generators
  "Generator contract scenarios, loaded only when their suite is selected."
  (:require [clojure.string :as str]
            [clojure.test :as t]
            [hegel.core :as h]
            [hegel.ffi :as hffi]
            [hegel.generator :as g]
            [hegel.temporal-test]
            [hegel.test-support :as support]))

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

(defn primitive-generators [context]
  (doseq [[description min-value max-value]
          [["rejects fractional integer bounds" 1.5 1.5]
           ["rejects integral double integer bounds" 1.0 1.0]
           ["rejects ratio integer bounds" 3/2 3/2]
           ["rejects nil integer bounds" nil 0]
           ["rejects non-numeric integer bounds" :minimum 0]
           ["rejects integer bounds below int64" -9223372036854775809N 0]
           ["rejects integer bounds above int64" 0 9223372036854775808N]]]
    (let [error (try
                  (g/integer min-value max-value)
                  nil
                  (catch Throwable error error))]
      (support/check! context description
             (and (= {:type ::g/invalid-bounds
                      :min min-value
                      :max max-value}
                     (select-keys (ex-data error) [:type :min :max]))
                  (true? (:hegel/usage-error? (ex-data error)))))))
  (support/check! context "accepts in-range BigInt integer bounds"
         (g/generator? (g/integer 1N 1N)))
  (support/check! context "accepts exact signed int64 endpoints"
         (g/generator? (g/integer -9223372036854775808N
                                    9223372036854775807)))
  (let [error (try
                (g/integer 1 0)
                nil
                (catch Throwable error error))]
    (support/check! context "inverted integer bounds retain their message and abort classification"
           (and (= "integer generator minimum exceeds maximum"
                   (ex-message error))
                (= ::g/invalid-bounds (:type (ex-data error)))
                (true? (:hegel/usage-error? (ex-data error))))))
  (let [native-draw? (atom false)
        error (try
                (with-redefs [hffi/generate-integer!
                              (fn [& _]
                                (reset! native-draw? true)
                                0)]
                  (h/run-test!
                   {:test-cases 1 :seed 1 :database "" :verbosity :quiet}
                   (fn [_]
                     (h/draw! (g/integer 1.5 1.5)))))
                nil
                (catch Throwable error error))]
    (support/check! context "invalid integer bounds abort a property before a native draw"
           (and (= ::g/invalid-bounds (:type (ex-data error)))
                (true? (:hegel/usage-error? (ex-data error)))
                (false? @native-draw?))))
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
    (support/check! context "primitive generator run passes" (:passed? result))
    (support/check! context "probability endpoints force booleans"
           (and (seq (:true @values))
                (every? true? (:true @values))
                (every? false? (:false @values))))
    (support/check! context "bounded doubles stay in range"
           (every? #(<= 0.0 % 1.0) (:doubles @values)))
    (support/check! context "octets are unsigned-comparable integers"
           (and (seq (:octets @values))
                (every? #(and (integer? %) (<= 0 % 255))
                        (:octets @values))))
    (support/check! context "fixed-size bytes are copied into jolt byte arrays"
           (and (every? (fn [data]
                          (and (= 8 (alength data))
                               (every? #(<= -128 % 127) (seq data))
                               (every? #(<= 0 (bit-and % 0xff) 255)
                                       (seq data))))
                        (:bytes @values))
                (every? #(zero? (alength %)) (:empty-bytes @values))))
    (support/check! context "versioned UUIDs use canonical RFC 4122 text"
           (every? (fn [value]
                     (and (some? (re-matches
                                  #"[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
                                  value))
                          (= value (str (java.util.UUID/fromString value)))))
                   (:uuids @values)))
    (support/check! context "IPv4 draws use valid dotted-quad text"
           (every? valid-ipv4? (:ipv4 @values)))
    (support/check! context "IPv6 draws use valid canonical colon-hex text"
           (every? valid-ipv6? (:ipv6 @values)))
    (let [formatted
          (fn [data]
            (with-redefs [hffi/generate-ipv6! (fn [& _] (byte-array data))]
              ((g/ipv6) {:context nil :handle nil})))]
      (support/check! context "IPv6 formatting compresses the longest zero run"
             (and (= "2001:db8::1"
                     (formatted [0x20 0x01 0x0d 0xb8 0 0 0 0
                                 0 0 0 0 0 0 0 1]))
                  (= "::" (formatted (repeat 16 0))))))))

(defn temporal-generators [context]
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
    (support/check! context "temporal generator run passes through direct aggregate bindings"
           (:passed? result))
    (support/check! context "date draws use conventional ISO 8601 text"
           (and (seq (:dates @values))
                (every? #(some? (re-matches #"[0-9]{4}-[0-9]{2}-[0-9]{2}" %))
                        (:dates @values))))
    (support/check! context "time draws use ISO 8601 text with optional microseconds"
           (every? #(some? (re-matches
                            #"[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]{6})?" %))
                   (:times @values)))
    (support/check! context "datetime draws combine the date and time layouts"
           (every? #(some? (re-matches
                            #"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]{6})?"
                            %))
                   (:datetimes @values)))
    (support/check! context "fixed leap-day bounds round-trip through hegel_date_t"
           (every? #{"2024-02-29"} (:fixed-dates @values)))
    (support/check! context "fixed microsecond bounds round-trip through hegel_time_t"
           (every? #{"14:30:15.123456"} (:fixed-times @values)))
    (support/check! context "fixed nested bounds round-trip through hegel_datetime_t"
           (every? #{"2024-02-29T14:30:15.123456"}
                   (:fixed-datetimes @values)))
    (support/check! context "minimum temporal bounds round-trip through aggregate layouts"
           (and (every? #{"0001-01-01"} (:minimum-dates @values))
                (every? #{"00:00:00"} (:minimum-times @values))
                (every? #{"0001-01-01T00:00:00"}
                        (:minimum-datetimes @values))))
    (support/check! context "maximum temporal bounds round-trip through aggregate layouts"
           (and (every? #{"9999-12-31"} (:maximum-dates @values))
                (every? #{"23:59:59.999999"} (:maximum-times @values))
                (every? #{"9999-12-31T23:59:59.999999"}
                        (:maximum-datetimes @values))))
    (support/check! context "invalid calendar bounds fail before entering native code"
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
    (support/check! context "date failures shrink through direct aggregate bindings"
           (not (:passed? result)))
    (support/check! context "date shrinking finds and replays the minimal leap-day failure"
           (= ["2024-02-29"] @final-dates))
    (support/check! context "the temporal counterexample is reproduced, not flaky"
           (and (:reproduced? failure) (false? (:flaky? result))))))

(defn temporal-precision-contract [context]
  (let [result (t/run-tests 'hegel.temporal-test)]
    (support/check! context "temporal precision contract suite"
                    (zero? (+ (:fail result) (:error result))))))


(defn string-generators [context]
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
    (support/check! context "string and format generators run through owned native handles"
           (:passed? result))
    (support/check! context "text sizes count Unicode code points"
           (every? #(<= 2 (support/codepoint-count (:text %)) 5) @values))
    (support/check! context "length-delimited UTF-8 preserves an embedded NUL"
           (every? #(= "\u0000" (:nul %)) @values))
    (support/check! context "character filters reach the native text generator"
           (every? #(some? (re-matches #"[b-df-hj-np-tv-z]"
                                       (:character %)))
                   @values))
    (support/check! context "regex generation defaults to full matching"
           (every? #(some? (re-matches #"[A-Z]{2}-[0-9]{4}" (:strict %)))
                   @values))
    (support/check! context "non-full regex generation always contains a match"
           (every? #(some? (re-find #"[A-Z]{2}" (:loose %))) @values))
    (support/check! context "email, URL, and bounded domain generators produce their formats"
           (every?
            (fn [{:keys [email url domain]}]
              (and (str/includes? email "@")
                   (or (str/starts-with? url "http://")
                       (str/starts-with? url "https://"))
                   (not (empty? domain))
                   (<= (count domain) 30)))
            @values)))
  (support/check! context "alphabet cannot be combined with character filters"
         (support/throws? #(g/string {:alphabet "abc" :codec :ascii})))
  (let [error (try
                (g/string {:alphabet (vec "abc")})
                nil
                (catch Throwable error
                  error))]
    (support/check! context "alphabet rejects character collections with a useful message"
           (and (= "string alphabet must be a string" (ex-message error))
                (= ::g/invalid-option (:type (ex-data error))))))
  (support/check! context "invalid regexes fail when the generator is constructed"
         (support/throws? #(g/regex-str "(")))
  (support/check! context "unknown codecs fail when the generator is constructed"
         (support/throws? #(g/string {:codec :not-a-codec})))
  (support/check! context "domain length is validated before native construction"
         (and (support/throws? #(g/domain {:max-length 3}))
              (support/throws? #(g/domain {:max-length 256})))))

(defn collection-combinators [context]
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
    (support/check! context "collection and composition generators pass together"
           (:passed? result))
    (support/check! context "g/let draws tagged generators and keeps dependent values ordinary"
           (every? (fn [{:keys [dependent ordinary-fn?]}]
                     (and (= (+ (first dependent) 2) (second dependent))
                          ordinary-fn?))
                   @values))
    (support/check! context "fmap, filter, sampled-from, and one-of preserve their contracts"
           (every? (fn [{:keys [mapped filtered sampled chosen]}]
                     (and (even? mapped)
                          (even? filtered)
                          (contains? #{:a :b :c} sampled)
                          (contains? #{:left :right} chosen)))
                   @values))
    (support/check! context "bind supports dependent fixed-size collection draws"
           (every? (fn [{:keys [bound]}]
                     (and (<= 1 (count (first bound)) 5)
                          (= (count (first bound))
                             (count (second bound)))))
                   @values))
    (support/check! context "chunkings preserve payloads with nonempty chunks"
           (every?
            (fn [{:keys [chunks empty-chunks single-chunks]}]
              (and (vector? chunks)
                   (every? #(and (vector? %) (pos? (count %))) chunks)
                   (= [0 1 2 3 4 5] (vec (mapcat identity chunks)))
                   (= [] empty-chunks)
                   (= [[7]] single-chunks)))
            @values))
    (support/check! context "vector, list, set, and map shapes and bounds are enforced"
           (every?
            (fn [{:keys [vector list set map]}]
              (and (vector? vector)
                   (<= 3 (count vector) 5)
                   (= (count vector) (count (distinct vector)))
                   (list? list) (= 3 (count list))
                   (set? set) (<= 2 (count set) 4)
                   (map? map) (<= 2 (count map) 4)))
            @values))
    (support/check! context "sorted collections, tuples, hmaps, and optional values work"
           (every?
            (fn [{:keys [sorted-set sorted-map tuple hmap optional]}]
              (and (sorted? sorted-set) (set? sorted-set)
                   (sorted? sorted-map) (map? sorted-map)
                   (= 2 (count tuple))
                   (= "Ada" (:name hmap))
                   (<= 0 (:age hmap) 100)
                   (contains? #{nil 1} optional)))
            @values)))
  (support/check! context "empty sampled-from and one-of inputs are rejected"
         (and (support/throws? #(g/sampled-from []))
              (support/throws? #(g/one-of []))))
  (let [capture (fn [call]
                  (try
                    (call)
                    nil
                    (catch Throwable error error)))
        one-of-error (capture #(g/one-of [(g/just :ok) :invalid :later]))
        tuple-error (capture #(g/tuple (g/just :ok) :invalid :later))]
    (support/check! context "collection combinators validate generators eagerly in order"
           (and (= {:type ::g/invalid-option
                    :operation "one-of"
                    :value :invalid}
                   (select-keys (ex-data one-of-error)
                                [:type :operation :value]))
                (= {:type ::g/invalid-option
                    :operation "tuple"
                    :value :invalid}
                   (select-keys (ex-data tuple-error)
                                [:type :operation :value]))
                (= "one-of requires a generator" (ex-message one-of-error))
                (= "tuple requires a generator" (ex-message tuple-error)))))
  (support/check! context "inverted collection bounds are rejected"
         (support/throws? #(g/vector {:min-size 3 :max-size 2} (g/boolean)))))

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

(defn combinator-shrink-quality [context]
  (let [string-case
        (minimal-value "hegel.test-runner:string-length"
                       (g/string)
                       #(>= (support/codepoint-count %) 10))
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
    (support/check! context "text shrinking matches Hegel's ten-zero minimum"
           (= "0000000000" (:value string-case)))
    (support/check! context "collection shrinking keeps only the required contained value"
           (= [42] (:value vector-case)))
    (support/check! context "flat-map shrinking minimizes both dependent sides"
           (= [true true true] (:value bind-case)))
    (support/check! context "set shrinking finds one of Hegel's canonical adjacent triples"
           (contains? #{#{0 1 2} #{-1 0 1}} (:value set-case)))
    (support/check! context "one-of shrinking prefers the simpler failing branch"
           (= [:boolean true] (:value one-of-case)))
    (support/check! context "all cross-binding shrink cases reproduce without flakiness"
           (every? (fn [{:keys [result failure]}]
                     (and (not (:passed? result))
                          (:reproduced? failure)
                          (false? (:flaky? result))))
                   [string-case vector-case bind-case set-case one-of-case]))))

(defn- recursive-tree-generator
  ([]
   (recursive-tree-generator {}))
  ([opts]
   (g/recursive
    opts
    (g/fmap (fn [value] [:leaf value]) (g/integer 0 20))
    (fn [subtree]
      ;; Reusing the same subtree generator is the public contract: every
      ;; draw advances the shared recursion scope at the appropriate depth.
      (g/fmap (fn [[left right]] [:branch left right])
              (g/tuple subtree subtree))))))

(defn- tree-height [tree]
  (if (= :leaf (first tree))
    0
    (inc (max (tree-height (second tree))
              (tree-height (nth tree 2))))))

(defn- tree-leaf-count [tree]
  (if (= :leaf (first tree))
    1
    (+ (tree-leaf-count (second tree))
       (tree-leaf-count (nth tree 2)))))

(defn- tree-has-odd-leaf-pair? [tree]
  (and (= :branch (first tree))
       (let [left (second tree)
             right (nth tree 2)]
         (or (and (= :leaf (first left))
                  (= :leaf (first right))
                  (odd? (second left))
                  (odd? (second right)))
             (tree-has-odd-leaf-pair? left)
             (tree-has-odd-leaf-pair? right)))))

(defn recursive-generators [context]
  (support/check! context "recursive construction rejects invalid options and declarations"
         (and (support/throws? #(g/recursive [] (g/just :leaf) identity))
              (support/throws? #(g/recursive {:max-depth -1}
                                     (g/just :leaf) identity))
              (support/throws? #(g/recursive {:max-leaves (inc hffi/no-max-size)}
                                     (g/just :leaf) identity))
              (support/throws? #(g/recursive {:unknown true}
                                     (g/just :leaf) identity))
              (support/throws? #(g/recursive :not-a-generator identity))
              (support/throws? #(g/recursive (g/just :leaf) :not-a-function))))
  (let [values (atom [])
        recursion-counts (atom {:new 0 :free 0})
        new-recursion! hffi/new-recursion!
        recursion-free! hffi/recursion-free!
        result
        (with-redefs
         [hffi/new-recursion!
          (fn [& args]
            (swap! recursion-counts update :new inc)
            (apply new-recursion! args))
          hffi/recursion-free!
          (fn [& args]
            (swap! recursion-counts update :free inc)
            (apply recursion-free! args))]
         (h/run-test!
          {:test-cases 120
           :seed 20260828
           :database ""
           :verbosity :quiet}
          (fn [_]
            (swap! values conj
                   (h/draw!
                    (recursive-tree-generator
                     {:max-depth 3 :max-leaves 6}))))))]
    (support/check! context "recursive generation respects depth and leaf bounds"
           (and (:passed? result)
                (seq @values)
                (every? #(<= (tree-height %) 3) @values)
                (every? #(<= (tree-leaf-count %) 6) @values)))
    (support/check! context "recursive generation produces nested branches"
           (some #(>= (tree-height %) 2) @values))
    (support/check! context "real recursive scopes are freed exactly once"
           (and (pos? (:new @recursion-counts))
                (= (:new @recursion-counts)
                   (:free @recursion-counts)))))
  (let [{:keys [result value failure]}
        (minimal-value
         "hegel.test-runner:recursive-hoist"
         (recursive-tree-generator {:max-depth 3 :max-leaves 8})
         tree-has-odd-leaf-pair?)]
    (support/check! context "recursive shrinking hoists a deep witness to the root"
           (= [:branch [:leaf 1] [:leaf 1]] value))
    (support/check! context "recursive shrinking reproduces without flakiness"
           (and (not (:passed? result))
                (:reproduced? failure)
                (false? (:flaky? result))))))

(defn recursive-retry-protocol [context]
  (let [events (atom [])
        branch-decisions (atom [true false false])
        leaf-results (atom [:retry :ok])
        generator
        (g/recursive
         {:max-depth 2 :max-leaves 1}
         (g/just :leaf)
         (fn [subtree]
           (g/fmap (fn [child] [:branch child]) subtree)))
        value
        (with-redefs
         [hffi/new-recursion!
          (fn [_ _ max-depth max-leaves]
            (swap! events conj [:new max-depth max-leaves])
            :recursion)
          hffi/start-span!
          (fn [_ _ label] (swap! events conj [:start label]))
          hffi/stop-span!
          (fn [& _] (swap! events conj [:stop]))
          hffi/recursion-branch!
          (fn [_ _ _ depth]
            (let [decision (first @branch-decisions)]
              (swap! branch-decisions subvec 1)
              (swap! events conj [:branch depth decision])
              decision))
          hffi/recursion-leaf!
          (fn [& _]
            (let [result (first @leaf-results)]
              (swap! leaf-results subvec 1)
              (swap! events conj [:leaf result])
              result))
          hffi/recursion-retry!
          (fn [& _] (swap! events conj [:retry]))
          hffi/recursion-finish!
          (fn [& _]
            (swap! events conj [:finish :ok])
            :ok)
          hffi/recursion-free!
          (fn [& _] (swap! events conj [:free]))]
         (generator {:context :context :handle :test-case}))
        retry-index (.indexOf @events [:retry])
        first-stop-index (.indexOf @events [:stop])]
    (support/check! context "leaf-budget retry unwinds to the recursive root"
           (and (= :leaf value)
                (pos? retry-index)
                (or (= -1 first-stop-index)
                    (> first-stop-index retry-index))))
    (support/check! context "leaf-budget retry preserves nested recursive span order"
           (= [[:new 2 1]
               [:start hffi/label-recursive]
               [:branch 0 true]
               [:start hffi/label-mapped]
               [:start hffi/label-recursive]
               [:branch 1 false]
               [:leaf :retry]
               [:retry]
               [:start hffi/label-recursive]
               [:branch 0 false]
               [:leaf :ok]
               [:finish :ok]
               [:stop]
               [:free]]
              @events)))
  (let [events (atom [])
        finish-results (atom [:retry :ok])
        generator (g/recursive (g/just :leaf) identity)
        value
        (with-redefs
         [hffi/new-recursion! (fn [& _] :recursion)
          hffi/start-span! (fn [& _] (swap! events conj :start))
          hffi/stop-span! (fn [& _] (swap! events conj :stop))
          hffi/recursion-branch! (fn [& _] false)
          hffi/recursion-leaf! (fn [& _] :ok)
          hffi/recursion-retry! (fn [& _] (swap! events conj :retry))
          hffi/recursion-finish!
          (fn [& _]
            (let [result (first @finish-results)]
              (swap! finish-results subvec 1)
              (swap! events conj result)
              result))
          hffi/recursion-free! (fn [& _] (swap! events conj :free))]
         (generator {:context :context :handle :test-case}))]
    (support/check! context "finish retry restarts directly without recursion-retry"
           (and (= :leaf value)
                (= [:start :retry :start :ok :stop :free] @events))))
  (let [events (atom [])
        generator (g/recursive (g/just :leaf) (fn [_] :not-a-generator))]
    (support/check! context "recursive user errors close their span and free their scope"
           (and
            (with-redefs
             [hffi/new-recursion! (fn [& _] :recursion)
              hffi/start-span! (fn [& _] (swap! events conj :start))
              hffi/stop-span! (fn [& _] (swap! events conj :stop))
              hffi/recursion-branch! (fn [& _] true)
              hffi/recursion-free! (fn [& _] (swap! events conj :free))]
             (support/throws? #(generator {:context :context :handle :test-case})))
            (= [:start :stop :free] @events)))))
