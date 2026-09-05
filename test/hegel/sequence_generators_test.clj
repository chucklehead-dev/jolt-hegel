(ns hegel.sequence-generators-test
  (:require [clojure.test :refer [deftest is]]
            [hegel.core :as h]
            [hegel.generator :as g]
            [hegel.ffi :as hffi]))

(def ^:private test-case {:context :context :handle :handle})

(defn- thrown-data [thunk]
  (try
    (thunk)
    nil
    (catch clojure.lang.ExceptionInfo error
      (ex-data error))))

(defn- scripted-draw [generator more-results choices]
  (let [more-results (atom more-results)
        choices (atom choices)
        frees (atom 0)
        spans (atom [])]
    {:value
     (with-redefs [hffi/start-span! (fn [& _] (swap! spans conj :start))
                   hffi/stop-span! (fn [& _] (swap! spans conj :stop))
                   hffi/new-collection! (fn [& _] :collection)
                   hffi/collection-more! (fn [& _]
                                           (let [result (first @more-results)]
                                             (swap! more-results next)
                                             result))
                   hffi/collection-free! (fn [& _] (swap! frees inc))
                   hffi/generate-integer! (fn [& _]
                                             (let [choice (first @choices)]
                                               (swap! choices next)
                                               choice))]
       (generator test-case))
     :frees frees
     :spans spans}))

(deftest sequence-constructors-reject-invalid-options-before-a-draw
  (doseq [thunk [#(g/subsequences nil [1 2])
                 (fn []
                   ;; Deliberately violate the public options-map contract.
                   #_{:clj-kondo/ignore [:type-mismatch]}
                   (g/subsequences :not-a-map [1 2]))
                 #(g/samples nil [1 2])
                 (fn []
                   ;; Deliberately violate the public options-map contract.
                   #_{:clj-kondo/ignore [:type-mismatch]}
                   (g/samples :not-a-map [1 2]))
                 #(g/subsequences {:unknown true} [])
                 #(g/samples {:unknown true} [])
                 #(g/samples {:replacement? :truthy} [1 2])
                 #(g/subsequences {:max-size 3} [1 2])
                 #(g/samples {:replacement? false :max-size 3} [1 2])
                 #(g/samples {:min-size 1} [])
                 #(g/samples {:max-size 1} [])]]
    (let [data (thrown-data thunk)]
      (is (= :hegel.generator/invalid-option (:type data)))
      (is (true? (:hegel/usage-error? data)))))
  ;; Regression for no-replacement's default finite upper bound: no explicit
  ;; :max-size must be legal and must not construct a native collection yet.
  (is (g/generator? (g/samples {:replacement? false} [1 2])))
  (is (g/generator? (g/subsequences {} [1 2]))))

(deftest sequence-generators-use-index-choices-and-preserve-positions
  (let [source [{:position 0 :value :x}
                {:position 1 :value :x}
                {:position 2 :value :y}]
        permutation (scripted-draw (g/permutations source)
                                   [true true true false] [2 0 0])
        subsequence (scripted-draw (g/subsequences {:size 2} source)
                                   [true true false] [2 0])
        sample-without (scripted-draw
                        (g/samples {:size 2 :replacement? false} source)
                        [true true false] [2 0])
        sample-with (scripted-draw (g/samples {:size 3} source)
                                  [true true true false] [1 1 0])]
    ;; The three results distinguish positional duplicate handling from a set,
    ;; dedupe, or value-based removal mutant.
    (is (= [2 0 1] (mapv :position (:value permutation))))
    (is (= [0 2] (mapv :position (:value subsequence))))
    (is (= [2 0] (mapv :position (:value sample-without))))
    (is (= [1 1 0] (mapv :position (:value sample-with))))
    (doseq [result [permutation subsequence sample-without sample-with]]
      (is (= 1 @(:frees result)))
      (is (= [:start :stop] @(:spans result))))))

(deftest sequence-generators-retain-equal-duplicate-values-by-position
  (let [source [:x :x :y]
        permutation (scripted-draw (g/permutations source)
                                   [true true true false] [2 0 0])
        full-subsequence (scripted-draw (g/subsequences {:size 3} source)
                                        [true true true false] [0 0 0])
        without-replacement (scripted-draw
                             (g/samples {:size 3 :replacement? false} source)
                             [true true true false] [2 0 0])]
    ;; Equality cannot stand in for source position: these controls retain both
    ;; indistinguishable :x positions through permutation, full subsequence,
    ;; and no-replacement selection.
    (is (= [:y :x :x] (:value permutation)))
    (is (= source (:value full-subsequence)))
    (is (= [:y :x :x] (:value without-replacement)))
    (doseq [result [permutation full-subsequence without-replacement]]
      (is (= {:x 2 :y 1} (frequencies (:value result)))))))

(deftest sequence-generators-have-nonvacuous-choice-and-minimum-controls
  (let [source [3 1 2]
        original (scripted-draw (g/permutations source)
                                [true true true false] [0 0 0])
        reordered (scripted-draw (g/permutations source)
                                 [true true true false] [2 0 0])
        subsequence-minimum (scripted-draw
                             (g/subsequences {:size 2} source)
                             [true true false] [0 0])
        with-replacement-minimum (scripted-draw
                                  (g/samples {:size 3} source)
                                  [true true true false] [0 0 0])
        without-replacement-minimum (scripted-draw
                                     (g/samples {:size 2 :replacement? false} source)
                                     [true true false] [0 0])]
    ;; Zero integer choices are the native simplest path: preserve upstream's
    ;; documented minima without replacing engine choices with a host shuffle.
    (is (= source (:value original)))
    (is (= [2 3 1] (:value reordered)))
    (is (not= (:value original) (:value reordered)))
    (is (= [3 1] (:value subsequence-minimum)))
    (is (= [3 3 3] (:value with-replacement-minimum)))
    (is (= [3 1] (:value without-replacement-minimum)))))

(deftest empty-sequences-have-only-the-empty-result
  (doseq [generator [(g/permutations [])
                     (g/subsequences [])
                     (g/subsequences {:size 0} [])
                     (g/samples [])
                     (g/samples {:size 0} [])
                     (g/samples {:replacement? false} [])]]
    (is (= [] (generator test-case)))))

(def ^:private native-source
  [{:position 0 :value :x}
   {:position 1 :value :x}
   {:position 2 :value :y}])

(defn- native-sequence-run [seed]
  (let [observed (atom [])
        result (h/run-test!
                {:test-cases 64 :seed seed :database "" :verbosity :quiet}
                (fn [_]
                  (let [permutation (h/draw! (g/permutations native-source))
                        subsequence (h/draw! (g/subsequences native-source))
                        without (h/draw!
                                 (g/samples {:max-size 3 :replacement? false}
                                            native-source))
                        with (h/draw! (g/samples {:size 4} native-source))]
                    (swap! observed conj {:permutation permutation
                                          :subsequence subsequence
                                          :without without
                                          :with with}))))]
    {:result result :observed @observed}))

(deftest native-sequence-generators-preserve-cardinality-order-and-replacement
  (let [{result :result first-observed :observed} (native-sequence-run 20260905)
        {rerun-result :result rerun-observed :observed} (native-sequence-run 20260905)
        input-frequency (frequencies native-source)
        in-input? (set native-source)
        histogram (into {}
                        (for [family [:permutation :subsequence :without :with]]
                          [family (frequencies (map family first-observed))]))]
    (is (:passed? result))
    (is (:passed? rerun-result))
    ;; An explicit second run protects the seed contract independently of the
    ;; engine's automatic final-case replay machinery.
    (is (= first-observed rerun-observed))
    (is (seq first-observed))
    (is (every? #(= input-frequency (frequencies (:permutation %))) first-observed))
    (is (every? #(every? in-input? (:permutation %)) first-observed))
    (is (every? (fn [case]
                  (let [subsequence (:subsequence case)
                        positions (mapv :position subsequence)]
                    (and (<= 0 (count subsequence) (count native-source))
                         (every? in-input? subsequence)
                         (every? (fn [[value count]]
                                   (<= count (get input-frequency value 0)))
                                 (frequencies subsequence))
                         (or (empty? positions) (apply <= positions)))))
                first-observed))
    (is (every? (fn [case]
                  (let [without (:without case)
                        positions (mapv :position without)]
                    (and (<= 0 (count without) (count native-source))
                         (every? in-input? without)
                         (every? (fn [[value count]]
                                   (<= count (get input-frequency value 0)))
                                 (frequencies without))
                         (= (count without) (count (set positions))))))
                first-observed))
    ;; Four replacement draws from three positions forces a repeated position;
    ;; this is a nonvacuous positive witness, not a distribution claim.
    (is (every? #(and (= 4 (count (:with %)))
                      (every? in-input? (:with %))
                      (< (count (set (map :position (:with %)))) 4))
                first-observed))
    ;; This deterministic, bounded histogram is a coverage control, not a
    ;; uniformity assertion: each native choice family must expose at least two
    ;; witnessed outcomes for the fixed seed, without a fragile ratio bound.
    (is (every? #(<= 2 (count (get histogram %)))
                [:permutation :subsequence :without :with]))))

(deftest native-sequence-shrinking-replays-the-choice-minima
  (let [final-values (atom nil)
        result (h/run-test!
                {:test-cases 1 :seed 17 :database "" :verbosity :quiet
                 :report-multiple-failures? false}
                (fn [_]
                  (let [permutation (h/draw! (g/permutations [3 1 2]))
                        subsequence (h/draw! (g/subsequences {:size 2} [3 1 2]))
                        with (h/draw! (g/samples {:size 3} [3 1 2]))
                        without (h/draw!
                                 (g/samples {:size 2 :replacement? false}
                                            [3 1 2]))]
                    (when (h/final?)
                      (reset! final-values {:permutation permutation
                                            :subsequence subsequence
                                            :with with
                                            :without without}))
                    (throw (ex-info "sequence minima"
                                    {:hegel/origin "sequence-generators/minima"})))))]
    (is (false? (:passed? result)))
    (is (= "sequence-generators/minima" (:origin (first (:failures result)))))
    (is (true? (:reproduced? (first (:failures result)))))
    (is (false? (:flaky? result)))
    (is (= {:permutation [3 1 2]
            :subsequence [3 1]
            :with [3 3 3]
            :without [3 1]}
           @final-values))))

(defn -main [& _]
  (let [{:keys [fail error]} (clojure.test/run-tests 'hegel.sequence-generators-test)]
    (when (pos? (+ fail error))
      (throw (ex-info "sequence generator tests failed" {:fail fail :error error})))))
