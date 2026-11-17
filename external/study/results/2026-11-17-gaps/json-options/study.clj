(ns study
  (:require [yyjson.core :as json] [clojure.string :as str]
            [hegel.core :as h] [hegel.generator :as g]
            [hegel.replay-bundle :as bundle] [hegel.replay-bundle.codec :as codec]))
(defn check [id input expected actual]
  (when-not (= expected actual)
    (throw (ex-info id {:hegel/origin id :input input :expected expected :actual actual}))))
(def strings (g/string {:max-size 14 :alphabet "abAB/\\\"\n\r\t\u0000é😀"}))
(def leaf (g/one-of [strings (g/boolean) (g/just nil)]))
(def tree (g/recursive {:max-depth 5 :max-leaves 25} leaf
                      (fn [sub] (g/one-of [(g/vector {:max-size 4} sub)
                                          (g/map {:max-size 4} strings sub)]))))
;; Independently emit JSON with every character represented as \uXXXX;
;; astral code points are split into UTF-16 pairs, unlike Jolts
(defn hex4 [n] (format "\\u%04x" n))
(defn quoted [s]
  (str "\"" (apply str (map (fn [c] (let [n (int c)]
    (if (< n 65536) (hex4 n)
      (let [u (- n 65536)] (str (hex4 (+ 55296 (quot u 1024)))
                               (hex4 (+ 56320 (mod u 1024)))))))) s)) "\""))
(declare encode)
(defn encode [x]
  (cond (nil? x) "null" (true? x) "true" (false? x) "false"
        (string? x) (quoted x)
        (vector? x) (str "[" (str/join "," (map encode x)) "]")
        (map? x) (str "{" (str/join "," (map (fn [[k v]] (str (quoted k) ":" (encode v))) x)) "}")))
(defn kf [k] (str "K\u0000" (str/lower-case k)))
(defn vf [k v] (if (string? v) (str k "\u0000" v) v))
(defn write-model [x]
  (cond (map? x) (reduce (fn [m [k v]] (assoc m (kf k) (write-model (vf k v)))) {} x)
        (vector? x) (mapv write-model x) :else x))
(defn read-model [f x]
  (cond (map? x) (reduce (fn [m [k v]] (assoc m (f k) (read-model f v))) {} x)
        (vector? x) (mapv #(read-model f %) x) :else x))
(defn options-check [x pretty]
  (check "options/recursive-transform" [x pretty] (write-model x)
         (json/read-str (json/write-str x :pretty pretty :key-fn kf :value-fn vf))))
(defn escaping-check [x]
  (check "escaping/independent-reader" x x (json/read-str (encode x)))
  (check "escaping/roundtrip" x x (json/read-str (json/write-str x))))
(defn dup-check [pairs mode]
  (let [f (case mode 0 identity 1 str/lower-case 2 (constantly nil))
        source (str "{" (str/join "," (map (fn [[k v]] (str (quoted k) ":" (encode v))) pairs)) "}")
        expected (reduce (fn [m [k v]] (assoc m (f k) (read-model f v))) {} pairs)]
    (check "duplicates/last-wins" [pairs mode] expected (json/read-str source :key-fn f))))
(defn error-data [f] (try (f) nil (catch clojure.lang.ExceptionInfo e (ex-data e))))
(defn nul-check [s]
  (let [prefix (str "[" (json/write-str s) ",") source (str prefix "\u0000]")
        e (error-data #(json/read-str source))]
    (check "errors/raw-nul-kind" s :invalid (:yyjson/error e))
    (check "errors/raw-nul-byte-position" s (alength (.getBytes prefix "UTF-8")) (:position e))))
(def props
  {"options" (fn [_] (let [x (h/draw! tree)] (h/event! (if (map? x) "map-root" "other-root"))
                        (options-check x (h/draw! (g/boolean)))))
   "escaping" (fn [_] (escaping-check (h/draw! tree)))
   "duplicates" (fn [_] (let [pairs (h/draw! (g/vector {:max-size 8} (g/tuple (g/sampled-from ["a" "A" "" "\u0000" "😀"]) tree)))
                             mode (h/draw! (g/integer 0 2))]
                         (h/event! (str "key-mode-" mode)) (dup-check pairs mode)))
   "nul-errors" (fn [_] (nul-check (h/draw! strings)))})
(def provenance-base {:hegel-sha "6321d5ab485db656a48823030453bc4b2d0af1cc"
 :libhegel-version "0.51.6" :runtime {:host :jolt :version "0.8.27" :os "Linux" :arch "amd64"}
 :generator-revision "json-options/v1" :model-revision "json-options-independent/v1"})
(defn run-one [id prop]
  (let [r (h/run-test! {:name id :test-cases 400 :seed 20261116 :database "" :verbosity :quiet :observations? true} prop)
        summary (select-keys r [:passed? :status :seed :test-cases :valid-test-cases :invalid-test-cases :overrun-test-cases :interesting-test-cases :n-failures :flaky? :error :observations])]
    (prn id summary)
    (spit (str "logs/" id "-result.edn") (pr-str summary))
    (when-not (:passed? r)
      (spit (str id "-input.edn") (pr-str (-> r :final first :exception ex-data)))
      (when (and (not (:flaky? r)) (seq (:final r)))
        (spit (str id "-bundle.edn") (codec/encode (bundle/from-result (assoc provenance-base :property-id id) r)))))
    (:passed? r)))
(defn witnesses []
  (doseq [x [nil true false "" "\u0000😀\"\\" [] {} {"a" [{"A" "\u0000" "a" "😀"}]}]]
    (escaping-check x) (options-check x true) (options-check x false))
  (doseq [mode (range 3)] (dup-check [["a" true] ["A" false] ["a" nil] ["\u0000" "😀"] ["\u0000" false]] mode))
  (doseq [s ["" "é" "😀" "\u0000" "😀é\n"]] (nul-check s))
  (doseq [s ["[\"\\ud800\"]" "[\"\\udc00\"]" "{} true" "[true,]" "{\"a\":false,}"]]
    (check "errors/invalid" s :invalid (:yyjson/error (error-data #(json/read-str s)))))
  (check "options/key-type" nil :unsupported (:yyjson/error (error-data #(json/write-str {"a" true} :key-fn keyword))))
  (println "Explicit witnesses passed"))
(defn control-property [_]
  (let [k (h/draw! strings)]
    (check "control/first-wins" k {k true}
      (json/read-str (str "{" (quoted k) ":true," (quoted k) ":false}")))))
(defn control []
  ;; Deliberately broken local first-wins oracle; no target mutation.
  (let [r (h/run-test! {:test-cases 10 :seed 9 :database "" :verbosity :quiet}
            control-property)]
    (spit "control-input.edn" (pr-str (-> r :final first :exception ex-data)))
    (spit "control-bundle.edn" (codec/encode (bundle/from-result
      (assoc provenance-base :property-id "control/first-wins") r)))
    (prn "semantic-control" (select-keys r [:passed? :status :n-failures :flaky?]))
    (when-not (and (false? (:passed? r)) (not (:flaky? r))
                  (= "control/first-wins" (-> r :final first :exception ex-data :hegel/origin)))
      (throw (ex-info "Control did not detect semantic discrepancy" {})))))
(defn -main [& _]
  (witnesses) (control)
  (let [outcomes (doall (map (fn [[id prop]] (run-one id prop)) props))]
    (when-not (every? true? outcomes) (System/exit 1))))
