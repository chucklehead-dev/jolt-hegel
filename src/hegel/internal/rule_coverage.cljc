(ns hegel.internal.rule-coverage
  (:require [hegel.internal.stateful-id :as id]
            [hegel.validation :as validation]))

(defn- invalid! [message]
  (validation/usage-error! ::invalid-option message {}))

(def thresholds
  {:min-applied :applied :min-selected :selected
   :min-applied-cases :applied-cases :min-selected-cases :selected-cases
   :min-eligible :eligible :min-guard-rejected :guard-rejected})

(defn- unique-ids! [kind requirements]
  (when-not (= (count requirements) (count (set (map #(id/canonical kind %) (keys requirements)))))
    (invalid! "coverage requirements contain duplicate canonical names")))

(defn validate! [opts]
  (when (contains? opts :stateful-coverage)
    (let [{:keys [rules census?] :as config} (:stateful-coverage opts)]
      (validation/reject-unknown-keys! ::invalid-option "stateful coverage" #{:rules :census? :min-final-checks :final-checks} config)
      (when (contains? config :census?)
        (validation/require-boolean! ::invalid-option :census? census?))
      (when (contains? config :min-final-checks)
        (validation/require-integer-range! ::invalid-option :min-final-checks (:min-final-checks config)
                                           1 9223372036854775807))
      (when (contains? config :final-checks)
        (let [requirements (:final-checks config)]
          (when-not (and (map? requirements) (<= (count requirements) 256))
            (invalid! ":final-checks must be a map of at most 256 named minima"))
          (unique-ids! :final-check requirements)
          (doseq [[_ minimum] requirements]
            (validation/require-integer-range! ::invalid-option :min-final-checks minimum 1 9223372036854775807))))
      (when (contains? config :rules)
        (when-not (and (map? rules) (<= (count rules) 256))
          (invalid! "stateful coverage :rules must be a map of at most 256 requirements"))
        (unique-ids! :rule rules)
        (doseq [[id req] rules]
          (when-not (or (keyword? id) (string? id) (symbol? id))
            (invalid! "stateful coverage requires a rule name"))
          (validation/reject-unknown-keys! ::invalid-option "rule requirement" (conj (set (keys thresholds)) :min-selection-ratio) req)
          (when (empty? req) (invalid! "rule coverage requires at least one minimum"))
          (when (and (contains? req :min-eligible) (not census?))
            (invalid! ":min-eligible requires :census? true"))
          (when (contains? req :min-selection-ratio)
            (when-not (and (:min-eligible req) (number? (:min-selection-ratio req))
                           (<= 0 (:min-selection-ratio req) 1))
              (invalid! ":min-selection-ratio requires :min-eligible and a fraction between 0 and 1")))
          (doseq [[minimum value] (dissoc req :min-selection-ratio)]
            (validation/require-integer-range! ::invalid-option minimum value 1 9223372036854775807)))))))

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
      (update :valid-rules
              (fn [valid]
                ;; Machines may spell the same ID differently in this case.
                ;; Combine their applications before incrementing case counts.
                (let [canonical (reduce-kv
                                 (fn [rows rule row]
                                   (update rows (id/canonical :rule rule) #(merge-with + % row)))
                                 {} counts)]
                  (reduce-kv
                   (fn [rows rule row]
                     (let [row (cond-> row
                                 (pos? (get row :selected 0)) (assoc :selected-cases 1)
                                 (pos? (get row :applied 0)) (assoc :applied-cases 1))]
                       (update rows rule #(merge-with + % row))))
                   valid canonical))))
      (= status :interesting) (assoc :stopped? true))))

(defn- check-rule [counts requirements]
  (let [check (reduce-kv
               (fn [check minimum n]
                 (let [metric (get thresholds minimum)
                       actual (get counts metric 0)]
                   (cond-> (assoc check metric actual)
                     (< actual n) (assoc :passed? false :valid-counts (or counts {})))))
               (assoc requirements :passed? true)
               (dissoc requirements :min-selection-ratio))]
    (if-let [minimum (:min-selection-ratio requirements)]
      (let [eligible (get counts :eligible 0)
            selected-eligible (get counts :guard-passed 0)
            ratio (when (pos? eligible) (/ selected-eligible eligible))]
        (cond-> (assoc check :selection-ratio (when ratio (double ratio)))
          (or (nil? ratio) (< ratio (rationalize minimum)))
          (assoc :passed? false :valid-counts (or counts {}))))
      check)))

(defn finish [result opts summary]
  (if-not summary
    result
    (let [checks (into {} (for [[id requirements] (:rules opts)]
                            [id (check-rule (get-in summary [:valid-rules (id/canonical :rule id)]) requirements)]))
          final-checks (merge {:valid-cases 0} (:final-checks summary))
          named (into {} (for [[name minimum] (:final-checks opts)]
                           (let [actual (get-in summary [:named-checks (id/canonical :final-check name)] 0)]
                             [name {:valid-cases actual :min-final-checks minimum :passed? (>= actual minimum)}])))
          minimum (:min-final-checks opts)
          final-met? (or (nil? minimum) (>= (:valid-cases final-checks) minimum))
          passed? (and (every? :passed? (vals named)) final-met? (every? :passed? (vals checks)))
          zeroes (cond-> {:selected 0 :guard-passed 0 :guard-rejected 0 :assumption-rejected 0
                          :applied 0 :failed 0 :invariant-failed 0}
                   (:census? opts) (assoc :eligible 0 :ineligible 0))
          report {:rules (into {} (map (fn [[id row]] [id (merge zeroes row)])) (:rules summary)) :checks checks :passed? passed?
                  :census? (true? (:census? opts))
                  :final-checks (cond-> final-checks (seq named) (assoc :by-id named)
                                  minimum
                                  (assoc :min-final-checks minimum :passed? final-met?))}]
      (cond-> (assoc result :stateful-coverage report)
        (and (:passed? result) (not passed?)) (assoc :passed? false :status :coverage-failed)))))
