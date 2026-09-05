(ns hegel.internal.portable-edn
  "Restricted, bounded EDN mechanics parameterized by caller policy.

  Limits are trusted configuration. The invalid! callback must throw; validate!
  must reject unsupported data and return accepted values unchanged."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [hegel.host :as host]
            [hegel.internal.portable-data :as portable-data]))

(defn- preflight! [text limits invalid!]
  (when-not (string? text)
    (invalid! :string-required))
  (when (> (count text) (:max-text-chars limits))
    (invalid! :max-text-chars))
  (when (> (portable-data/text-size text) (:max-text-chars limits))
    (invalid! :max-text-chars))
  ;; Check nesting and token counts before the recursive EDN reader. Dispatch
  ;; syntax is deliberately absent: no tags, sets, discards, or reader eval.
  (loop [index 0 stack [] quoted? false escaped? false comment? false
         token? false nodes 0]
    (when (> nodes (:max-nodes limits))
      (invalid! :max-nodes))
    (if (= index (count text))
      (when (or quoted? (seq stack))
        (invalid! :unterminated-form))
      (let [c (nth text index)
            index (inc index)]
        (cond
          comment?
          (recur index stack false false (not (or (= c \newline)
                                                 (= c \return))) false nodes)

          quoted?
          (cond
            escaped? (recur index stack true false false false nodes)
            (= c \\) (recur index stack true true false false nodes)
            (= c \") (recur index stack false false false false nodes)
            :else (recur index stack true false false false nodes))

          (= c \;) (recur index stack false false true false nodes)
          (= c \") (recur index stack true false false false (inc nodes))

          (or (= c \{) (= c \[))
          (let [stack (conj stack c)]
            ;; The data validator numbers the root at depth zero. An empty
            ;; container at depth 32 therefore has 33 open containers.
            (when (> (count stack) (inc (:max-depth limits)))
              (invalid! :max-depth))
            (recur index stack false false false false (inc nodes)))

          (or (= c \}) (= c \]))
          (do
            (when-not (= (peek stack) (if (= c \}) \{ \[))
              (invalid! :mismatched-delimiter))
            (recur index (pop stack) false false false false nodes))

          (contains? #{\# \\ \( \) \^ \@ \` \~ \'} c)
          (invalid! :unsupported-reader-syntax)

          (contains? #{\space \tab \newline \return \, \formfeed} c)
          (recur index stack false false false false nodes)

          :else
          (recur index stack false false false true
                 (if token? nodes (inc nodes))))))))

(defn decode
  "Read exactly one restricted EDN value and pass it to caller validation."
  [text limits invalid! validate!]
  (preflight! text limits invalid!)
  (let [forms (host/try-catch-all
               ;; A wrapper avoids host-specific PushbackReader constructors.
               ;; Its closing bracket follows a newline so a trailing comment
               ;; cannot consume it. Preflight has checked the original stack.
               (edn/read-string (str "[" text "\n]"))
               _
               (invalid! :invalid-edn))]
    (when-not (and (vector? forms) (= 1 (count forms)))
      (invalid! :one-form-required))
    (validate! (first forms))))

(defn- scalar-text [value]
  (binding [*print-meta* false *print-readably* true
            *print-length* nil *print-level* nil]
    ;; Preserve a Float's exact numeric value when EDN reads it as a Double.
    (pr-str (if (float? value) (double value) value))))

(defn- encode-data [value limits invalid!]
  ;; Each fragment is bounded before joining; do not print an entire candidate
  ;; and only afterwards discover that escaping exceeded the text budget.
  (loop [pending (list [:value value]) fragments [] length 0]
    (if-let [[kind value] (first pending)]
      (let [pending (next pending)]
        (cond
          (= kind :text)
          (let [length (+ length (portable-data/text-size value))]
            (when (> length (:max-text-chars limits))
              (invalid! :max-text-chars))
            (recur pending (conj fragments value) length))

          (map? value)
          (recur (concat [[:text "{"]]
                         (mapcat (fn [[k v]]
                                   [[:value k] [:text " "]
                                    [:value v] [:text " "]]) value)
                         [[:text "}"]] pending)
                 fragments length)

          (vector? value)
          (recur (concat [[:text "["]]
                         (mapcat (fn [v] [[:value v] [:text " "]]) value)
                         [[:text "]"]] pending)
                 fragments length)

          :else
          (recur (cons [:text (scalar-text value)] pending) fragments length)))
      (str/join fragments))))

(defn encode
  "Encode caller-validated data as bounded readable EDN and revalidate it."
  [value limits invalid! validate!]
  (validate! value)
  (let [text (encode-data value limits invalid!)]
    (when-not (= value (decode text limits invalid! validate!))
      (invalid! :not-roundtrippable))
    text))
