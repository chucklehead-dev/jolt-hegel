(ns pilot.yyjson
  (:require [hegel.generator :as g]
            [yyjson.core :as json]
            [pilot.runner :as runner]))

(def text (g/fmap #(apply str %) (g/vector {:max-size 12} (g/sampled-from ["a" "é" "😀" "\u0000" "\"" "\\" "\n"]))))
(def values (g/recursive {:max-depth 4 :max-leaves 20}
                         (g/one-of [(g/just nil) (g/boolean) (g/integer -1000 1000) text])
                         (fn [child] (g/one-of [(g/vector {:max-size 4} child)
                                              (g/map {:max-size 4} text child)]))))
(defn hex4 [n]
  (apply str (map #(nth ["0" "1" "2" "3" "4" "5" "6" "7" "8" "9" "a" "b" "c" "d" "e" "f"]
                         (bit-and 15 (bit-shift-right n %))) [12 8 4 0])))
(defn escape-scalar [n]
  (if (< n 65536) (str "\\u" (hex4 n))
      (let [offset (- n 65536)]
        (str "\\u" (hex4 (+ 55296 (quot offset 1024)))
             "\\u" (hex4 (+ 56320 (mod offset 1024)))))))
;; Independent encoders; neither calls yyjson's writer or data.json.
;; This pack runs on Jolt, whose string sequences contain Unicode code points.
(defn json-string [s escaped? mutant?]
  (str "\"" (apply str (map (fn [c]
                               (let [n (int c)]
                                 (cond
                                   (and mutant? (> n 65535)) "\\ufffd"
                                   (or escaped? (< n 32) (= n 34) (= n 92)) (escape-scalar n)
                                   :else (str c)))) s)) "\""))
(defn utf8-size [s]
  (reduce + 0 (map #(let [n (int %)] (cond (< n 128) 1 (< n 2048) 2 (< n 65536) 3 :else 4)) s)))
(def prefix (g/fmap #(apply str %) (g/vector {:min-size 1 :max-size 12} (g/sampled-from ["a" "é" "😀"]))))
(def properties
  [{:id :roundtrip :generator values
    :labels (fn [v]
              (let [children (if (map? v) (vals v) (when (vector? v) v))
                    nodes (tree-seq coll? #(if (map? %) (concat (keys %) (vals %)) (seq %)) v)]
                (cond-> [(cond (nil? v) "null" (map? v) "object" (vector? v) "array" :else "scalar")]
                  (some coll? children) (conj "nested")
                  (some #(and (string? %) (some (fn [c] (> (int c) 127)) %)) nodes) (conj "unicode"))))
    :coverage ["null" "object" "array" "scalar" "nested" "unicode"]
    :check (fn [v mutant?]
             (let [decoded (json/read-str (json/write-str v))
                   actual (if (and mutant? (nil? decoded)) false decoded)]
               (runner/require! :roundtrip (= v actual) {:expected v :actual actual})))}
   {:id :unicode :generator text
    :labels (fn [s] (cond-> [] (some #(> (int %) 65535) s) (conj "supplementary")
                              (some #(zero? (int %)) s) (conj "nul")
                              (some #(< (int %) 32) s) (conj "control")))
    :coverage ["supplementary" "nul" "control"]
    :check (fn [s mutant?]
             (let [raw (json/read-str (json-string s false false))
                   escaped (json/read-str (json-string s true mutant?))]
               (runner/require! :unicode (= s raw escaped) {:expected s :raw raw :escaped escaped})))}
   {:id :nul-offset :generator prefix
    :labels (fn [s] [(if (some #(> (int %) 127) s) "multibyte" "ascii")]) :coverage ["multibyte" "ascii"]
    :check (fn [s mutant?]
             (let [source (str "\"" s "\u0000\"")
                   error (try (json/read-str source) nil (catch Exception e (ex-data e)))
                   expected (inc (if mutant? (count s) (utf8-size s)))]
               (runner/require! :nul-offset (and (= :invalid (:yyjson/error error)) (= expected (:position error)))
                                {:source source :expected-byte-offset expected :error error})
               (runner/require! :nul-offset (= "ok" (json/read-str "\"ok\"")) {:kind :post-error-usability})))}])
(defn -main [& args] (runner/main! :yyjson properties args))
