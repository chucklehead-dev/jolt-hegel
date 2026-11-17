(ns study
 (:require [instaparse.core :as i] [hegel.core :as h] [hegel.generator :as g]
           [hegel.replay-bundle :as bundle] [hegel.replay-bundle.codec :as codec]
           [clojure.edn :as edn]))
(def provenance {:hegel-sha "6321d5ab485db656a48823030453bc4b2d0af1cc" :libhegel-version "0.51.6"
 :runtime {:host :jolt :version "0.8.27" :os "Linux" :arch "amd64"}
 :property-id "instaparse/hidden-root-line-columns" :generator-revision "v1" :model-revision "prefix-position-v1"})
(def grammar "S = \u0027a\u0027* T*; T = \u0027b\u0027 | \u0027\n\u0027")
(def hidden-grammar "<S> = \u0027a\u0027* T*; T = \u0027b\u0027 | \u0027\n\u0027")
(def parsers (into {} (for [f [:hiccup :enlive] hide? [false true]]
 [[f hide?] (i/parser (if hide? hidden-grammar grammar) :output-format f)])))
(defn check! [ok origin data] (when-not ok (throw (ex-info origin (assoc data :hegel/origin origin)))))
(defn nodes [tree] (filter #(or (map? %) (and (vector? %) (keyword? (first %))))
 (tree-seq #(or (map? %) (sequential? %)) #(if (map? %) (:content %) (if (keyword? (first %)) (rest %) %)) tree)))
(defn pos [text index line col]
 (reduce (fn [[l c] ch] (if (= ch \newline) [(inc l) 1] [l (inc c)])) [line col] (take index text)))
(defn verify-annotations! [text tree line col]
 (doseq [node (nodes tree) :let [[s e] (i/span node) m (meta node)]]
  (check! (= (pos text s line col) [(:instaparse.gll/start-line m) (:instaparse.gll/start-column m)]) "annotation/start" {:text text :node node})
  (check! (= (pos text e line col) [(:instaparse.gll/end-line m) (:instaparse.gll/end-column m)]) "annotation/end" {:text text :node node})))
(defn annotate! [{:keys [text format line col] :as input} hidden?]
 (let [tree ((parsers [format hidden?]) text)]
  (check! (not (i/failure? tree)) "harness/valid-text" {:input input})
  (let [annotated (try (doall (i/add-line-and-column-info-to-metadata text line col tree))
                  (catch IllegalArgumentException e
                   (throw (ex-info "Valid hidden-root parse rejected by line-column annotation"
                           {:hegel/origin "hidden-root/annotation-dispatch" :input input :target-message (.getMessage e)} e))))]
   (check! (= (map i/span (nodes tree)) (map i/span (nodes annotated))) "annotation/shape" {:input input})
   (verify-annotations! text annotated line col))))
(def input-gen (g/tuple (g/integer 0 8) (g/vector {:max-size 8} (g/sampled-from ["b" "\n"]))
                       (g/sampled-from [:hiccup :enlive]) (g/integer 1 5) (g/integer 1 5)))
(defn draw-input [] (let [[n tail fmt line col] (h/draw! input-gen "input")]
 {:text (str (apply str (repeat n "a")) (apply str tail)) :format fmt :line line :col col}))
(defn hidden-property [_] (annotate! (draw-input) true))
(defn visible-property [_] (annotate! (draw-input) false))
(defn optimized-property [_]
 (let [{:keys [text format] :as input} (draw-input)]
  (doseq [hidden? [false true] :let [p (parsers [format hidden?]) a (p text) b (p text :optimize :memory)]]
   (check! (= a b) "optimized/tree" {:input input})
   (check! (= (map i/span (nodes a)) (map i/span (nodes b))) "optimized/spans" {:input input}))))
(def summary-keys [:passed? :status :seed :test-cases :valid-test-cases :invalid-test-cases :overrun-test-cases :interesting-test-cases :n-failures :flaky?])
(defn run-prop [name f] (let [r (h/run-test! {:name name :seed 42017 :test-cases 250 :database "" :verbosity :quiet} f)]
 (prn name (select-keys r summary-keys)) r))
(defn -main [& [mode]]
 (case mode
 "replay" (let [r (h/replay-bundle! provenance (codec/decode (slurp "failure.bundle.edn")) hidden-property)]
            (prn :replay (select-keys r [:status :reproduced? :flaky?]))
            (assert (= :reproduced (:status r)))
            (let [calls (atom 0) mismatch (h/replay-bundle! (assoc provenance :model-revision "wrong")
                 (codec/decode (slurp "failure.bundle.edn")) (fn [_] (swap! calls inc)))]
              (assert (= :incompatible (:status mismatch))) (assert (zero? @calls))
              (prn :provenance-mismatch :rejected-before-property)))
 "run" (do
   ;; Explicit witnesses separate from generated counts, including both formats,
   ;; no children, initial raw leaf, initial tagged node, and multiline mixed children.
   (doseq [fmt [:hiccup :enlive] txt ["" "a" "b" "a\nb" "\nb\n"]]
    (annotate! {:text txt :format fmt :line 3 :col 4} false)
    (if (and (seq txt) (not= \a (first txt)))
     (annotate! {:text txt :format fmt :line 3 :col 4} true)
     (let [caught (try (annotate! {:text txt :format fmt :line 3 :col 4} true) nil
                   (catch Exception e (:hegel/origin (ex-data e))))]
      (assert (= "hidden-root/annotation-dispatch" caught)))))
   ;; Semantic broken control: independently computed positions detect a shifted column.
   (let [t ((parsers [:hiccup false]) "b") good (i/add-line-and-column-info-to-metadata "b" t)
         bad (vary-meta good assoc :instaparse.gll/start-column 2)
         caught (try (verify-annotations! "b" bad 1 1) nil (catch Exception e (:hegel/origin (ex-data e))))]
     (assert (= "annotation/start" caught)) (prn :broken-control :detected-shifted-column))
   (doseq [[name f] [["visible-annotation" visible-property] ["optimized-tree-and-spans" optimized-property]]]
    (assert (:passed? (run-prop name f))))
   (let [r (run-prop "hidden-root-annotation" hidden-property)
         input (-> r :final first :exception ex-data :input)]
    (assert (false? (:passed? r))) (assert (not (:flaky? r)))
    (assert (= "hidden-root/annotation-dispatch" (-> r :final first :exception ex-data :hegel/origin)))
    (prn :minimum input)
    (spit "minimum.edn" (pr-str input))
    (spit "failure.bundle.edn" (codec/encode (bundle/from-result provenance r)))
    (spit "summary.edn" (pr-str (select-keys r summary-keys)))))
 (throw (ex-info "Expected run or replay" {}))))
