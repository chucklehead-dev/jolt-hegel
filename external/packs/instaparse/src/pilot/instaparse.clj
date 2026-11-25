(ns pilot.instaparse
  (:require [clojure.string :as str]
            [instaparse.core :as insta]
            [instaparse.repeat :as repeat]
            [hegel.generator :as g]
            [pilot.runner :as runner]))

;; Independently authored from the Hegel grammar: no shared grammar translator.
(def parser (insta/parser "expr = integer | <'('> expr <ws> <'+'> <ws> expr <')'>; integer = #'[0-9]{1,3}'; ws = #' *'"))
(def rejecting-parser (insta/parser "expr = #'[1-9]{1,3}'"))
(def expressions
  (g/grammar {:start :expr
              :rules {:expr [:alt :integer [:cat "(" :expr :ws "+" :ws :expr ")"]]
                      :integer [:repeat {:min 1 :max 3} [:char-range "0" "9"]]
                      :ws [:repeat {:min 0 :max 2} " "]}}
             {:max-size 64 :max-depth 12}))
(def repetition (insta/parser "S = A*; A = 'ab'"))
(def repetition-enlive (insta/parser "S = A*; A = 'ab'" :output-format :enlive))
(def asts (g/recursive {:max-depth 4 :max-leaves 12} (g/integer 0 99)
                       (fn [child] (g/tuple (g/just :add) child child))))
(defn print-ast [ast]
  (if (integer? ast) (str ast)
      (str "(" (print-ast (nth ast 1)) " + " (print-ast (nth ast 2)) ")")))
(defn evaluate [ast]
  (if (integer? ast) ast (+ (evaluate (nth ast 1)) (evaluate (nth ast 2)))))
(defn parsed-value [text mutant?]
  (insta/transform {:integer #(parse-long %)
                    :expr (fn [& values] (if (= 1 (count values)) (first values)
                                             (apply (if mutant? - +) values)))}
                   (parser text)))
(def properties
  [{:id :acceptance :generator expressions
    :labels (fn [s] [(if (str/includes? s "(") "recursive" "leaf")
                      (if (str/includes? s " ") "whitespace" "compact")])
    :coverage ["recursive" "leaf" "whitespace" "compact"]
    :check (fn [s mutant?]
             (runner/require! :acceptance (not (insta/failure? ((if mutant? rejecting-parser parser) s)))
                              {:input s}))}
   {:id :optimization :generator (g/fmap #(apply str %) (g/vector {:max-size 30} (g/just "ab")))
    :labels (fn [s] [(if (empty? s) "empty" "nonempty")]) :coverage ["empty" "nonempty"]
    :check (fn [s mutant?]
             (doseq [p [repetition repetition-enlive]]
               (let [normal (p s) optimized (p s :optimize :memory)
                     actual (if (and mutant? (seq s)) [:lost-child] optimized)]
                 (when (seq s)
                   (runner/require! :optimization (repeat/used-memory-optimization? optimized)
                                    {:kind :optimization-not-exercised :input s}))
                 (runner/require! :optimization (= normal actual) {:normal normal :optimized actual}))))}
   {:id :meaning :generator asts
    :labels (fn [ast] [(if (integer? ast) "leaf" "addition")]) :coverage ["leaf" "addition"]
    :check (fn [ast mutant?]
             (let [text (print-ast ast) expected (evaluate ast) actual (parsed-value text mutant?)]
               (runner/require! :meaning (= expected actual) {:text text :expected expected :actual actual})))}])
(defn -main [& args] (runner/main! :instaparse properties args))
