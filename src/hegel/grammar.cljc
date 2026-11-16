(ns hegel.grammar
  "Pure compilation of Clojure grammar data to Hegel's Lark subset."
  (:refer-clojure :exclude [compile])
  (:require [clojure.string :as str]
            [hegel.validation :as validation]))

(defn- invalid! [path message value]
  (validation/usage-error! ::invalid-grammar
                           (str message " at " (pr-str path))
                           {:path path :value value}))

(defn- keys! [value allowed path]
  (when-not (map? value) (invalid! path "Expected a map" value))
  (doseq [key (keys value)]
    (when-not (contains? allowed key)
      (invalid! (conj path key) "Unknown grammar option" (get value key)))))

(defn- rule-name [value path]
  (when-not (and (keyword? value) (nil? (namespace value))
                 (re-matches #"[a-z][a-z0-9_-]*" (name value)))
    (invalid! path "Rule names must be unqualified lowercase keywords" value))
  (str/replace (name value) "-" "_"))

(defn- scalars [value path]
  (when-not (string? value) (invalid! path "Expected a string" value))
  ;; JVM/CLR sequence UTF-16 units; Jolt sequences Unicode scalar values.
  (loop [units (seq (map int value)) result []]
    (if-let [unit (first units)]
      (cond
        (<= 0xD800 unit 0xDBFF)
        (let [low (second units)]
          (when-not (and low (<= 0xDC00 low 0xDFFF))
            (invalid! path "Unpaired Unicode surrogate" value))
          (recur (nnext units)
                 (conj result (+ 0x10000 (* (- unit 0xD800) 1024) (- low 0xDC00)))))
        (<= 0xDC00 unit 0xDFFF) (invalid! path "Unpaired Unicode surrogate" value)
        :else (recur (next units) (conj result unit)))
      result)))

(defn- literal [value path]
  (scalars value path)
  (str "\""
       (apply str (map (fn [c]
                         (case (int c)
                           34 "\\\""
                           92 "\\\\"
                           (if (< (int c) 32)
                             (format "\\u%04x" (int c))
                             (str c)))) value))
       "\""))

(declare expression)

(defn- expression [value rules path]
  (cond
    (string? value) (if (empty? value) "()" (literal value path))
    (keyword? value)
    (do (when-not (contains? rules value)
          (invalid! path "Undefined rule reference" value))
        (rule-name value path))
    (vector? value)
    (let [[op & args] value
          child #(expression (nth value %) rules (conj path %))
          arity! (fn [n]
                   (when-not (= n (count args))
                     (invalid! path (str (pr-str op) " expects " n " arguments") value)))]
      (case op
        (:cat :alt)
        (do (when (empty? args) (invalid! path "Expected at least one expression" value))
            (str "(" (str/join (if (= op :cat) " " " | ")
                               (map child (range 1 (count value)))) ")"))
        :optional (do (arity! 1) (str "(" (child 1) ")?"))
        :repeat
        (do (arity! 2)
            (let [opts (first args) opts-path (conj path 1)]
              (keys! opts #{:min :max} opts-path)
              (doseq [key [:min :max]]
                (when-not (and (integer? (get opts key)) (<= 0 (get opts key) 10000))
                  (invalid! (conj opts-path key) "Repeat bounds must be integers from 0 to 10000" (get opts key))))
              (when (> (:min opts) (:max opts))
                (invalid! opts-path "Repeat minimum exceeds maximum" opts))
              (str "(" (child 2) ")~" (:min opts) ".." (:max opts))))
        :char-range
        (do (arity! 2)
            (let [bounds (mapv #(scalars (nth value %) (conj path %)) [1 2])]
              (doseq [[index bound] (map vector [1 2] bounds)]
                (when-not (= 1 (count bound))
                  (invalid! (conj path index) "Range endpoint must be one Unicode character" (nth value index))))
              (when (> (ffirst bounds) (first (second bounds)))
                (invalid! path "Character range is reversed" value))
              (str (literal (first args) (conj path 1)) ".."
                   (literal (second args) (conj path 2)))))
        (invalid! (conj path 0) "Unknown grammar operator" op)))
    :else (invalid! path "Expected a literal string, rule keyword or operator vector" value)))

(defn- productive? [value known]
  (cond
    (string? value) true
    (keyword? value) (contains? known value)
    :else
    (case (first value)
      :alt (boolean (some #(productive? % known) (rest value)))
      :cat (every? #(productive? % known) (rest value))
      :optional true
      :repeat (or (zero? (:min (second value))) (productive? (nth value 2) known))
      :char-range true)))

(defn compile
  "Return {:source Lark-text :start native-rule-name}, without loading Hegel.

  Grammar: {:start :start, :rules {keyword expression}}; :start is optional.
  Strings are literals and keywords reference rules. Operators: :cat, :alt,
  :optional, :repeat {:min n :max n}, :char-range. Rule names accept lowercase
  letters, digits, underscores and hyphens; hyphens compile to underscores."
  [grammar]
  (keys! grammar #{:start :rules} [])
  (let [rules (:rules grammar)
        start (get grammar :start :start)]
    (when-not (and (map? rules) (seq rules))
      (invalid! [:rules] "Expected a nonempty rule map" rules))
    (let [names (mapv #(rule-name % [:rules %]) (keys rules))]
      (when-not (= (count names) (count (set names)))
        (invalid! [:rules] "Rule names collide after replacing hyphens with underscores" (keys rules))))
    (rule-name start [:start])
    (when-not (contains? rules start) (invalid! [:start] "Undefined start rule" start))
    (let [lines (mapv (fn [[rule value]]
                        (str (rule-name rule [:rules rule]) ": "
                             (expression value rules [:rules rule])))
                      (sort-by (comp name key) rules))]
      (loop [known #{}]
        (let [next-known (into known (for [[rule value] rules :when (productive? value known)] rule))]
          (if (= known next-known)
            (doseq [rule (keys rules) :when (not (contains? known rule))]
              (invalid! [:rules rule] "Rule cannot produce a finite string" (get rules rule)))
            (recur next-known))))
      {:source (str (str/join "\n" lines) "\n")
       :start (rule-name start [:start])})))
