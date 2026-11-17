(ns study
  (:require [yyjson.core :as json] [hegel.core :as h] [hegel.generator :as g]
            [hegel.replay-bundle :as bundle] [hegel.replay-bundle.codec :as codec]))
(def provenance {:hegel-sha "6321d5ab485db656a48823030453bc4b2d0af1cc" :libhegel-version "0.51.6"
 :runtime {:host :jolt :version "0.8.27" :os "Linux" :arch "amd64"}
 :property-id "numeric/exact-integer-control" :generator-revision "v1" :model-revision "exact-text-v1"})
(def summaries (atom []))
(defn check! [id input expected actual]
 (when-not (= expected actual) (throw (ex-info "Numeric contract mismatch" {:hegel/origin id :input input :expected expected :actual actual}))))
(defn run! [id n property expected-pass]
 (let [r (h/run-test! {:name id :test-cases n :seed 20261116 :database "" :verbosity :quiet} property)
       summary (assoc (select-keys r [:passed? :status :seed :test-cases :valid-test-cases :invalid-test-cases :interesting-test-cases :n-failures :flaky?]) :property id)]
  (prn summary) (swap! summaries conj summary)
  (assert (= expected-pass (:passed? r)) (str "Unexpected verdict: " id))
  (assert (not (:flaky? r))) r))
(defn contexts [v] [v [v] {"n" v} {"n\u0000" v} {"n" [v]}])
(defn check-contexts! [id v normalized pretty]
 (doseq [[input expected] (map vector (contexts v) (contexts normalized))]
  (check! id (pr-str input) expected (json/read-str (json/write-str input :pretty pretty)))))
(defn control [_]
 (let [n (+ 9007199254740992N (h/draw! (g/integer 0 1000)))
       actual (double (json/read-str (str n)))]
  (check! "control/lossy-integer-conversion" (str n) (str n) (str (bigint actual)))))
(defn -main [& [mode]]
 (case mode
  "replay" (let [b (codec/decode (slurp "control-bundle.edn"))
                 r (h/replay-bundle! provenance b control)
                 mismatch (h/replay-bundle! (assoc provenance :generator-revision "wrong") b (fn [_] (throw (ex-info "Must not run" {}))))]
             (prn (select-keys r [:status :reproduced? :flaky?]))
             (assert (= :reproduced (:status r)))
             (assert (= :incompatible (:status mismatch))))
  (do
   (run! "signed-and-unsigned-integer-read" 1000
    (fn [_] (let [n (h/draw! (g/big-integer -9223372036854775808N 18446744073709551615N))]
      (check! "integer/read" (str n) n (json/read-str (str n))))) true)
   (run! "arbitrary-integer-exact-write" 500
    (fn [_] (let [n (h/draw! (g/big-integer -10000000000000000000000000000000000000000N 10000000000000000000000000000000000000000N))]
      (check! "integer/write" (str n) (str n) (json/write-str n))
      (check! "integer/write-array" (str n) (str "[" n "]") (json/write-str [n])))) true)
   (run! "finite-double-roundtrip-contexts" 2000
    (fn [_] (let [v (h/draw! (g/double {:nan? false :infinity? false}))]
      (check-contexts! "double/roundtrip" v v (h/draw! (g/boolean))))) true)
   (run! "decimal-verbatim-and-options" 500
    (fn [_] (let [coefficient (h/draw! (g/integer)) exponent (h/draw! (g/integer -400 400))
                 v (bigdec (str coefficient "E" exponent))]
      (check! "decimal/exact-write" (str v) (str v) (json/write-str v))
      (check! "decimal/map-value-fn" (str v) (str "{\"n\":" v "}")
              (json/write-str {:n 0} :value-fn (fn [_ _] v))))) true)
   (run! "ratios-as-double" 500
    (fn [_] (let [n (h/draw! (g/integer -1000000000 1000000000)) d (h/draw! (g/integer 1 1000000)) v (/ n d)]
      (check-contexts! "ratio/as-double" v (if (ratio? v) (double v) v) false))) true)
   (doseq [n [-9223372036854775808N -9223372036854775807N -1 0 1 9007199254740991N 9007199254740992N 9007199254740993N 9223372036854775807N 9223372036854775808N 18446744073709551615N]]
    (check-contexts! "boundary/integer" n n false))
   (doseq [v [0.0 -0.0 5.0E-324 -5.0E-324 1.7976931348623157E308 -1.7976931348623157E308]]
    (check-contexts! "boundary/finite-double" v v true))
   (doseq [v [##NaN ##Inf ##-Inf] c (contexts v)]
    (check! "nonfinite/rejection" (pr-str c) :write
     (try (json/write-str c) :accepted (catch Exception e (:yyjson/error (ex-data e))))))
   (doseq [s ["-9223372036854775809" "18446744073709551616" "18446744073709551617" "1e309"]]
     (prn :outside-exact-domain s (try {:value (json/read-str s) :type (str (type (json/read-str s)))} (catch Exception e (ex-data e)))))
   (let [r (run! "semantic-broken-control" 100 control false)]
    (spit "control-minimum.edn" (pr-str (-> r :final first :exception ex-data)))
    (spit "control-bundle.edn" (codec/encode (bundle/from-result provenance r))))
   (spit "summaries.edn" (pr-str @summaries))
   (println "PASS: actual target properties passed; semantic control failed as expected."))))
(apply -main *command-line-args*)
