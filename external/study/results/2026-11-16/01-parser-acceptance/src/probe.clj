(ns probe
  (:require [hegel.core :as h]
            [hegel.generator :as g]
            [hegel.replay-bundle :as bundle]
            [hegel.replay-bundle.codec :as codec]
            [instaparse.core :as insta]
            [clojure.edn :as edn]
            [clojure.string :as str]))

;; Independently authored EBNF: repetitions express sums, atom recurses via groups.
(def good (insta/parser "S = WS Expr WS
Expr = Atom (WS '+' WS Atom)*
Atom = Number | '(' WS Expr WS ')'
Number = #'[0-9]+'
<WS> = #' *'"))
;; Semantic mutation: valid parenthesized expressions are no longer in Atom.
(def broken (insta/parser "S = WS Expr WS
Expr = Atom (WS '+' WS Atom)*
Atom = Number
Number = #'[0-9]+'
<WS> = #' *'"))

(def tokens
  (g/recursive {:max-depth 4 :max-leaves 12}
    (g/fmap (fn [n] [(str n)]) (g/integer 0 9999))
    (fn [sub]
      (g/one-of
        [(g/fmap (fn [xs] (vec (concat ["("] xs [")"]))) sub)
         (g/fmap (fn [[a b]] (vec (concat a ["+"] b))) (g/tuple sub sub))]))))

(def inputs
  (g/bind
    (fn [ts]
      (g/fmap (fn [gaps]
                (apply str (concat (mapcat vector gaps ts) [(last gaps)])))
              (g/vector {:size (inc (count ts))} (g/sampled-from ["" " " "  "]))))
    tokens))

(defn check-input! [parser input]
  (when (insta/failure? (insta/parse parser input :partial false))
    (throw (ex-info "Generated arithmetic input rejected"
                    {:hegel/origin "arithmetic/complete-acceptance" :input input}))))

(defn property [parser]
  (fn [_]
    (let [s (h/draw! inputs "arithmetic-input")
          group? (str/includes? s "(")
          plus? (str/includes? s "+")]
      (h/event! (if (or group? plus?) "recursive" "nonrecursive"))
      (h/event! (if (str/includes? s " ") "spaces" "no-spaces"))
      (when group? (h/event! "parentheses"))
      (when plus? (h/event! "addition"))
      (when (re-find #"[0-9]{2}" s) (h/event! "multi-digit"))
      (h/observe! (count s) "input-length")
      (check-input! parser s))))

(def provenance
  {:hegel-sha "8179cb83eda81c708dc834ee38647f2354fe3d55"
   :libhegel-version "0.51.6"
   :runtime {:host :jolt :version "0.8.27" :os "linux" :arch "x86_64"}
   :property-id "arithmetic/complete-acceptance-broken-parentheses"
   :generator-revision "tokens-depth4-leaves12-spaces-v1"
   :model-revision "independent-ebnf-no-parentheses-v1"})

(def opts {:test-cases 200 :seed 424242 :database "" :verbosity :quiet
           :observations? true})
(defn summary [r]
  (select-keys r [:passed? :status :seed :valid-test-cases :invalid-test-cases
                  :overrun-test-cases :n-failures :flaky? :observations :coverage
                  :counterexample :final :failures :error]))
(defn save! [path value] (spit path (pr-str value)))
(defn -main [& [mode]]
  (case mode
    "good"
    (let [r (h/run-test!
              (assoc opts :coverage
                {:scope :exploration
                 :requirements (zipmap ["recursive" "nonrecursive" "spaces" "no-spaces"
                                       "parentheses" "addition" "multi-digit"]
                                      (repeat {:min-count 1}))}) (property good))]
      (save! "good-result.edn" (summary r))
      (prn (summary r))
      (when-not (:passed? r) (throw (ex-info "Good property failed" {}))))
    "broken"
    (let [r (h/run-test! opts (property broken))]
      (save! "broken-result.edn" (summary r))
      (prn (summary r))
      (when (or (:passed? r) (:flaky? r) (zero? (:n-failures r)))
        (throw (ex-info "Control did not yield a stable failure" {})))
      (spit "failure-bundle.edn" (codec/encode (bundle/from-result provenance r))))
    "replay"
    (let [r (h/replay-bundle! provenance (codec/decode (slurp "failure-bundle.edn"))
                              (property broken))]
      (save! "replay-result.edn" (summary r))
      (save! "replay-verdict.edn" (select-keys r [:status :reproduced? :flaky?]))
      (prn r)
      (when-not (:reproduced? r) (throw (ex-info "Replay did not reproduce" {}))))
    "fixture"
    (let [s (:input (edn/read-string (slurp "counterexample.edn")))
          result {:input s :good-accepts? (not (insta/failure? (insta/parse good s :partial false)))
                  :broken-rejects? (insta/failure? (insta/parse broken s :partial false))
                  :trailing-junk-rejected? (insta/failure? (insta/parse good (str s "x") :partial false))}]
      (save! "fixture-result.edn" result)
      (prn result)
      (when-not (every? true? (vals (dissoc result :input)))
        (throw (ex-info "Fixture control failed" result))))))
