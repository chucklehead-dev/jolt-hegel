(ns hegel.replay-bundle-native-test
  (:require [clojure.test :refer [deftest is]]
            [hegel.core :as h]
            [hegel.ffi :as hffi]
            [hegel.generator :as g]
            [hegel.host :as host]
            [hegel.replay-bundle :as bundle]
            [hegel.replay-bundle.codec :as codec]
            [hegel.stateful :as hs]
            [hegel.version :as version]))

;; Explicit test identity, not a runtime auto-discovery claim. Production
;; callers supply their independently recorded deployment/property manifest.
(def provenance
  {:hegel-sha "0123456789abcdef0123456789abcdef01234567"
   :libhegel-version version/libhegel-version
   :runtime {:host (host/runtime) :version "test-runtime" :os "test-os"
             :arch "test-arch"}
   :property-id "native-bundle/control" :generator-revision "v1"
   :model-revision nil})

(def step-bundle
  {:format :hegel/replay-bundle :schema-version 1
   :provenance provenance :seed "1"
   :options {:stateful-step-count 52 :verbosity :quiet}
   :failures [{:origin "hegel.stateful/invariant:below-fifty-one"
               ;; Independent v0.36.3 fixture, not produced by this test run.
               :reproduction-blob
               "AXic7cahEQAACMNAYtlfMS0g6jtAI3I/dTV/EMLQ4AKH1ANw"}]})

(defn- step-property [_]
  (hs/run! {:initial-state {:count 0}
            :rules [(hs/rule :inc #(update % :count inc))]
            :invariants [(hs/invariant :below-fifty-one #(< (:count %) 51))]}))

(defn- failing-property [_]
  (let [n (h/draw! (g/integer 1 10))]
    (throw (ex-info "positive integer" {:hegel/origin "native-bundle/positive"
                                       :draw n}))))

(defn- caught [thunk]
  (try (thunk) nil (catch Throwable e e)))

(deftest actual-result-exports-and-replays-without-starting-a-run
  (let [result (h/run-test! {:test-cases 1 :seed 1 :database ""
                             :name "not-exported" :verbosity :quiet}
                            failing-property)
        exported (codec/decode (codec/encode (bundle/from-result provenance result)))
        calls (atom 0)]
    (is (= {:test-cases 1 :verbosity :quiet} (:replay-options result)))
    (is (false? (:passed? result)))
    (is (false? (:flaky? result)))
    (is (= 1 (:draw (ex-data (get-in result [:final 0 :exception])))))
    (with-redefs [hffi/run-start! (fn [& _] (throw (ex-info "must not generate" {})))]
      (let [replay (h/replay-bundle! provenance exported
                                     (fn [tc]
                                       (swap! calls inc)
                                       (is (true? (h/final?)))
                                       (failing-property tc)))]
        (is (= :reproduced (:status replay)))
        (is (true? (:reproduced? replay)))
        (is (false? (:flaky? replay)))
        (is (= 1 @calls))
        (is (= 1 (:draw (ex-data (get-in replay [:failures 0 :exception])))))))))

(deftest direct-replay-preserves-stateful-budget-and-unsigned-seed
  (with-redefs [hffi/run-start! (fn [& _] (throw (ex-info "must not generate" {})))]
    (let [replayed (h/replay-bundle! provenance step-bundle step-property)
          too-short (h/replay-bundle! provenance
                                      (assoc-in step-bundle [:options :stateful-step-count] 50)
                                      step-property)
          max-seed (h/replay-bundle! provenance
                                     (assoc step-bundle :seed "18446744073709551615")
                                     step-property)]
      (is (= :reproduced (:status replayed)))
      (is (= 51 (count (::hs/trace (ex-data (get-in replayed [:failures 0 :exception]))))))
      (is (= :not-reproduced (:status too-short)))
      (is (true? (:flaky? too-short)))
      (is (= :reproduced (:status max-seed)))
      (is (= "18446744073709551615" (:seed max-seed))))))

(deftest mismatches-and-malformed-schema-do-not-allocate-or-execute
  (let [calls (atom [])
        forbidden (fn [& _] (swap! calls conj :native) (throw (ex-info "unexpected native" {})))
        property (fn [_] (swap! calls conj :property))]
    (with-redefs [hffi/ensure-compatible-version! forbidden
                  hffi/context-new! forbidden hffi/run-start! forbidden]
      (doseq [[path changed] [[[:hegel-sha] "abcdef0123456789abcdef0123456789abcdef01"]
                              [[:libhegel-version] "0.33.3"]
                              [[:runtime :version] "changed"]
                              [[:runtime :os] "changed"]
                              [[:runtime :arch] "changed"]
                              [[:property-id] "changed"]
                              [[:generator-revision] "changed"]
                              [[:model-revision] "changed"]]]
        (let [replayed (h/replay-bundle! provenance
                                         (assoc-in step-bundle (into [:provenance] path) changed)
                                         property)]
          (is (= :incompatible (:status replayed)))
          (is (= [{:path path :source :bundle
                   :expected (get-in provenance path) :actual changed}]
                 (:mismatches replayed))))
        (is (empty? @calls)))
      (is (= :hegel.replay-bundle/invalid-bundle
             (:type (ex-data (caught #(h/replay-bundle!
                                      provenance (assoc step-bundle :seed "01") property))))))
      (let [wrong (assoc-in provenance [:runtime :host]
                            (if (= :bb (host/runtime)) :jvm :bb))]
        (is (= :incompatible
               (:status (h/replay-bundle! wrong (assoc step-bundle :provenance wrong) property))))
        (is (= [{:path [:runtime :host] :source :runtime
                 :expected (get-in wrong [:runtime :host]) :actual (host/runtime)}]
               (:mismatches (h/replay-bundle! wrong (assoc step-bundle :provenance wrong) property)))))
      (let [wrong (assoc provenance :libhegel-version "0.33.3")]
        (is (= [{:path [:libhegel-version] :source :native-binding
                 :expected "0.33.3" :actual version/libhegel-version}]
               (:mismatches (h/replay-bundle! wrong (assoc step-bundle :provenance wrong) property)))))
      (is (empty? @calls)))))

(deftest wrong-origin-pass-and-assumption-are-not-reproduction
  (doseq [property [(fn [_] nil)
                    (fn [_] (throw (ex-info "different" {:hegel/origin "wrong-origin"})))
                    (fn [_] (h/assume! false))]]
    (let [replayed (h/replay-bundle! provenance step-bundle property)]
      (is (= :not-reproduced (:status replayed)))
      (is (false? (:reproduced? replayed)))
      (is (true? (:flaky? replayed))))))

(deftest replay-cleans-up-after-native-and-harness-errors
  (hffi/ensure-compatible-version!)
  (let [ctx-free hffi/context-free!
        settings-free hffi/settings-free!
        case-free hffi/test-case-free!
        events (atom [])
        marker (ex-info "inconclusive" {:hegel/inconclusive? true})]
    (with-redefs [hffi/context-free! (fn [ctx] (swap! events conj :context) (ctx-free ctx))
                  hffi/settings-free! (fn [ctx settings]
                                        (swap! events conj :settings) (settings-free ctx settings))
                  hffi/test-case-free! (fn [ctx tc] (swap! events conj :case) (case-free ctx tc))]
      (let [error (caught #(h/replay-bundle!
                           provenance
                           (assoc-in step-bundle [:failures 0 :reproduction-blob] "not-base64!")
                           (fn [_] (swap! events conj :unexpected-body))))]
        (is (= :hegel.ffi/error (:type (ex-data error))))
        (is (= [:settings :context] @events)))
      (reset! events [])
      (is (identical? marker
                      (caught #(h/replay-bundle! provenance step-bundle
                                                 (fn [tc]
                                                   (h/register-native-cleanup!
                                                    tc (fn [] (swap! events conj :registered)))
                                                   (throw marker))))))
      (is (= [:registered :case :settings :context] @events)))))

(defn -main [& _]
  (let [result (clojure.test/run-tests 'hegel.replay-bundle-native-test)]
    (System/exit (if (zero? (+ (:fail result) (:error result))) 0 1))))
