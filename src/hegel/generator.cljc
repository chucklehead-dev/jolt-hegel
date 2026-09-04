(ns hegel.generator
  "Composable generators backed by libhegel draws and shrinker spans."
  (:refer-clojure :exclude [boolean bytes double filter integer list map set
                            sorted-map sorted-set string time uuid vector])
  (:require [clojure.string :as str]
            [hegel.core :as h]
            [hegel.ffi :as hffi]
            [hegel.host :as host]))

(def ^:private min-int64 -9223372036854775808N)
(def ^:private max-int64 9223372036854775807)
(def ^:private max-double 1.7976931348623157E308)
(def ^:private min-positive-double 4.9406564584124654E-324)

(defn- invalid-option [message data]
  (throw (ex-info message (assoc data :type ::invalid-option))))

(defn generator?
  "True when value is a generator produced by this namespace."
  [value]
  (and (fn? value) (true? (::generator (meta value)))))

(defn composite-fn
  "Tag `(fn [test-case] value)` as a generator."
  [f]
  (when-not (fn? f)
    (invalid-option "composite-fn requires a function" {:value f}))
  (with-meta f (assoc (meta f) ::generator true)))

(defn boolean
  "Generate a boolean. The optional probability is the chance of true."
  ([]
   (boolean 0.5))
  ([probability]
   (when-not (and (number? probability)
                  (<= 0.0 probability 1.0))
     (invalid-option "boolean probability must be between 0 and 1"
                     {:probability probability}))
   (composite-fn
    (fn [test-case]
      (hffi/generate-boolean! (:context test-case)
                              (:handle test-case)
                              (clojure.core/double probability)
                              false
                              false)))))

(defn integer
  "Generate a signed 64-bit integer between inclusive bounds.

  With no options, uses the complete int64 range. The one-argument form accepts
  {:min x :max y}; the two-argument form is shorthand for those bounds."
  ([]
   (integer min-int64 max-int64))
  ([opts]
   (integer (if (contains? opts :min) (:min opts) min-int64)
            (if (contains? opts :max) (:max opts) max-int64)))
  ([min-value max-value]
   (when (> min-value max-value)
     (throw (ex-info "integer generator minimum exceeds maximum"
                     {:type ::invalid-bounds
                      :min min-value
                      :max max-value})))
   (composite-fn
    (fn [test-case]
      (hffi/generate-integer! (:context test-case)
                              (:handle test-case)
                              min-value
                              max-value)))))

(defn octet
  "Generate an unsigned wire octet as an integer from 0 through 255.

  Convert it with `unchecked-byte` only at APIs that require Jolt's signed byte
  representation."
  []
  (integer 0 255))

(defn double
  "Generate a 64-bit floating-point value.

  Options are :min, :max, :exclude-min?, :exclude-max?, :nan?, and
  :infinity?. With no bounds, NaN and infinity are allowed by default, matching
  Hegel. Supplying either bound disables NaN by default; supplying both bounds
  disables infinity by default."
  ([]
   (double {}))
  ([opts]
   (let [has-min? (some? (:min opts))
         has-max? (some? (:max opts))
         allow-nan? (if (contains? opts :nan?)
                      (clojure.core/boolean (:nan? opts))
                      (and (not has-min?) (not has-max?)))
         allow-infinity? (if (contains? opts :infinity?)
                           (clojure.core/boolean (:infinity? opts))
                           (or (not has-min?) (not has-max?)))
         min-value (if has-min?
                     (clojure.core/double (:min opts))
                     (if allow-infinity?
                       ##-Inf
                       (- max-double)))
         max-value (if has-max?
                     (clojure.core/double (:max opts))
                     (if allow-infinity?
                       ##Inf
                       max-double))]
     (when (> min-value max-value)
       (invalid-option "double generator minimum exceeds maximum"
                       {:min min-value :max max-value}))
     (when (and allow-nan? (or has-min? has-max?))
       (invalid-option "double generator cannot allow NaN with a bound"
                       {:min (:min opts) :max (:max opts)}))
     (when (and allow-infinity? has-min? has-max?)
       (invalid-option
        "double generator cannot allow infinity with both bounds"
        {:min min-value :max max-value}))
     (composite-fn
      (fn [test-case]
        (hffi/generate-float!
         (:context test-case) (:handle test-case)
         64 min-value max-value allow-nan? allow-infinity?
         (:exclude-min? opts) (:exclude-max? opts) min-positive-double)))))
  ([min-value max-value]
   (double {:min min-value :max max-value})))

(defn bytes
  "Generate a byte array with an inclusive length range.

  With no options, lengths range from 0 through 100. The one-argument form
  accepts :min-size and :max-size; when :max-size is omitted it defaults to the
  larger of :min-size and 100."
  ([]
   (bytes {}))
  ([opts]
   (let [min-size (or (:min-size opts) 0)
         max-size (if (some? (:max-size opts))
                    (:max-size opts)
                    (max min-size 100))]
     (bytes min-size max-size)))
  ([min-size max-size]
   (when-not (and (integer? min-size) (integer? max-size)
                  (<= 0 min-size max-size))
     (invalid-option
      "byte generator sizes must be non-negative integers with min <= max"
      {:min-size min-size :max-size max-size}))
   (composite-fn
    (fn [test-case]
      (hffi/generate-bytes! (:context test-case)
                            (:handle test-case)
                            min-size
                            max-size)))))

(defn- canonical-uuid [data]
  (apply str
         (map-indexed
          (fn [index value]
            (str (when (#{4 6 8 10} index) "-")
                 (format "%02x" (bit-and value 0xff))))
          (seq data))))

(defn uuid
  "Generate a canonical lowercase UUID string.

  Pass a version from 1 through 5 to force the RFC 4122 version and variant."
  ([]
   (uuid nil))
  ([version]
   (when (and (some? version)
              (not (and (integer? version) (<= 1 version 5))))
     (invalid-option "UUID version must be an integer from 1 through 5"
                     {:version version}))
   (composite-fn
    (fn [test-case]
      (canonical-uuid
       (hffi/generate-uuid! (:context test-case)
                            (:handle test-case)
                            version))))))

(defn- format-ipv4 [data]
  (str/join "." (clojure.core/map #(str (bit-and % 0xff)) (seq data))))

(defn- ipv6-groups [data]
  (mapv (fn [index]
          (bit-or (bit-shift-left (bit-and (aget data (* 2 index)) 0xff) 8)
                  (bit-and (aget data (inc (* 2 index))) 0xff)))
        (range 8)))

(defn- longest-zero-run [groups]
  (loop [index 0 best-start nil best-length 0]
    (if (= index (count groups))
      (when (>= best-length 2) [best-start best-length])
      (if (zero? (nth groups index))
        (let [end (loop [end index]
                    (if (and (< end (count groups))
                             (zero? (nth groups end)))
                      (recur (inc end))
                      end))
              length (- end index)]
          (if (> length best-length)
            (recur end index length)
            (recur end best-start best-length)))
        (recur (inc index) best-start best-length)))))

(defn- format-ipv6 [data]
  (let [groups (ipv6-groups data)]
    (if-let [[start length] (longest-zero-run groups)]
      (str (str/join ":" (clojure.core/map (fn [group] (format "%x" group))
                                             (subvec groups 0 start)))
           "::"
           (str/join ":" (clojure.core/map
                            (fn [group] (format "%x" group))
                            (subvec groups (+ start length)))))
      (str/join ":" (clojure.core/map (fn [group] (format "%x" group))
                                       groups)))))

(defn ipv4
  "Generate an IPv4 address in dotted-quad form."
  []
  (composite-fn
   (fn [test-case]
     (format-ipv4
      (hffi/generate-ipv4! (:context test-case) (:handle test-case))))))

(defn ipv6
  "Generate an IPv6 address in canonical lowercase colon-hex form."
  []
  (composite-fn
   (fn [test-case]
     (format-ipv6
      (hffi/generate-ipv6! (:context test-case) (:handle test-case))))))

(def ^:private minimum-date {:year 1 :month 1 :day 1})
(def ^:private maximum-date {:year 9999 :month 12 :day 31})
(def ^:private minimum-time
  {:hour 0 :minute 0 :second 0 :microsecond 0})
(def ^:private maximum-time
  {:hour 23 :minute 59 :second 59 :microsecond 999999})
(def ^:private minimum-datetime
  {:date minimum-date :time minimum-time})
(def ^:private maximum-datetime
  {:date maximum-date :time maximum-time})

(defn- leap-year? [year]
  (or (zero? (mod year 400))
      (and (zero? (mod year 4))
           (not (zero? (mod year 100))))))

(defn- days-in-month [year month]
  (cond
    (= 2 month) (if (leap-year? year) 29 28)
    (contains? #{4 6 9 11} month) 30
    :else 31))

(defn- valid-date? [value]
  (and (map? value)
       (every? #(integer? (get value %)) [:year :month :day])
       (<= 1 (:year value) 9999)
       (<= 1 (:month value) 12)
       (<= 1 (:day value)
           (days-in-month (:year value) (:month value)))))

(defn- valid-time? [value]
  (and (map? value)
       (every? #(integer? (get value %))
               [:hour :minute :second :microsecond])
       (<= 0 (:hour value) 23)
       (<= 0 (:minute value) 59)
       (<= 0 (:second value) 59)
       (<= 0 (:microsecond value) 999999)))

(defn- valid-datetime? [value]
  (and (map? value)
       (valid-date? (:date value))
       (valid-time? (:time value))))

(defn- ordered-fields? [minimum maximum fields]
  (loop [[field & more] fields]
    (if field
      (let [comparison (compare (get minimum field) (get maximum field))]
        (cond
          (neg? comparison) true
          (pos? comparison) false
          :else (recur more)))
      true)))

(defn- ordered-dates? [minimum maximum]
  (ordered-fields? minimum maximum [:year :month :day]))

(defn- ordered-times? [minimum maximum]
  (ordered-fields? minimum maximum
                   [:hour :minute :second :microsecond]))

(defn- ordered-datetimes? [minimum maximum]
  (if (= (:date minimum) (:date maximum))
    (ordered-times? (:time minimum) (:time maximum))
    (ordered-dates? (:date minimum) (:date maximum))))

(defn- validate-bounds! [kind valid? ordered? minimum maximum]
  (when-not (and (valid? minimum) (valid? maximum))
    (invalid-option (str kind " bounds contain an invalid value")
                    {:min minimum :max maximum}))
  (when-not (ordered? minimum maximum)
    (invalid-option (str kind " generator minimum exceeds maximum")
                    {:min minimum :max maximum})))

(defn- zero-pad [width value]
  (let [text (str value)]
    (str (apply str (repeat (max 0 (- width (count text))) "0")) text)))

(defn- format-date [value]
  (str (zero-pad 4 (:year value))
       "-" (zero-pad 2 (:month value))
       "-" (zero-pad 2 (:day value))))

(defn- format-time [value]
  (let [base (str (zero-pad 2 (:hour value))
                  ":" (zero-pad 2 (:minute value))
                  ":" (zero-pad 2 (:second value)))]
    (if (zero? (:microsecond value))
      base
      (str base "." (zero-pad 6 (:microsecond value))))))

(defn date
  "Generate an ISO 8601 calendar-date string.

  Options :min and :max are inclusive maps with :year, :month, and :day.
  The default range is 0001-01-01 through 9999-12-31."
  ([]
   (date {}))
  ([opts]
   (let [minimum (if (contains? opts :min) (:min opts) minimum-date)
         maximum (if (contains? opts :max) (:max opts) maximum-date)]
     (validate-bounds! "date" valid-date? ordered-dates? minimum maximum)
     (composite-fn
      (fn [test-case]
        (format-date
         (hffi/generate-date! (:context test-case) (:handle test-case)
                              minimum maximum))))))
  ([minimum maximum]
   (date {:min minimum :max maximum})))

(defn time
  "Generate an ISO 8601 local-time string.

  Options :min and :max are inclusive maps with :hour, :minute, :second, and
  :microsecond. A nonzero fractional part is always rendered with six digits."
  ([]
   (time {}))
  ([opts]
   (let [minimum (if (contains? opts :min) (:min opts) minimum-time)
         maximum (if (contains? opts :max) (:max opts) maximum-time)]
     (validate-bounds! "time" valid-time? ordered-times? minimum maximum)
     (composite-fn
      (fn [test-case]
        (format-time
         (hffi/generate-time! (:context test-case) (:handle test-case)
                              minimum maximum))))))
  ([minimum maximum]
   (time {:min minimum :max maximum})))

(defn datetime
  "Generate an ISO 8601 naive-datetime string.

  Options :min and :max are inclusive {:date {...} :time {...}} maps using the
  fields accepted by `date` and `time`. No timezone component is produced."
  ([]
   (datetime {}))
  ([opts]
   (let [minimum (if (contains? opts :min) (:min opts) minimum-datetime)
         maximum (if (contains? opts :max) (:max opts) maximum-datetime)]
     (validate-bounds!
      "datetime" valid-datetime? ordered-datetimes? minimum maximum)
     (composite-fn
      (fn [test-case]
        (let [{:keys [date time]}
              (hffi/generate-datetime!
               (:context test-case) (:handle test-case) minimum maximum)]
          (str (format-date date) "T" (format-time time)))))))
  ([minimum maximum]
   (datetime {:min minimum :max maximum})))

;; String generators ---------------------------------------------------------

(def ^:private character-filter-options
  [:codec :min-codepoint :max-codepoint :categories :exclude-categories
   :include-characters :exclude-characters])

(defn- normalize-categories [option value]
  (when (some? value)
    (when-not (and (sequential? value) (not (string? value)))
      (invalid-option (str (name option) " must be a collection")
                      {option value}))
    (mapv (fn [category]
            (let [category
                  (cond
                    (string? category) category
                    (keyword? category) (name category)
                    :else
                    (invalid-option
                     (str (name option)
                          " entries must be strings or keywords")
                     {option value :entry category}))]
              (when (str/includes? category "\u0000")
                (invalid-option (str (name option) " entries cannot contain NUL")
                                {option value :entry category}))
              category))
          value)))

(defn- normalize-text-options [opts]
  (when-not (map? opts)
    (invalid-option "string options must be a map" {:options opts}))
  (let [min-size (if (contains? opts :min-size) (:min-size opts) 0)
        max-size (if (contains? opts :max-size)
                   (:max-size opts)
                   (max min-size 100))
        min-codepoint (if (contains? opts :min-codepoint)
                        (:min-codepoint opts)
                        0)
        max-codepoint (if (contains? opts :max-codepoint)
                        (:max-codepoint opts)
                        4294967295)
        alphabet (:alphabet opts)
        has-character-filter?
        (some #(some? (get opts %)) character-filter-options)]
    (when-not (and (integer? min-size) (integer? max-size)
                   (<= 0 min-size max-size))
      (invalid-option
       "string sizes must be non-negative integers with min <= max"
       {:min-size min-size :max-size max-size}))
    (when-not (and (integer? min-codepoint) (integer? max-codepoint)
                   (<= 0 min-codepoint max-codepoint 4294967295))
      (invalid-option
       "string code points must fit uint32 with min <= max"
       {:min-codepoint min-codepoint :max-codepoint max-codepoint}))
    (when (and (some? alphabet) (not (string? alphabet)))
      (invalid-option "string alphabet must be a string"
                      {:alphabet alphabet}))
    (when (and (some? alphabet) has-character-filter?)
      (invalid-option
       "string alphabet cannot be combined with character filters"
       {:alphabet alphabet}))
    (doseq [option [:include-characters :exclude-characters]
            :let [value (get opts option)]
            :when (and (some? value) (not (string? value)))]
      (invalid-option (str (name option) " must be a string")
                      {option value}))
    (let [codec (:codec opts)
          normalized
          {:min-size min-size
           :max-size max-size
           :codec (cond
                    (nil? codec) nil
                    (keyword? codec) (name codec)
                    (string? codec) codec
                    :else
                    (invalid-option "string codec must be a string or keyword"
                                    {:codec codec}))
           :min-codepoint min-codepoint
           :max-codepoint max-codepoint
           :categories (normalize-categories :categories (:categories opts))
           :exclude-categories
           (normalize-categories
            :exclude-categories (:exclude-categories opts))
           :include-characters (:include-characters opts)
           :exclude-characters (:exclude-characters opts)}]
      (when (and (some? (:codec normalized))
                 (str/includes? (:codec normalized) "\u0000"))
        (invalid-option "string codec cannot contain NUL"
                        {:codec (:codec normalized)}))
      (if (some? alphabet)
        (assoc normalized
               :categories []
               :include-characters alphabet)
        normalized))))

(defn- native-string-generator [builder]
  ;; Validate at generator construction, matching the C++ binding. The actual
  ;; immutable native handle is rebuilt per draw so every allocation has a
  ;; deterministic free even though Jolt has no user-facing close protocol.
  (let [validation-context (hffi/context-new!)]
    (try
      (let [handle (builder validation-context)]
        (hffi/string-generator-free! validation-context handle))
      (finally
        (hffi/context-free! validation-context))))
  (composite-fn
   (fn [test-case]
     (let [context (:context test-case)
           handle (builder context)]
       (try
         (hffi/generate-string! context (:handle test-case) handle)
         (finally
           (hffi/string-generator-free! context handle)))))))

(defn string
  "Generate Unicode text.

  Options include :min-size, :max-size, :codec, :min-codepoint,
  :max-codepoint, :categories, :exclude-categories, :include-characters,
  :exclude-characters, and :alphabet. :alphabet, :include-characters, and
  :exclude-characters must be strings, not collections of characters. Sizes
  count Unicode code points."
  ([]
   (string {}))
  ([opts]
   (let [params (normalize-text-options opts)]
     (native-string-generator
      #(hffi/string-generator-text! % params)))))

(defn character
  "Generate one Unicode character, represented as a one-code-point string."
  ([]
   (character {}))
  ([opts]
   (string (assoc opts :min-size 1 :max-size 1))))

(defn regex-str
  "Generate a string matching pattern.

  The whole generated string matches by default. Pass
  {:full-match? false} to allow arbitrary prefix and suffix text."
  ([pattern]
   (regex-str pattern {}))
  ([pattern opts]
   (when (nil? pattern)
     (invalid-option "regex pattern cannot be nil" {:pattern pattern}))
   (when-not (map? opts)
     (invalid-option "regex options must be a map" {:options opts}))
   (let [full-match? (if (contains? opts :full-match?)
                       (clojure.core/boolean (:full-match? opts))
                       true)
         pattern (str pattern)]
     (when (str/includes? pattern "\u0000")
       (invalid-option "regex pattern cannot contain NUL" {:pattern pattern}))
     (native-string-generator
      #(hffi/string-generator-regex! % pattern full-match?)))))

(defn email
  "Generate an RFC 5321/5322 email-address string."
  []
  (native-string-generator hffi/string-generator-email!))

(defn url-str
  "Generate an RFC 3986 HTTP or HTTPS URL string."
  []
  (native-string-generator hffi/string-generator-url!))

(defn domain
  "Generate an RFC 1035 domain name.

  :max-length defaults to 255 and must be between 4 and 255."
  ([]
   (domain {}))
  ([opts]
   (when-not (map? opts)
     (invalid-option "domain options must be a map" {:options opts}))
   (let [max-length (if (contains? opts :max-length)
                      (:max-length opts)
                      255)]
     (when-not (and (integer? max-length) (<= 4 max-length 255))
       (invalid-option "domain max length must be between 4 and 255"
                       {:max-length max-length}))
     (native-string-generator
      #(hffi/string-generator-domain! % max-length)))))

;; Composition and collection generators -----------------------------------

(defn- require-generator! [operation value]
  (when-not (fn? value)
    (invalid-option (str operation " requires a generator")
                    {:operation operation :value value}))
  value)

(defn- require-generators! [operation values]
  (doseq [value values]
    (require-generator! operation value)))

(def ^:private leaf-budget-retry ::leaf-budget-retry)
(def ^:private finish-retry ::finish-retry)

(defn- recursion-retry-control? [error]
  (contains? #{leaf-budget-retry finish-retry}
             (:type (ex-data error))))

(defn- in-span [test-case label body]
  (hffi/start-span! (:context test-case) (:handle test-case) label)
  (let [closed? (atom false)]
    (host/try-catch-all
     (let [value (body)]
       ;; Mark first: if stop-span itself fails, the catch path must not call
       ;; it a second time against an already-consumed or invalid span.
       (reset! closed? true)
       (hffi/stop-span! (:context test-case) (:handle test-case))
       value)
     error
     ;; The engine, not the unwinding frontend, discards every still-open span
     ;; in an abandoned recursive attempt. Stopping one here would pop the
     ;; innermost recursive span instead of this combinator's span.
     (when (and (not @closed?)
                (not (recursion-retry-control? error)))
       (reset! closed? true)
       (hffi/stop-span! (:context test-case) (:handle test-case)))
     (throw error))))

(defn fmap
  "Transform values from generator with f while preserving shrink structure."
  [f generator]
  (require-generator! "fmap" generator)
  (when-not (fn? f)
    (invalid-option "fmap requires a function" {:value f}))
  (composite-fn
   (fn [test-case]
     (in-span test-case hffi/label-mapped
              #(f (generator test-case))))))

(defn bind
  "Draw from generator, then draw from the generator returned by f."
  [f generator]
  (require-generator! "bind" generator)
  (when-not (fn? f)
    (invalid-option "bind requires a function" {:value f}))
  (composite-fn
   (fn [test-case]
     (in-span
      test-case hffi/label-flat-map
      #(let [next-generator (f (generator test-case))]
         (require-generator! "bind result" next-generator)
         (next-generator test-case))))))

(defn recursive
  "Generate recursively defined values.

  leaf generates base values. branch-fn is called afresh for every branch
  with a reusable generator for child values and must return a generator.
  Options are :max-depth (default 32) and :max-leaves (default 100)."
  ([leaf branch-fn]
   (recursive {} leaf branch-fn))
  ([opts leaf branch-fn]
   (when-not (map? opts)
     (invalid-option "recursive options must be a map" {:options opts}))
   (let [unknown (seq (remove #{:max-depth :max-leaves} (keys opts)))
         max-depth (if (contains? opts :max-depth) (:max-depth opts) 32)
         max-leaves (if (contains? opts :max-leaves) (:max-leaves opts) 100)]
     (when unknown
       (invalid-option "recursive received unknown options"
                       {:options (vec unknown)}))
     (doseq [[option value] [[:max-depth max-depth]
                             [:max-leaves max-leaves]]]
       (when-not (and (integer? value) (<= 0 value hffi/no-max-size))
         (invalid-option
          (str "recursive " (name option) " must fit uint64")
          {option value})))
     (require-generator! "recursive leaf" leaf)
     (when-not (fn? branch-fn)
       (invalid-option "recursive requires a branch function"
                       {:value branch-fn}))
     (composite-fn
      (fn [test-case]
        (let [context (:context test-case)
              handle (:handle test-case)
              recursion
              (hffi/new-recursion!
               context handle max-depth max-leaves)]
          (try
            (letfn [(draw-subtree! [current-test-case depth]
                      (let [current-context (:context current-test-case)
                            current-handle (:handle current-test-case)
                            closed? (atom false)]
                        (hffi/start-span!
                         current-context current-handle hffi/label-recursive)
                        (host/try-catch-all
                          (let [branch?
                                (hffi/recursion-branch!
                                 current-context current-handle recursion depth)
                                value
                                (if branch?
                                  (let [subtree
                                        (composite-fn
                                         (fn [child-test-case]
                                           (draw-subtree!
                                            child-test-case (inc depth))))
                                        branch-generator (branch-fn subtree)]
                                    (require-generator!
                                     "recursive branch result" branch-generator)
                                    (branch-generator current-test-case))
                                  (do
                                    (when (= :retry
                                             (hffi/recursion-leaf!
                                              current-context current-handle
                                              recursion))
                                      (throw
                                       (ex-info
                                        "recursive leaf budget exceeded"
                                        {:type leaf-budget-retry})))
                                    (leaf current-test-case)))]
                            ;; Finish while the root recursive span is open. On
                            ;; retry, libhegel has already discarded that span.
                            (when (and (zero? depth)
                                       (= :retry
                                          (hffi/recursion-finish!
                                           current-context current-handle
                                           recursion)))
                              (throw
                               (ex-info "recursive attempt was mispriced"
                                        {:type finish-retry})))
                            (reset! closed? true)
                            (hffi/stop-span!
                             current-context current-handle)
                            value)
                          error
                          (when (and (not @closed?)
                                     (not (recursion-retry-control? error)))
                            (reset! closed? true)
                            (hffi/stop-span!
                             current-context current-handle))
                          (throw error))))]
              (loop []
                (let [attempt
                      (host/try-catch-all
                        {:status :accepted
                         :value (draw-subtree! test-case 0)}
                        error
                        (case (:type (ex-data error))
                          ::leaf-budget-retry
                          {:status :leaf-budget-retry}
                          ::finish-retry
                          {:status :finish-retry}
                          (throw error)))]
                  (case (:status attempt)
                    :accepted (:value attempt)
                    :leaf-budget-retry
                    (do
                      (hffi/recursion-retry! context handle recursion)
                      (recur))
                    :finish-retry (recur)))))
            (finally
              (hffi/recursion-free! context recursion)))))))))

(defn filter
  "Keep values satisfying pred. Three failed attempts reject the test case."
  [pred generator]
  (require-generator! "filter" generator)
  (when-not (fn? pred)
    (invalid-option "filter requires a predicate function" {:value pred}))
  (composite-fn
   (fn [test-case]
     (loop [attempt 0]
       (hffi/start-span! (:context test-case) (:handle test-case)
                         hffi/label-filter)
       (let [[accepted? value]
             (let [closed? (atom false)]
               (host/try-catch-all
                 (let [value (generator test-case)
                       accepted? (clojure.core/boolean (pred value))]
                   (reset! closed? true)
                   (hffi/stop-span! (:context test-case) (:handle test-case)
                                    (not accepted?))
                   [accepted? value])
                 error
                 (when (and (not @closed?)
                            (not (recursion-retry-control? error)))
                   (reset! closed? true)
                   (hffi/stop-span! (:context test-case)
                                    (:handle test-case)))
                 (throw error)))]
         (if accepted?
           value
           (if (< attempt 2)
             (recur (inc attempt))
             (h/assume! test-case false))))))))

(defn just
  "Return a generator which always produces value without making a draw."
  [value]
  (composite-fn (fn [_] value)))

(defn sampled-from
  "Generate one value from a non-empty collection."
  [values]
  (let [values (vec values)]
    (when (empty? values)
      (invalid-option "sampled-from requires at least one value" {}))
    (composite-fn
     (fn [test-case]
       (let [index (hffi/generate-integer!
                    (:context test-case) (:handle test-case)
                    0 (dec (count values)))]
         (nth values index))))))

(defn one-of
  "Draw from one of a non-empty collection of generators."
  [generators]
  (let [generators (vec generators)]
    (when (empty? generators)
      (invalid-option "one-of requires at least one generator" {}))
    (require-generators! "one-of" generators)
    (composite-fn
     (fn [test-case]
       (in-span
        test-case hffi/label-one-of
        #(let [index (hffi/generate-integer!
                      (:context test-case) (:handle test-case)
                      0 (dec (count generators)))]
           ((nth generators index) test-case)))))))

(defn optional
  "Generate nil or a value from generator."
  [generator]
  (require-generator! "optional" generator)
  (composite-fn
   (fn [test-case]
     (in-span
      test-case hffi/label-optional
      #(when (hffi/generate-boolean!
              (:context test-case) (:handle test-case) 0.5 false false)
         (generator test-case))))))

(defn tuple*
  "Generate a vector containing one draw from each generator in generators."
  [generators]
  (let [generators (vec generators)]
    (require-generators! "tuple" generators)
    (if (empty? generators)
      (just [])
      (composite-fn
       (fn [test-case]
         (in-span test-case hffi/label-tuple
                  #(mapv (fn [generator] (generator test-case))
                         generators)))))))

(defn tuple
  "Generate a fixed-size vector with one value from each generator."
  [& generators]
  (tuple* generators))

(defn- collection-bounds [kind opts]
  (when-not (map? opts)
    (invalid-option (str kind " options must be a map") {:options opts}))
  (let [size (when (contains? opts :size) (:size opts))
        min-size (cond
                   (contains? opts :min-size) (:min-size opts)
                   (some? size) size
                   :else 0)
        max-size (cond
                   (contains? opts :max-size) (:max-size opts)
                   (some? size) size
                   :else hffi/no-max-size)]
    (when-not (and (integer? min-size) (integer? max-size)
                   (<= 0 min-size max-size))
      (invalid-option
       (str kind " sizes must be non-negative integers with min <= max")
       {:min-size min-size :max-size max-size}))
    [min-size max-size]))

(defn vector
  "Generate a vector. Options: :size, :min-size, :max-size, :unique?."
  ([elements]
   (vector {} elements))
  ([opts elements]
   (require-generator! "vector" elements)
   (let [[min-size max-size] (collection-bounds "vector" opts)
         unique? (clojure.core/boolean (:unique? opts))]
     (composite-fn
      (fn [test-case]
        (in-span
         test-case hffi/label-list
         (fn []
           (let [collection
                 (hffi/new-collection!
                  (:context test-case) (:handle test-case) min-size max-size)]
             (try
               (loop [result []]
                 (if (hffi/collection-more!
                      (:context test-case) (:handle test-case) collection)
                   (let [value (elements test-case)]
                     (if (and unique? (some #(= value %) result))
                       (do
                         (hffi/collection-reject!
                          (:context test-case) (:handle test-case)
                          collection "duplicate element")
                         (recur result))
                       (recur (conj result value))))
                   result))
               (finally
                 (hffi/collection-free!
                  (:context test-case) collection)))))))))))

(defn- split-by-sizes [payload sizes]
  (loop [remaining (vec payload)
         sizes (seq sizes)
         chunks []]
    (if (empty? remaining)
      chunks
      (let [requested (or (first sizes) (count remaining))
            chunk-size (min (max 1 requested) (count remaining))]
        (recur (subvec remaining chunk-size)
               (next sizes)
               (conj chunks (subvec remaining 0 chunk-size)))))))

(defn chunkings
  "Generate vector chunks whose concatenation equals payload.

  Every chunk of a nonempty payload is nonempty. Shrinking removes planned
  splits and moves retained chunk sizes toward one."
  [payload]
  (let [payload (vec payload)
        size (count payload)]
    (cond
      (zero? size) (just [])
      (= 1 size) (just [payload])
      :else
      (fmap #(split-by-sizes payload %)
            (vector {:max-size (dec size)} (integer 1 size))))))

(defn list
  "Generate a Clojure list. Accepts the same options as vector."
  ([elements]
   (list {} elements))
  ([opts elements]
   (fmap #(apply clojure.core/list %)
         (vector opts elements))))

(defn set
  "Generate a set. Options: :size, :min-size, and :max-size."
  ([elements]
   (set {} elements))
  ([opts elements]
   (require-generator! "set" elements)
   (let [[min-size max-size] (collection-bounds "set" opts)]
     (composite-fn
      (fn [test-case]
        (in-span
         test-case hffi/label-set
         (fn []
           (let [collection
                 (hffi/new-collection!
                  (:context test-case) (:handle test-case) min-size max-size)]
             (try
               (loop [result #{}]
                 (if (hffi/collection-more!
                      (:context test-case) (:handle test-case) collection)
                   (let [value (elements test-case)]
                     (if (contains? result value)
                       (do
                         (hffi/collection-reject!
                          (:context test-case) (:handle test-case)
                          collection "duplicate element")
                         (recur result))
                       (recur (conj result value))))
                   result))
               (finally
                 (hffi/collection-free!
                  (:context test-case) collection)))))))))))

(defn sorted-set
  "Generate a sorted set. Accepts the same options as set."
  ([elements]
   (sorted-set {} elements))
  ([opts elements]
   (fmap #(into (clojure.core/sorted-set) %)
         (set opts elements))))

(defn map
  "Generate a map. Options: :size, :min-size, and :max-size."
  ([keys values]
   (map {} keys values))
  ([opts keys values]
   (require-generator! "map keys" keys)
   (require-generator! "map values" values)
   (let [[min-size max-size] (collection-bounds "map" opts)]
     (composite-fn
      (fn [test-case]
        (in-span
         test-case hffi/label-map
         (fn []
           (let [collection
                 (hffi/new-collection!
                  (:context test-case) (:handle test-case) min-size max-size)]
             (try
               (loop [result {}]
                 (if (hffi/collection-more!
                      (:context test-case) (:handle test-case) collection)
                   (let [key (keys test-case)]
                     (if (contains? result key)
                       (do
                         (hffi/collection-reject!
                          (:context test-case) (:handle test-case)
                          collection "duplicate key")
                         (recur result))
                       (recur (assoc result key (values test-case)))))
                   result))
               (finally
                 (hffi/collection-free!
                  (:context test-case) collection)))))))))))

(defn sorted-map
  "Generate a sorted map. Accepts the same options as map."
  ([keys values]
   (sorted-map {} keys values))
  ([opts keys values]
   (fmap #(into (clojure.core/sorted-map) %)
         (map opts keys values))))

(defn hmap
  "Generate a map with fixed keys and a distinct generator for each value."
  [generators-by-key]
  (when-not (map? generators-by-key)
    (invalid-option "hmap requires a map of generators"
                    {:value generators-by-key}))
  (let [map-keys (vec (keys generators-by-key))]
    (fmap #(zipmap map-keys %)
          (tuple* (mapv generators-by-key map-keys)))))

(defmacro let
  "Like clojure.core/let, drawing right-hand sides tagged as generators.

  Ordinary expressions remain ordinary values, so later bindings can depend
  on values drawn by earlier bindings. Wrap a custom generator function with
  composite-fn so this macro can distinguish it from a function value."
  [binding-forms & body]
  (when-not (and (vector? binding-forms) (even? (count binding-forms)))
    (throw #?(:cljr
              (System.ArgumentException.
               "hegel.generator/let requires an even binding vector")
              :jank
              (ex-info "hegel.generator/let requires an even binding vector"
                       {:type ::invalid-bindings})
              :default
              (IllegalArgumentException.
               "hegel.generator/let requires an even binding vector"))))
  (clojure.core/let
   [expanded
    (reduce
     (fn [result [lhs rhs]]
       (clojure.core/let [temporary (gensym "generated__")]
         (into result
               [temporary rhs
                lhs `(if (hegel.generator/generator? ~temporary)
                       (hegel.core/draw! ~temporary ~(pr-str lhs))
                       ~temporary)])))
     []
     (partition 2 binding-forms))]
   `(clojure.core/let [~@expanded]
      ~@body)))
