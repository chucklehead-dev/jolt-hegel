(ns hegel.stateful.history
  "Engine-free validation and matching of portable command histories."
  (:require [clojure.string :as str]
            [hegel.internal.portable-data :as data]
            [hegel.internal.portable-edn :as edn]
            [hegel.internal.stateful-id :as id]
            [hegel.replay-bundle :as bundle]
            [hegel.validation :as validation]))

(defn- invalid! [reason]
  (validation/usage-error! ::invalid-history "invalid stateful history" {:reason reason}))

(defn- id? [x]
  (and (string? x) (not (str/blank? x)) (not (str/includes? x "\u0000"))))

(defn- unique-vector? [xs]
  (and (vector? xs) (= (count xs) (count (set xs)))))

(defn validate-history!
  "Validate v1 declarations, event membership and journal bounds. Return the input."
  [{:keys [format schema-version rules invariants events stateful-step-count max-bytes max-entries] :as history}]
  (data/validate! history bundle/limits (fn [_ reason] (invalid! reason)))
  (when-not (and (= format :hegel/stateful-history) (= schema-version 1))
    (invalid! :unsupported-format))
  (when-not (= (set (keys history)) #{:format :schema-version :rules :invariants :stateful-step-count
                                      :max-entries :max-bytes :events})
    (invalid! :invalid-fields))
  (doseq [[k v lo hi] [[:stateful-step-count stateful-step-count 1 9223372036854775807]
                       [:max-entries max-entries 1 256] [:max-bytes max-bytes 2 262144]]]
    (validation/require-integer-range! ::invalid-history k v lo hi))
  (when-not (and (vector? rules) (seq rules)
                 (every? (fn [{:keys [id weight] :as rule}]
                           (and (map? rule) (= #{:id :weight} (set (keys rule))) (id? id)
                                (number? weight) (< 0 (double weight) ##Inf))) rules)
                 (unique-vector? (mapv :id rules)) (unique-vector? invariants) (every? id? invariants))
    (invalid! :invalid-declarations))
  (let [ids (set (map :id rules))]
    (when-not (and (vector? events) (<= (count events) max-entries)
                   (every? #(and (map? %) (contains? ids (:rule %))
                                 (every? #{:rule :input :outcome :model-before :model-after} (keys %))) events))
      (invalid! :invalid-events)))
  (let [text (edn/encode events bundle/limits invalid! identity)
        bytes (.getBytes ^String text "UTF-8")]
    (when-not (= text (String. bytes "UTF-8")) (invalid! :invalid-unicode))
    (when (> (alength bytes) max-bytes) (invalid! :max-bytes)))
  history)

(defn upgrade-v0-history
  "Validate and return the v1 representation of a v0.7.0 unversioned value."
  [history]
  (when-not (and (map? history) (not (contains? history :format)) (not (contains? history :schema-version)))
    (invalid! :expected-unversioned-history))
  (validate-history! (assoc history :format :hegel/stateful-history :schema-version 1)))

(defn match-scenario
  "Return matched event indexes and the next unmatched pattern. With key-fn,
  all matched events must share a key. Return the longest prefix on a miss."
  ([history scenario] (match-scenario history scenario (constantly true)))
  ([history scenario key-fn]
   (when-not (and (vector? scenario) (seq scenario)
                  (every? #(and (map? %) (seq %) (every? #{:rule :outcome} (keys %))) scenario))
     (invalid! :invalid-scenario))
   (let [patterns (mapv #(cond-> % (contains? % :rule) (update :rule (partial id/canonical :rule))) scenario)]
     (loop [i 0 groups {} best []]
       (if (= (count best) (count patterns))
         {:matched? true :indexes best}
         (if (= i (count (:events history)))
           {:matched? false :indexes best :next-pattern (nth scenario (count best))}
           (let [event (nth (:events history) i)
                 k (key-fn event)
                 indexes (get groups k [])
                 pattern (nth patterns (count indexes))]
             (if (and (some? k) (not (and (sequential? k) (some nil? k)))
                      (= pattern (select-keys event (keys pattern))))
               (let [indexes (conj indexes i)]
                 (recur (inc i) (assoc groups k indexes) (if (> (count indexes) (count best)) indexes best)))
               (recur (inc i) groups best)))))))))
