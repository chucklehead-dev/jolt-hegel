(ns probe
  (:require [clojure.edn :as edn]
            [hegel.core :as h]
            [hegel.generator :as g]
            [hegel.replay-bundle :as bundle]
            [hegel.replay-bundle.codec :as codec]
            [instaparse.core :as insta]))

(def grammar "S = chunk*; chunk = 'a' | 'b'")
(def parsers (into {} (for [format [:hiccup :enlive]]
                       [format (insta/parser grammar :output-format format)])))
(def manifest
  {:hegel-sha "8179cb83eda81c708dc834ee38647f2354fe3d55"
   :libhegel-version "0.51.6"
   :runtime {:host :jolt :version "0.8.27" :os "linux" :arch "x86_64"}
   :property-id "instaparse/fixed-repetition/drop-last-control"
   :generator-revision "format-ab-vector-max32-v1"
   :model-revision "tree-equality-v1"})
(def case-gen
  (g/tuple (g/sampled-from [:hiccup :enlive])
           (g/vector {:max-size 32} (g/sampled-from ["a" "b"]))))
(defn results [[format tokens] broken?]
  (let [input (apply str tokens)
        parser (parsers format)
        normal (parser input)
        optimized (parser input :optimize :memory)
        ;; Realistic assembly defect: lose the final repeated chunk, only when present.
        actual (if (and broken? (seq tokens))
                 (if (= format :hiccup)
                   (pop optimized)
                   (update optimized :content #(seq (butlast %))))
                 optimized)]
    {:format format :input input :tokens tokens :normal normal :actual actual
     :optimization-used? (= :memory (:optimize (meta optimized)))}))
(defn check! [data]
  (when (or (insta/failure? (:normal data)) (insta/failure? (:actual data))
            (not= (:normal data) (:actual data)))
    (throw (ex-info "normal and memory trees differ"
                    (assoc data :hegel/origin "instaparse/tree-equality")))))
(defn property [broken?]
  (fn [_]
    (let [data (results (h/draw! case-gen "format-and-chunks") broken?)
          category (str (name (:format data)) "/" (if (empty? (:input data)) "empty" "nonempty"))]
      (h/event! category)
      (h/event! (if (:optimization-used? data) "optimized" "fallback"))
      (h/observe! (count (:tokens data)) "chunks")
      (check! data))))
(def settings {:test-cases 200 :seed 424242 :database "" :verbosity :quiet
               :observations? true})
(def coverage {:scope :exploration
               :requirements (zipmap ["hiccup/empty" "hiccup/nonempty" "enlive/empty" "enlive/nonempty" "optimized" "fallback"]
                                     (repeat {:min-count 1}))})
(defn summary [r] (select-keys r [:passed? :status :seed :test-cases :valid-test-cases :n-failures :flaky? :coverage :observations :counterexample]))
(defn -main [& [mode]]
  (case mode
    "good" (let [r (h/run-test! (assoc settings :coverage coverage) (property false))]
             (spit "good-result.edn" (pr-str (summary r)))
             (prn (summary r))
             (assert (:passed? r)))
    "control" (let [r (h/run-test! settings (property true))]
                (spit "control-result.edn" (pr-str (summary r)))
                (prn (summary r))
                (assert (and (false? (:passed? r)) (not (:flaky? r)) (pos? (:n-failures r))))
                (spit "counterexample.edn"
                      (pr-str (dissoc (ex-data (-> r :final first :exception)) :hegel/origin)))
                (spit "retained-failure.edn"
                      (pr-str (mapv #(assoc (dissoc % :exception) :exception-data (ex-data (:exception %))) (:final r))))
                (spit "failure-bundle.edn" (codec/encode (bundle/from-result manifest r))))
    "replay" (let [r (h/replay-bundle! manifest (codec/decode (slurp "failure-bundle.edn")) (property true))]
               (spit "replay-result.edn" (pr-str (select-keys r [:status :reproduced? :flaky? :mismatches])))
               (prn (select-keys r [:status :reproduced? :flaky? :mismatches]))
               (assert (:reproduced? r)))
    "fixture" (let [fixture (edn/read-string (slurp "counterexample.edn"))
                    case [(:format fixture) (:tokens fixture)]
                    good (results case false)
                    bad (results case true)]
                (check! good)
                (assert (not= (:normal bad) (:actual bad)))
                (prn {:good-passed? true :control-differs? true :case case :normal (:normal bad) :broken (:actual bad)}))
    (throw (ex-info "use good, control, replay or fixture" {}))))
