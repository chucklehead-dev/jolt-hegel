(ns hegel.internal.observations
  "Pure bounded aggregation for internal event observations."
  (:require [hegel.validation :as validation]))

(def ^:private max-labels 256)

(defn- labels [data]
  (let [events (:events data)]
    (into (if (set? events) events (set (keys events)))
          (keys (:numeric data)))))

(defn- ensure-label-capacity! [existing label]
  ;; Labels are trusted at this layer, but their cardinality is still a hard
  ;; resource boundary.  Do not expose a possibly arbitrary label in errors.
  (when (> (count (conj existing label)) max-labels)
    (validation/usage-error! :hegel.observation/too-many-labels
                             "too many observation labels"
                             {})))

(defn empty-case []
  {:events #{} :numeric {}})

(defn event
  "Record one categorical label for a case, idempotently."
  [case-data label]
  (ensure-label-capacity! (labels case-data) label)
  (update case-data :events conj label))

(defn observe
  "Aggregate one validated finite numeric observation without retaining it."
  [case-data label finite-double]
  (ensure-label-capacity! (labels case-data) label)
  (update-in case-data [:numeric label]
             (fn [summary]
               (if summary
                 {:count (inc (:count summary))
                  :min (min (:min summary) finite-double)
                  :max (max (:max summary) finite-double)}
                 {:count 1 :min finite-double :max finite-double}))))

(defn empty-summary []
  {:cases {:valid 0 :invalid 0 :overrun 0 :interesting 0}
   :events {}
   :numeric {}})

(defn- merge-numeric [summary observation]
  {:count (+ (:count summary 0) (:count observation))
   :min (if summary (min (:min summary) (:min observation))
            (:min observation))
   :max (if summary (max (:max summary) (:max observation))
            (:max observation))})

(defn record-case
  "Merge one completed case into the aggregate summary.

  Event labels increment once per case. Numeric aggregates preserve only their
  count/min/max, partitioned by the case outcome."
  [summary outcome-key case-data]
  (let [case-labels (labels case-data)]
    (when (> (count (into (labels summary) case-labels)) max-labels)
      (validation/usage-error! :hegel.observation/too-many-labels
                               "too many observation labels"
                               {}))
    (let [summary (update-in summary [:cases outcome-key] inc)
          summary (reduce (fn [result label]
                            (update-in result [:events label outcome-key]
                                       (fnil inc 0)))
                          summary
                          (:events case-data))]
      (reduce-kv (fn [result label observation]
                   (update-in result [:numeric label outcome-key]
                              #(merge-numeric % observation)))
                 summary
                 (:numeric case-data)))))
