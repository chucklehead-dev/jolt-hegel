(ns hegel.internal.portable-data
  "Bounded validation of portable EDN-shaped data.

  Callers supply their limits and an `(fn [path reason] ...)` failure policy so
  this internal mechanic does not impose a public error namespace. The failure
  callback must throw; limits are trusted configuration supplied by the caller."
  (:require [clojure.string :as str]))

(defn text-size
  "Count UTF-16 code units consistently, including on codepoint-indexed Jolt."
  [text]
  (reduce (fn [size c] (+ size (if (> (int c) 65535) 2 1))) 0 text))

(def ^:private min-int64 -9223372036854775808N)
(def ^:private max-uint64 18446744073709551615N)

(defn- record-value? [value]
  ;; The experimental jank runtime has neither records nor record?.
  #?(:jank (do value false)
     :default (record? value)))

(defn- finite-floating? [value]
  (and (or (double? value) (float? value))
       (= value value)
       (not= value ##Inf)
       (not= value ##-Inf)))

(defn- portable-scalar? [value]
  (or (nil? value)
      (true? value)
      (false? value)
      (string? value)
      (keyword? value)
      (and (integer? value) (<= min-int64 value max-uint64))
      (finite-floating? value)))

(defn- consume-node! [path depth nodes limits invalid!]
  (when (> depth (:max-depth limits))
    (invalid! path :max-depth))
  (let [nodes (inc nodes)]
    (when (> nodes (:max-nodes limits))
      (invalid! path :max-nodes))
    nodes))

(defn- consume-text! [path text-chars value limits invalid!]
  ;; Cheap rejection precedes the portable scan for already oversized input.
  (when (> (count value) (:max-string-chars limits))
    (invalid! path :max-string-chars))
  (let [length (text-size value)
        text-chars (+ text-chars length)]
    (when (str/includes? value "\u0000")
      (invalid! path :nul-string))
    (when (> length (:max-string-chars limits))
      (invalid! path :max-string-chars))
    (when (> text-chars (:max-text-chars limits))
      (invalid! path :max-text-chars))
    text-chars))

(defn validate!
  "Iteratively validate portable maps, vectors, and scalar data.

  Map keys count toward node and text limits. Lists, sets, lazy seqs, records,
  and opaque host values are rejected instead of being realized or coerced."
  [value limits invalid!]
  (loop [frames (list [:value [] value 0])
         nodes 0
         text-chars 0]
    (if-let [[kind path item depth index] (first frames)]
      (let [frames (next frames)]
        (case kind
          :value
          (let [nodes (consume-node! path depth nodes limits invalid!)]
            (cond
              (record-value? item) (invalid! path :opaque-value)
              (map? item)
              (recur (conj frames [:map path (seq item) (inc depth) 0])
                     nodes text-chars)

              (vector? item)
              (recur (conj frames [:vector path item (inc depth) 0])
                     nodes text-chars)

              (string? item)
              (recur frames nodes
                     (consume-text! path text-chars item limits invalid!))

              (keyword? item)
              ;; Keywords are serialized as text too. Count their complete
              ;; spelling so a large keyword cannot bypass encoder limits.
              (do
                (when (> (+ 1 (count (name item))
                            (if-let [ns (namespace item)] (inc (count ns)) 0))
                         (:max-string-chars limits))
                  (invalid! path :max-string-chars))
                (recur frames nodes
                       (consume-text! path text-chars (str item) limits invalid!)))

              (portable-scalar? item)
              (recur frames nodes text-chars)

              :else
              (invalid! path :opaque-value)))

          :map
          (if-let [entry (first item)]
            (let [[key value] entry
                  entry-path (conj path :entry index)]
              (recur (list* [:value (conj entry-path :key) key depth]
                            [:value (conj entry-path :value) value depth]
                            [:map path (next item) depth (inc index)]
                            frames)
                     nodes text-chars))
            (recur frames nodes text-chars))

          :vector
          (if (< index (count item))
            (recur (list* [:value (conj path index) (nth item index) depth]
                          [:vector path item depth (inc index)]
                          frames)
                   nodes text-chars)
            (recur frames nodes text-chars))))
      value)))
