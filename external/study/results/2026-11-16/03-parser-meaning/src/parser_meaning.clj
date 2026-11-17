(ns parser-meaning
  (:require [clojure.edn :as edn]
            [clojure.walk :as walk]
            [instaparse.core :as insta]
            [hegel.core :as h]
            [hegel.generator :as g]
            [hegel.replay-bundle :as bundle]
            [hegel.replay-bundle.codec :as codec]))

;; At most 16 leaves, each 0..999: every subtree sum is 0..15984.
;; Exact in signed 64-bit integers (and even IEEE double integer precision).
(def ast-gen
  (g/recursive {:max-depth 4 :max-leaves 16}
               (g/integer 0 999)
               (fn [sub] (g/fmap (fn [[a b]] [:add a b]) (g/tuple sub sub)))))
(def parser
  (insta/parser "expr = number | add
                 add = <'('> expr <'+'> expr <')'>
                 number = #'[0-9]+'"))
(defn leaves [ast]
  (if (integer? ast) [ast] (into (leaves (nth ast 1)) (leaves (nth ast 2)))))
(defn depth [ast]
  (if (integer? ast) 0 (inc (max (depth (nth ast 1)) (depth (nth ast 2))))))
(defn render [ast]
  (if (integer? ast) (str ast)
      (str "(" (render (nth ast 1)) "+" (render (nth ast 2)) ")")))
(defn evidence [broken? ast]
  (let [text (render ast)
        tree (parser text)
        actual (insta/transform {:expr identity :number parse-long :add (if broken? - +)} tree)]
    {:ast ast :text text :tree tree :expected (reduce + 0 (leaves ast))
     :actual actual :parse-failed? (insta/failure? tree)}))
(def captured (atom nil))
(defn check! [broken? ast]
  (let [{:keys [actual expected parse-failed?] :as ev} (evidence broken? ast)]
    (when (or parse-failed? (not= actual expected))
      (when (h/final?) (reset! captured ev))
      (throw (ex-info "Printed AST meaning changed" (assoc ev :hegel/origin "parser-meaning/evaluation"))))
    ev))
(defn property [broken?]
  (fn [_]
    (let [ast (h/draw! ast-gen)
          ns (leaves ast) d (depth ast)]
      (h/event! (if (zero? d) "leaf" "addition"))
      (when (> d 1) (h/event! "nested"))
      (when (some zero? ns) (h/event! "zero"))
      (when (some #(>= % 10) ns) (h/event! "multi-digit"))
      (h/observe! d "depth")
      (h/observe! (count ns) "leaves")
      (check! broken? ast))))
(def provenance
  {:hegel-sha "8179cb83eda81c708dc834ee38647f2354fe3d55"
   :libhegel-version "0.51.6"
   :runtime {:host :jolt :version "0.8.27" :os "Linux" :arch "amd64"}
   :property-id "parser-meaning/subtraction-control"
   :generator-revision "bounded-add-ast-v1"
   :model-revision "leaf-sum-v1"})
(defn portable [x]
  (walk/postwalk (fn [v] (if (instance? Throwable v)
                          {:message (ex-message v) :data (ex-data v)} v)) x))
(defn save! [path value] (spit path (pr-str (portable value))))
(defn run! [broken?]
  (reset! captured nil)
  (let [result (h/run-test!
                {:test-cases 300 :seed 20261116 :database "" :verbosity :quiet
                 :observations? true
                 :coverage {:scope :exploration
                            :requirements (zipmap ["leaf" "addition" "nested" "zero" "multi-digit"]
                                                  (repeat {:min-count 1}))}}
                (property broken?))
        stem (if broken? "control" "correct")]
    (save! (str stem "-result.edn") result)
    (prn (select-keys result [:passed? :status :seed :valid-test-cases :n-failures :flaky? :coverage :observations]))
    (when broken?
      (save! "counterexample.edn" @captured)
      (spit "control-replay.edn" (codec/encode (bundle/from-result provenance result))))
    (when-not (= (not broken?) (:passed? result))
      (throw (ex-info "Unexpected verdict" {:result result})))
    result))
(defn -main [& [mode]]
  (case mode
    "correct" (run! false)
    "control" (run! true)
    "replay" (let [result (h/replay-bundle! provenance (codec/decode (slurp "control-replay.edn")) (property true))]
                 (save! "replay-result.edn" result)
                 (prn result)
                 (when-not (:reproduced? result) (throw (ex-info "Replay failed" {}))))
    "fixture" (let [ast (:ast (edn/read-string (slurp "counterexample.edn")))
                    correct (evidence false ast) broken (evidence true ast)]
                (prn {:correct correct :broken broken})
                (assert (= (:expected correct) (:actual correct)))
                (assert (not= (:expected broken) (:actual broken))))))
