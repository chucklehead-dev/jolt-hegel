(ns hegel.stateful.journal
  "Opt-in sanitized execution journals for sequential state machines."
  (:refer-clojure :exclude [run!])
  (:require [hegel.core :as h]
            [hegel.internal.portable-data :as data]
            [hegel.internal.portable-edn :as edn]
            [hegel.replay-bundle :as bundle]
            [hegel.stateful :as hs]
            [hegel.validation :as validation]))

(def ^:dynamic ^:private *append* nil)

(defn- invalid! [reason]
  (validation/usage-error! ::invalid-journal "invalid stateful execution journal" {:reason reason}))

(defn entry!
  "During confirmed execution record one explicitly projected command event.
  Never affects the property return value. The current rule supplies :rule."
  [entry]
  (when *append* (*append* entry))
  nil)

(defn- append! [journal opts rule entry]
  (let [{:keys [events bytes truncated?]} @journal]
    (when-not truncated?
      (if (>= (count events) (:max-entries opts))
        (swap! journal assoc :truncated? true)
        (let [safe (try ((:redact opts) entry)
                        (catch Throwable _ (invalid! :redaction-failed)))]
          (when-not (and (map? safe) (not (record? safe))
                         (every? #{:input :outcome :model-before :model-after} (keys safe)))
            (invalid! :invalid-entry))
          (let [event (assoc safe :rule rule)
                validate #(data/validate! % bundle/limits (fn [_ reason] (invalid! reason)))
                text (edn/encode event bundle/limits invalid! validate)
                utf8 (.getBytes ^String text "UTF-8")
                size (+ bytes 1 (alength utf8))]
            (when-not (= text (String. utf8 "UTF-8")) (invalid! :invalid-unicode))
            (if (> size (:max-bytes opts))
              (swap! journal assoc :truncated? true)
              ;; Keep only decoded portable data, including no object metadata.
              (reset! journal {:events (conj events (edn/decode text bundle/limits invalid! validate))
                               :bytes size :truncated? false}))))))))

(defn run!
  "Run hs/run! with a bounded per-failure journal. :redact transforms each
  entry before retention. Bounds apply to the encoded EDN events vector."
  [opts machine]
  (validation/reject-unknown-keys! ::invalid-journal "journal" #{:max-entries :max-bytes :redact} opts)
  (let [opts (merge {:max-entries 64 :max-bytes 16384 :redact identity} opts)]
    (validation/require-integer-range! ::invalid-journal :max-entries (:max-entries opts) 1 256)
    (validation/require-integer-range! ::invalid-journal :max-bytes (:max-bytes opts) 2 262144)
    (when-not (fn? (:redact opts)) (invalid! :invalid-redactor))
    (if-not (h/final?)
      (hs/run! machine)
      (let [journal (atom {:events [] :bytes 2 :truncated? false})
            capture (fn [rule attempt]
                      (let [before @journal
                            result (binding [*append* #(append! journal opts rule %)] (attempt))]
                        (when-not (:applied? result) (reset! journal before))
                        result))]
        (try
          (binding [h/*test-case* (assoc (h/current-test-case!) :journal-attempt capture)]
            (hs/run! machine))
          (catch Throwable e
            (if (::hs/phase (ex-data e))
              (throw (ex-info (ex-message e) (assoc (ex-data e) ::journal @journal) e))
              (throw e))))))))

(defn fixture
  "Return the complete redacted event vector for one confirmed failure."
  [failure]
  (let [journal (::journal (ex-data (:exception failure)))]
    (when-not (and journal (:reproduced? failure) (not (:caveat failure)))
      (invalid! :confirmed-journal-required))
    (when (:truncated? journal) (invalid! :incomplete-journal))
    (:events journal)))

(defn to-bundle
  "Export one confirmed origin and its corresponding journal as a schema-v1 trace."
  [provenance result origin]
  (let [matches (filterv #(= origin (:origin %)) (:failures result))]
    (when-not (= 1 (count matches)) (invalid! :unique-origin-required))
    (bundle/from-result provenance (assoc result :failures matches)
                        {:trace {:contract-id "hegel.stateful/journal" :contract-revision "1"
                                 :events (fixture (first matches))}})))
