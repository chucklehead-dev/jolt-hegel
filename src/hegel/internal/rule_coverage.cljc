(ns hegel.internal.rule-coverage
  (:require [hegel.validation :as validation]))

(defn- invalid! [message]
  (validation/usage-error! ::invalid-option message {}))

(defn validate! [opts]
  (when (contains? opts :stateful-coverage)
    (let [{:keys [rules census?] :as config} (:stateful-coverage opts)]
      (validation/reject-unknown-keys! ::invalid-option "stateful coverage" #{:rules :census?} config)
      (when (contains? config :census?)
        (validation/require-boolean! ::invalid-option :census? census?))
      (when (contains? config :rules)
        (when-not (and (map? rules) (<= (count rules) 256))
          (invalid! "stateful coverage :rules must be a map of at most 256 requirements"))
        (doseq [[id req] rules]
          (when-not (or (keyword? id) (string? id) (symbol? id))
            (invalid! "stateful coverage requires a rule name"))
          (validation/reject-unknown-keys! ::invalid-option "rule requirement" #{:min-applied} req)
          (validation/require-integer-range! ::invalid-option :min-applied (:min-applied req)
                                             1 9223372036854775807))))))

(defn register! [counts rules]
  (swap! counts
         (fn [m]
           (let [m (reduce (fn [m rule] (update m (:name rule) #(or % {}))) m rules)]
             (when (> (count m) 256) (invalid! "too many distinct stateful rule names"))
             m))))

(defn hit! [counts id metric]
  (when counts (swap! counts update-in [id metric] (fnil inc 0))))

(defn record-case [summary counts status]
  (let [totals (merge-with (partial merge-with +) (:rules summary) counts)]
    (when (> (count totals) 256) (invalid! "too many distinct stateful rule names"))
    (cond-> (assoc summary :rules totals)
      (= status :valid)
      (update :valid-applied
              (fn [applied]
                (reduce-kv (fn [m id row] (update m id (fnil + 0) (:applied row 0)))
                           applied counts)))
      (= status :interesting) (assoc :stopped? true))))

(defn finish [result opts summary]
  (if-not summary
    result
    (let [checks (into {} (map (fn [[id {:keys [min-applied]}]]
                                 (let [hits (get-in summary [:valid-applied id] 0)]
                                   [id {:applied hits :min-applied min-applied
                                        :passed? (>= hits min-applied)}])))
                       (:rules opts))
          passed? (every? :passed? (vals checks))
          zeroes (cond-> {:selected 0 :guard-passed 0 :guard-rejected 0 :assumption-rejected 0
                          :applied 0 :failed 0 :invariant-failed 0}
                   (:census? opts) (assoc :eligible 0))
          report {:rules (into {} (map (fn [[id row]] [id (merge zeroes row)])) (:rules summary)) :checks checks :passed? passed?
                  :census? (true? (:census? opts))}]
      (cond-> (assoc result :stateful-coverage report)
        (and (:passed? result) (not passed?)) (assoc :passed? false :status :coverage-failed)))))
