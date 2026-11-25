(ns probe
  (:require [hegel.core :as h]
            [hegel.generator :as g]
            [hegel.replay-bundle :as bundle]
            [hegel.replay-bundle.codec :as codec]
            [yyjson.core :as json]
            [clojure.edn :as edn]
            [clojure.string :as str]))

;; Unicode scalar strings over this explicit finite alphabet, 0..12 codepoints.
;; Includes NUL, controls, combining accent, BMP letters, and supplementary emoji.
(def strings (g/string {:max-size 12 :alphabet "ab Z\"\\\n\t\u0000é中́😀"}))
(def leaf (g/one-of [(g/just nil) (g/boolean) (g/integer -1000000 1000000) strings]))
(def values
  (g/recursive {:max-depth 4 :max-leaves 32} leaf
               (fn [sub] (g/one-of [(g/vector {:max-size 4} sub)
                                    (g/map {:max-size 4} strings sub)]))))
(def final-input (atom ::unset))
(defn kind [x]
  (cond (nil? x) "nil" (boolean? x) "boolean" (integer? x) "integer"
        (string? x) "string" (vector? x) "vector" (map? x) "map"))
(defn nodes [x]
  (cons x (cond (map? x) (mapcat (fn [[k v]] (concat [k] (nodes v))) x)
                (vector? x) (mapcat nodes x) :else [])))
(defn depth [x]
  (if (coll? x) (inc (reduce max 0 (map depth (if (map? x) (vals x) x)))) 0))
(defn check! [broken? x]
  (let [encoded (json/write-str x)
        actual (if broken? (json/read-str encoded :key-fn keyword) (json/read-str encoded))]
    (when-not (= x actual)
      (throw (ex-info "JSON round-trip equality failed"
                      {:hegel/origin "json-roundtrip/equality" :input x :json encoded :actual actual})))
    actual))
(defn property [broken?]
  (fn [_]
    (let [x (h/draw! values)
          ns (nodes x) ss (filter string? ns)]
      (h/event! (str "root/" (kind x)))
      (when (>= (depth x) 2) (h/event! "nested"))
      (when (some #(re-find #"[^\x00-\x7f]" %) ss) (h/event! "unicode"))
      (when (some #(str/includes? % "😀") ss) (h/event! "supplementary"))
      (when (some #(str/includes? % "\u0000") ss) (h/event! "nul"))
      (h/observe! (depth x) "depth")
      (when (h/final?) (reset! final-input x))
      (check! broken? x))))
(def provenance
  {:hegel-sha "8179cb83eda81c708dc834ee38647f2354fe3d55"
   :libhegel-version "0.51.6"
   :runtime {:host :jolt :version "0.8.27" :os "Linux" :arch "amd64"}
   :property-id "json-roundtrip-keyword-control" :generator-revision "v1" :model-revision "equality-v1"})
(def requirements
  (into {} (map (fn [s] [s {:min-count 1}])
                ["root/nil" "root/boolean" "root/integer" "root/string" "root/vector" "root/map"
                 "nested" "unicode" "supplementary" "nul"])))
(def witnesses [nil false true -1000000 0 1000000 "" "é中😀é\u0000\n\"\\"
                [] {} {"😀\u0000" [nil true -1 {"中" ["é"]}]}])
(defn -main [& [mode]]
  (case mode
    "replay" (let [r (h/replay-bundle! provenance (codec/decode (slurp "failure-bundle.edn")) (property true))]
                 (spit "replay-result.edn" (pr-str r)) (prn (select-keys r [:status :reproduced? :flaky?]))
                 (when-not (:reproduced? r) (System/exit 1)))
    "fixture" (let [x (edn/read-string (slurp "counterexample.edn"))]
                  (check! false x)
                  (try (check! true x) (throw (ex-info "Control did not fail" {}))
                       (catch Throwable e
                         (if (= "json-roundtrip/equality" (:hegel/origin (ex-data e)))
                           (prn {:reproduced? true :failure (ex-data e)}) (throw e)))))
    (let [broken? (= mode "control")
          _ (when-not broken? (doseq [x witnesses] (check! false x)))
          r (h/run-test! (cond-> {:test-cases 500 :seed 424242 :database "" :verbosity :quiet
                                 :observations? true :name "json-roundtrip"}
                          (not broken?) (assoc :coverage {:scope :exploration :requirements requirements}))
                         (property broken?))
          stem (if broken? "control" "baseline")]
      (spit (str stem "-result.edn") (pr-str r))
      (prn (select-keys r [:passed? :status :seed :valid-test-cases :n-failures :flaky? :observations :coverage]))
      (when (and broken? (false? (:passed? r)) (not (:flaky? r)) (seq (:failures r)))
        (spit "failure-bundle.edn" (codec/encode (bundle/from-result provenance r)))
        (spit "counterexample.edn" (pr-str @final-input))
        (prn :counterexample @final-input))
      (when-not (if broken? (and (false? (:passed? r)) (seq (:failures r))) (:passed? r))
        (System/exit 1)))))
