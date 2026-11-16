(ns hegel.grammar-test
  (:require [clojure.test :refer [deftest is testing]]
            [hegel.grammar :as grammar]
            [hegel.generator :as g]
            [hegel.core :as h]))

(def expression-grammar
  {:start :expr
   :rules {:expr [:alt :integer [:cat "(" :expr " + " :expr ")"]]
           :integer [:repeat {:min 1 :max 4} :digit]
           :digit [:char-range "0" "9"]}})

(defn- invalid-data [spec]
  (try (grammar/compile spec) nil
       (catch Exception error (ex-data error))))

(deftest compilation-is-stable-and-inspectable
  (is (= {:source "digit: \"0\"..\"9\"\nexpr: (integer | (\"(\" expr \" + \" expr \")\"))\ninteger: (digit)~1..4\n"
          :start "expr"}
         (grammar/compile expression-grammar)))
  (is (= (grammar/compile expression-grammar)
         (grammar/compile (assoc expression-grammar :rules
                                 (into (sorted-map) (:rules expression-grammar))))))
  (is (= {:source "some_rule: \"yes\"\n" :start "some_rule"}
         (grammar/compile {:start :some-rule :rules {:some-rule "yes"}}))))

(deftest malformed-grammars-have-data-paths
  (doseq [[spec path]
          [[{} [:rules]]
           [{:rules {:start :missing}} [:rules :start]]
           [{:rules {:start [:cat]}} [:rules :start]]
           [{:rules {:start [:unknown "a"]}} [:rules :start 0]]
           [{:rules {:start [:optional "a" "b"]}} [:rules :start]]
           [{:rules {:start [:repeat {:min 3 :max 2} "a"]}} [:rules :start 1]]
           [{:rules {:start [:repeat {:min -1 :max 2} "a"]}} [:rules :start 1 :min]]
           [{:rules {:start [:repeat {:min 0} "a"]}} [:rules :start 1 :max]]
           [{:rules {:start [:char-range "ab" "z"]}} [:rules :start 1]]
           [{:rules {:start [:char-range "z" "a"]}} [:rules :start]]
           [{:rules {:start "x"} :start :absent} [:start]]
           [{:rules {:start "x"} :ignore " "} [:ignore]]
           [{:rules {:start "x" :other/rule "x"}} [:rules :other/rule]]
           [{:rules {:start "x" :some-rule "x" :some_rule "y"}} [:rules]]
           [{:rules {:start :start}} [:rules :start]]]]
    (testing (pr-str spec)
      (let [data (invalid-data spec)]
        (is (= path (:path data)))
        (is (true? (:hegel/usage-error? data)))))))

(deftest literals-are-escaped-and-preserved
  (doseq [literal ["" "quote\"\\slash\n\r\t" "\u0000" "é😀" "\"\nother: \"injection"]]
    (is (= [literal] (h/sample 1 (g/grammar {:rules {:start literal}}))))))

(deftest operators-generate-and-shrink-through-native-grammar
  (is (= ["abab"] (h/sample 1 (g/grammar {:rules {:start [:repeat {:min 2 :max 2}
                                                                        [:cat "a" "b"]]}}))))
  (is (= [""] (h/sample 1 (g/grammar {:rules {:start [:repeat {:min 0 :max 0} "a"]}}))))
  (is (every? #{"" "a"} (h/sample 20 (g/grammar {:rules {:start [:optional "a"]}}))))
  (is (every? #{"😀" "😁"} (h/sample 20 (g/grammar {:rules {:start [:char-range "😀" "😁"]}}))))
  (let [gen (g/grammar expression-grammar {:max-size 40 :max-depth 12})
        result (h/run-test! {:test-cases 20 :seed 9 :database "" :verbosity :quiet}
                           (fn [_]
                             (throw (ex-info "expression fails"
                                             {:hegel/origin "grammar-dsl"
                                              :value (h/draw! gen)}))))]
    (is (= "0" (-> result :final first :exception ex-data :value)))
    (is (false? (:flaky? result))))
  (is (= ["hello"] (h/sample 1 (g/grammar {:start :greeting :rules {:greeting "hello"}})))))

(deftest grammar-remains-an-ordinary-hegel-generator
  (is (= [{:query "hello"}]
         (h/sample 1 (g/fmap #(hash-map :query %)
                            (g/grammar {:rules {:start "hello"}})))))
  (is (:hegel/usage-error?
       (try (g/grammar expression-grammar {:start "digit"}) nil
            (catch Exception error (ex-data error))))))
