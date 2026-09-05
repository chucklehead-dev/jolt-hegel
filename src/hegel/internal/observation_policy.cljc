(ns hegel.internal.observation-policy
  "Frontend observation policy; independent of native printed statistics."
  (:require [clojure.string :as str]
            [hegel.host :as host]
            [hegel.internal.observations :as observations]
            [hegel.internal.portable-data :as portable-data]
            [hegel.validation :as validation]))

(defn- invalid! [message data]
  (validation/usage-error! :hegel.observation/invalid-option message data))

(defn- unicode-scalar-text? [text]
  ;; Jolt sequences Unicode codepoints; JVM/BB sequence UTF-16 code units.
  ;; Accept the same scalar strings without replacing malformed surrogates.
  (loop [chars (seq text)]
    (if-let [c (first chars)]
      (let [n (int c)]
        (cond
          (<= 55296 n 56319)
          (if (= :jolt (host/runtime))
            ;; A surrogate is not a scalar on codepoint-indexed Jolt, even
            ;; when followed by another surrogate. JVM/BB store valid pairs.
            false
            (if-let [next-char (second chars)]
              (and (<= 56320 (int next-char) 57343) (recur (nnext chars)))
              false))
          (or (<= 56320 n 57343) (> n 1114111)) false
          :else (recur (next chars))))
      true)))

(defn label! [label]
  (when-not (and (string? label)
                 (<= (count label) 256)
                 (not (str/blank? label))
                 (not (str/includes? label "\u0000"))
                 (<= (portable-data/text-size label) 256)
                 (unicode-scalar-text? label))
    (invalid! "observation label must be nonblank Unicode text without NUL, at most 256 UTF-16 code units"
              {}))
  label)

(defn finite-value! [value]
  (when-not (number? value)
    (invalid! "numeric observation must be a finite number representable as a double" {}))
  (let [converted (host/try-catch-all (double value) _ nil)]
    (when-not (and (some? converted) (< ##-Inf converted ##Inf))
      (invalid! "numeric observation must be a finite number representable as a double" {}))
    converted))

(defn scope [opts]
  (if (= #{:generate} (set (:phases opts))) :generation-only :exploration))

(defn validate-coverage! [opts]
  (when (contains? opts :coverage)
    (let [coverage (:coverage opts)
          requirements (:requirements coverage)]
      (validation/reject-unknown-keys! :hegel.observation/invalid-option
                                       "coverage" #{:scope :requirements} coverage)
      (when-not (contains? #{:generation-only :exploration} (:scope coverage))
        (invalid! "coverage requires explicit :scope :generation-only or :exploration" {}))
      (when (and (= :generation-only (:scope coverage))
                 (not= :generation-only (scope opts)))
        (invalid! "generation-only coverage requires explicit :phases [:generate]; phases are never changed implicitly" {}))
      (when-not (and (map? requirements) (<= 1 (count requirements) 256))
        (invalid! "coverage :requirements must contain 1 through 256 categorical labels" {}))
      (doseq [[label requirement] requirements]
        (label! label)
        (validation/reject-unknown-keys! :hegel.observation/invalid-option
                                         "coverage requirement" #{:min-count :min-fraction}
                                         requirement)
        (when (contains? requirement :min-count)
          (validation/require-integer-range! :hegel.observation/invalid-option
                                             :min-count (:min-count requirement)
                                             1 18446744073709551615N))
        (when (contains? requirement :min-fraction)
          (let [fraction (finite-value! (:min-fraction requirement))]
            (when-not (<= 0.0 fraction 1.0)
              (invalid! "coverage :min-fraction must be between zero and one" {})))))))
  opts)

(defn initial [opts]
  (when (or (:observations? opts) (contains? opts :coverage))
    {:scope (scope opts)
     :phases (if (contains? opts :phases) (vec (:phases opts)) :all)
     :exploration (observations/empty-summary)
     :final-replay (observations/empty-summary)}))

(defn- check-coverage [summary coverage]
  (let [valid-cases (get-in summary [:cases :valid])
        checks
        (mapv (fn [[label requirement]]
                (let [hits (get-in summary [:events label :valid] 0)
                      minimum (get requirement :min-count 1)
                      fraction (when (pos? valid-cases) (/ (double hits) valid-cases))
                      minimum-fraction (double (get requirement :min-fraction 0.0))]
                  {:label label :hits hits :valid-cases valid-cases
                   :fraction fraction :min-count minimum :min-fraction minimum-fraction
                   :passed? (boolean (and (pos? valid-cases)
                                          (>= hits minimum)
                                          ;; Compare exact counts against the
                                          ;; decimal value of the normalized
                                          ;; double threshold. Display rounding
                                          ;; must not turn n-1 of n into 100%.
                                          (>= (/ hits valid-cases)
                                              (rationalize minimum-fraction))))}))
              (sort-by key (:requirements coverage)))]
    {:scope (:scope coverage)
     :valid-cases valid-cases
     :passed? (every? :passed? checks)
     :checks checks}))

(defn finish [result opts observed]
  (if-not observed
    result
    (let [result (assoc result :observations observed)]
      (if-let [coverage (:coverage opts)]
        (let [verdict (check-coverage (:exploration observed) coverage)]
          ;; Coverage is run-level evidence, never a fabricated shrinkable
          ;; counterexample. Preserve a native failure/error/flaky verdict.
          (cond-> (assoc result :coverage verdict)
            (and (:passed? result) (not (:passed? verdict)))
            (assoc :passed? false :status :coverage-failed)))
        result))))
