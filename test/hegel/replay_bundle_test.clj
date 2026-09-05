(ns hegel.replay-bundle-test
  (:require [clojure.test :refer [deftest is testing]]
            [hegel.replay-bundle :as bundle]))

(def provenance
  {:hegel-sha "0123456789abcdef0123456789abcdef01234567"
   :libhegel-version "0.36.3"
   :runtime {:host :bb :version "1.13.220" :os "linux" :arch "x86_64"}
   :property-id "example/replay"
   :generator-revision "generator-v1"
   :model-revision nil})

(def valid-bundle
  {:format :hegel/replay-bundle
   :schema-version 1
   :provenance provenance
   :seed "18446744073709551615"
   :options {:test-cases 10
             :phases [:generate :shrink]
             :suppress-health-checks [:too-slow]}
   :failures [{:origin "example/property"
               :reproduction-blob "blob-v1"}]})

(defn- error-data [thunk]
  (try
    (thunk)
    nil
    (catch clojure.lang.ExceptionInfo error
      (ex-data error))))

(defn- reason-of [thunk]
  (:reason (error-data thunk)))

(defn- nested-vector [depth]
  (loop [value :leaf
         remaining depth]
    (if (zero? remaining)
      value
      (recur [value] (dec remaining)))))

(deftest validates-a-portable-versioned-literal
  (is (= valid-bundle (bundle/validate! valid-bundle)))
  (is (= provenance (bundle/validate-provenance! provenance)))
  (is (= {:test-cases 10
          :phases [:generate :shrink]
          :suppress-health-checks [:too-slow]}
         (bundle/snapshot-options
          {:test-cases 10
           :seed 42
           :database ""
           :database-key "not-exported"
           :name "not-exported"
           :phases [:generate :shrink]
           :suppress-health-checks [:too-slow]})))
  (is (= [:generate :shrink]
         (:phases (bundle/snapshot-options {:phases '(:generate :shrink)}))))
  (is (= [:generate :shrink]
         (:phases (bundle/snapshot-options {:phases #{:shrink :generate}}))))
  (is (= {:phases []} (bundle/snapshot-options {:phases '()})))
  (is (= {:max-text-chars 262144 :max-depth 32 :max-nodes 8192
          :max-string-chars 8192 :max-failures 16 :max-trace-events 256}
         bundle/limits)))

(deftest rejects-closed-schema-and-provenance-mutations
  (doseq [[path thunk]
          [[[:unknown-key] #(bundle/validate! (assoc valid-bundle :unknown true))]
           [[:schema-version] #(bundle/validate! (assoc valid-bundle :schema-version 2))]
           [[:provenance :unknown-key]
            #(bundle/validate! (assoc-in valid-bundle [:provenance :unknown] :x))]
           [[:options :unknown-key]
            #(bundle/validate! (assoc-in valid-bundle [:options :database] ""))]
           [[:failures 0 :unknown-key]
            #(bundle/validate! (assoc-in valid-bundle [:failures 0 :exception] :opaque))]
           [[:trace :unknown-key]
            #(bundle/validate! (assoc valid-bundle
                                      :trace {:contract-id "c"
                                              :contract-revision "r"
                                              :events []
                                              :unknown true}))]
           [[:provenance :model-revision]
            #(bundle/validate! (assoc-in valid-bundle
                                         [:provenance :model-revision] ""))]]]
    (let [data (error-data thunk)]
      (is (= :hegel.replay-bundle/invalid-bundle (:type data)))
      (is (true? (:hegel/usage-error? data)))
      (is (= path (:path data)))
      (is (keyword? (:reason data)))
      (is (not (contains? data :value)))))
  (let [raw-key (apply str (repeat 128 "private-key"))
        data (error-data #(bundle/validate! (assoc valid-bundle raw-key true)))]
    (is (= [:unknown-key] (:path data)))
    (is (not-any? #(= raw-key %) (:path data)))))

(deftest rejects-noncanonical-seeds-and-option-domain-escapes
  (doseq [seed ["" "01" "-1" "18446744073709551616" 7]]
    (let [data (error-data #(bundle/validate! (assoc valid-bundle :seed seed)))]
      (is (= [:seed] (:path data)))))
  (doseq [options [{:test-cases 0}
                   {:test-cases 18446744073709551616N}
                   {:stateful-step-count 0}
                   {:derandomize? :truthy}
                   {:phases [:unknown]}
                   {:suppress-health-checks #{:too-slow}}]]
    (let [data (error-data #(bundle/validate! (assoc valid-bundle
                                                       :options options)))]
      (is (true? (:hegel/usage-error? data)))))
  (let [data (error-data #(bundle/snapshot-options
                           {:phases (lazy-seq (cons :generate nil))}))]
    (is (= :portable-enum-collection-required (:reason data))))
  (is (= :max-nodes
         (:reason (error-data #(bundle/snapshot-options
                                {:phases (apply list (repeat 8193 :generate))}))))))

(deftest rejects-bounded-and-opaque-values-before-schema-traversal
  (let [too-long (assoc-in valid-bundle [:failures 0 :reproduction-blob]
                           (apply str (repeat 8193 "x")))
        too-deep (assoc valid-bundle :trace
                        {:contract-id "c" :contract-revision "r"
                         :events [(nested-vector 33)]})
        too-many-nodes (assoc valid-bundle :trace
                              {:contract-id "c" :contract-revision "r"
                               :events [(vec (repeat 8192 :x))]})
        too-many-events (assoc valid-bundle :trace
                               {:contract-id "c" :contract-revision "r"
                                :events (vec (repeat 257 {}))})]
    (testing "individual text, nesting, and node limits"
      (is (= :max-string-chars (reason-of #(bundle/validate! too-long))))
      (is (= :max-depth (reason-of #(bundle/validate! too-deep))))
      (is (= :max-nodes (reason-of #(bundle/validate! too-many-nodes))))
      (is (= :max-trace-events (reason-of #(bundle/validate! too-many-events))))))
  (testing "opaque and nonportable values do not reach a schema coercion"
    (doseq [value [(Object.) '(lazy) #{:a} \x 3/2 ##Inf]]
      (is (= :opaque-value
             (reason-of #(bundle/validate!
                          (assoc-in valid-bundle [:failures 0 :origin]
                                    value)))))))
  (is (= :nul-string
         (reason-of #(bundle/validate!
                      (assoc-in valid-bundle [:failures 0 :origin]
                                "bad\u0000text")))))
  (is (= :max-string-chars
         (reason-of #(bundle/validate!
                      (assoc-in valid-bundle [:failures 0 :origin]
                                (keyword (apply str (repeat 8193 "k"))))))))
  (let [too-much-text
        (assoc valid-bundle :trace
               {:contract-id "c"
                :contract-revision "r"
                :events (vec (repeat 33
                                    {:text (apply str (repeat 8192 "x"))}))})]
    (is (= :max-text-chars (reason-of #(bundle/validate! too-much-text))))))

(deftest validates-trace-events-as-portable-map-data
  (let [trace {:contract-id "journal"
               :contract-revision "r2"
               :events [{:phase :enter :nested [{:id 1 :ok? true}]}]}
        checked (bundle/validate! (assoc valid-bundle :trace trace))]
    (is (= trace (:trace checked)))
    (is (= [:trace :events 0]
           (:path (error-data #(bundle/validate!
                                (assoc valid-bundle :trace
                                       (assoc trace :events [[:not-a-map]])))))))))

(def stable-result
  {:status :failed
   :passed? false
   :flaky? false
   :seed "17"
   :replay-options {:test-cases 4 :seed 17 :database "" :verbosity :quiet}
   :failures [{:origin "example/property"
               :reproduction-blob "blob-v1"
               :reproduced? true
               :exception (ex-info "never exported" {})
               :value {:never "exported"}}]})

(deftest exports-only-stable-replay-data-and-redacts-explicit-traces
  (let [calls (atom [])
        exported (bundle/from-result
                  provenance stable-result
                  {:trace {:contract-id "private"
                           :contract-revision "r1"
                           :events [{:token "remove" :phase :enter}]}
                   :redact-trace (fn [trace]
                                   (swap! calls conj trace)
                                   (assoc trace :contract-id "public"
                                          :events [{:phase :enter}]))})]
    (is (= 1 (count @calls)))
    (is (= {:test-cases 4 :verbosity :quiet} (:options exported)))
    (is (= [{:origin "example/property" :reproduction-blob "blob-v1"}]
           (:failures exported)))
    (is (= {:contract-id "public" :contract-revision "r1"
            :events [{:phase :enter}]}
           (:trace exported)))
    (is (not (contains? (first (:failures exported)) :exception)))
    (is (not (contains? (first (:failures exported)) :value))))
  (is (not (contains? (bundle/from-result provenance stable-result) :trace)))
  (doseq [result [(assoc stable-result :flaky? true)
                  (assoc stable-result :status :passed :passed? true)
                  (assoc stable-result :failures
                         [(assoc (first (:failures stable-result)) :reproduced? false)])
                  (dissoc stable-result :replay-options)]]
    (is (true? (:hegel/usage-error?
                (error-data #(bundle/from-result provenance result))))))
  (is (= :max-failures
         (:reason (error-data
                   #(bundle/from-result
                     provenance
                     (assoc stable-result :failures
                            (vec (repeat 17 (first (:failures stable-result))))))))))
  (is (= :opaque-value
         (:reason (error-data
                   #(bundle/from-result provenance stable-result
                                        {:trace {:contract-id "c"
                                                 :contract-revision "r"
                                                 :events []}
                                         :redact-trace (fn [_]
                                                         {:contract-id "c"
                                                          :contract-revision "r"
                                                          :events [(Object.)]})})))))
  (let [close-error (ex-info "redactor failed" {:marker :same})
        observed (try
                   (bundle/from-result provenance stable-result
                                       {:trace {:contract-id "c"
                                                :contract-revision "r" :events []}
                                        :redact-trace (fn [_] (throw close-error))})
                   nil
                   (catch clojure.lang.ExceptionInfo error error))]
    (is (identical? close-error observed))))

(deftest compatibility-compares-only-stable-provenance-paths
  (let [identical (bundle/compatibility provenance valid-bundle)
        changed (bundle/compatibility
                 provenance
                 (assoc-in valid-bundle [:provenance :runtime :arch] "aarch64"))]
    ;; A false-positive mutant that treats nil model revisions as mismatched
    ;; would fail this exact compatible control.
    (is (= {:compatible? true :mismatches []} identical))
    (is (= {:compatible? false
            :mismatches [{:path [:runtime :arch]
                          :expected "x86_64"
                          :actual "aarch64"}]}
           changed))))

(defn -main [& _]
  (let [{:keys [fail error]} (clojure.test/run-tests 'hegel.replay-bundle-test)]
    (when (pos? (+ fail error))
      (throw (ex-info "replay bundle tests failed" {:fail fail :error error})))))
