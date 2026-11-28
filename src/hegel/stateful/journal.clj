(ns hegel.stateful.journal
  "Opt-in sanitized execution journals for sequential state machines."
  (:refer-clojure :exclude [run!])
  (:require [hegel.core :as h]
            [hegel.ffi :as hffi]
            [hegel.internal.portable-data :as data]
            [hegel.internal.portable-edn :as edn]
            [hegel.replay-bundle :as bundle]
            [hegel.stateful :as hs]
            [hegel.validation :as validation]))

(defn- invalid! [reason]
  (validation/usage-error! ::invalid-journal "invalid stateful execution journal" {:reason reason}))

(defn capturing?
  "True inside a rule whose explicit journal entries are being collected."
  [] (some? (:journal-entry! h/*test-case*)))

(defn entry!
  "During capture record one explicitly projected command event.
  Never affects the property return value. The current rule supplies :rule."
  [entry]
  (when-let [append! (:journal-entry! h/*test-case*)] (append! entry))
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

(defn- checked-run [machine final-check]
  (let [state (hs/run! machine)]
    (when final-check
      (let [counts (:final-check-counts (h/current-test-case!))]
        (when counts (swap! counts update :invocations (fnil inc 0)))
        (try (final-check state)
             (when counts (swap! counts update :completed (fnil inc 0)))
           (catch Throwable e
             (let [details (ex-data e)]
               (if (or (:hegel/usage-error? details) (:hegel/inconclusive? details)
                       (= :hegel.core/assumption-rejected (:type details))
                       (hffi/error? e) (hffi/stop-test? e) (hffi/assumption-rejected? e))
                 (throw e)
                 (do
                   (when counts (swap! counts update :failed (fnil inc 0)))
                   (throw (ex-info (or (ex-message e) "stateful final check failed")
                                 (assoc details ::hs/phase :final-check
                                        :hegel/origin (or (:hegel/origin details) "hegel.stateful/final-check"))
                                 e)))))))))
    state))

(defn- options [opts]
  (validation/reject-unknown-keys! ::invalid-journal "journal" #{:max-entries :max-bytes :redact :final-check} opts)
  (let [opts (merge {:max-entries 64 :max-bytes 16384 :redact identity} opts)]
    (validation/require-integer-range! ::invalid-journal :max-entries (:max-entries opts) 1 256)
    (validation/require-integer-range! ::invalid-journal :max-bytes (:max-bytes opts) 2 262144)
    (when-not (fn? (:redact opts)) (invalid! :invalid-redactor))
    (when (and (contains? opts :final-check) (not (fn? (:final-check opts))))
      (invalid! :invalid-final-check))
    opts))

(defn- captured-run [opts machine history?]
  (let [journal (atom {:events [] :bytes 2 :truncated? false})
        capture (fn [rule attempt]
                  (let [before @journal
                        result (binding [h/*test-case* (assoc (h/current-test-case!) :journal-entry!
                                                             #(append! journal opts rule %))]
                                 (attempt))]
                    (if-not (:applied? result)
                      (reset! journal before)
                      (when (and history? (not (:truncated? @journal))
                                 (not= (inc (count (:events before))) (count (:events @journal))))
                        (invalid! :expected-one-command-entry)))
                    result))]
    (try
      (let [state (binding [h/*test-case* (assoc (h/current-test-case!) :journal-attempt capture)]
                    (checked-run machine (:final-check opts)))]
        (if history?
          (do (when (:truncated? @journal) (invalid! :incomplete-journal))
              {:stateful-step-count hffi/*stateful-step-count*
               :rules (mapv (fn [rule] {:id (hs/rule-id (:name rule)) :weight (::hs/weight rule)})
                            (:rules machine))
               :invariants (mapv ::hs/native-name (:invariants machine))
               :max-entries (:max-entries opts) :max-bytes (:max-bytes opts)
               :events (:events @journal)})
          state))
      (catch Throwable e
        (if (and (not history?) (::hs/phase (ex-data e)))
          (throw (ex-info (ex-message e) (assoc (ex-data e) ::journal @journal) e))
          (throw e))))))

(defn run!
  "Run hs/run! and journal confirmed failures. Returns the final model."
  [opts machine]
  (let [opts (options opts)]
    (if (h/final?)
      (captured-run opts machine false)
      (checked-run machine (:final-check opts)))))

(defn history!
  "Run a sequential machine and return its bounded, redacted command history.
  Use as a value inside materialize! to export only after the property passes.
  Every applied rule must emit exactly one entry. Rejected attempts are rolled back."
  [opts machine]
  (captured-run (options opts) machine true))

(defn covers?
  "Does this history contain the ordered rule/outcome patterns in scenario?
  Intervening events are allowed. Each pattern must name :rule or :outcome."
  [history scenario]
  (when-not (and (vector? scenario) (seq scenario)
                 (every? #(and (map? %) (seq %) (every? #{:rule :outcome} (keys %))) scenario))
    (invalid! :invalid-scenario))
  (loop [events (:events history) patterns (seq scenario)]
    (cond
      (nil? patterns) true
      (empty? events) false
      :else (let [pattern (first patterns)]
              (recur (next events)
                     (if (= pattern (select-keys (first events) (keys pattern)))
                       (next patterns) patterns))))))

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
