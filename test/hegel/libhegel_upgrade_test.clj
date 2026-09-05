(ns hegel.libhegel-upgrade-test
  "Focused libhegel v0.36.3 regression checks. Run only with the verified
  upgrade asset selected by HEGEL_LIBHEGEL_LIBRARY."
  (:require [clojure.test :refer [deftest is testing]]
            [hegel.core :as h]
            [hegel.ffi :as hffi]
            [hegel.generator :as g]
            [hegel.stateful :as hs]))

;; Generated with libhegel v0.36.3 (release tag caafb40), seed 1, and the
;; exact property below.  It is deliberately not a migration claim for the
;; historical v0.32.3 blob in test/fixtures/hegel-0.32.3.
(def ^:private v0363-step-51-blob
  "AXic7cahEQAACMNAYtlfMS0g6jtAI3I/dTV/EMLQ4AKH1ANw")

(defn- step-51-machine []
  {:initial-state {:count 0}
   :rules [(hs/rule :inc #(update % :count inc))]
   :invariants [(hs/invariant :below-fifty-one #(< (:count %) 51))]})

(defn- replay-step-51 [step-count]
  (let [context (hffi/context-new!)]
    (try
      (let [settings (hffi/settings-new! context)]
        (try
          (hffi/settings-set-stateful-step-count! context settings step-count)
          (let [handle (hffi/test-case-from-blob! context settings
                                                  v0363-step-51-blob)
                test-case (h/->TestCase context handle true :quiet)]
            (try
              (binding [h/*test-case* test-case]
                (try
                  {:outcome :completed
                   :value (hs/run! (step-51-machine))}
                  (catch Throwable error
                    {:outcome :threw
                     :error error})))
              (finally
                (hffi/test-case-free! context handle))))
          (finally
            (hffi/settings-free! context settings))))
      (finally
        (hffi/context-free! context)))))

(defn- property-failure-at-51? [error]
  (let [data (ex-data error)]
    (and (= ::hs/invariant-failed (:type data))
         (= "hegel.stateful/invariant:below-fifty-one" (:hegel/origin data))
         (= 51 (count (::hs/trace data))))))

(defn- caught-error [thunk]
  (try
    (thunk)
    nil
    (catch Throwable error error)))

(defn- in-stateful-round-span [test-case round-fn]
  ((ns-resolve 'hegel.stateful 'in-stateful-round-span) test-case round-fn))

(deftest stateful-round-spans-balance-and-preserve-the-original-error
  (let [events (atom [])
        test-case {:context :context :handle :handle}
        marker (ex-info "property failed" {:marker :property})
        close-error (ex-info "closing failed" {:marker :close})
        start-error (ex-info "starting failed" {:marker :start})]
    (with-redefs [hffi/start-span!
                  (fn [_ _ label] (swap! events conj [:start label]))
                  hffi/stop-span!
                  (fn
                    ([_ _] (swap! events conj [:stop false]))
                    ([_ _ discard?] (swap! events conj [:stop discard?])))]
      (is (= {:discard? true}
             (in-stateful-round-span test-case #(hash-map :discard? true))))
      (is (= [[:start hffi/label-stateful-rule] [:stop true]] @events))
      (reset! events [])
      (is (identical? marker
                      (caught-error #(in-stateful-round-span
                                      test-case (fn [] (throw marker))))))
      (is (= [[:start hffi/label-stateful-rule] [:stop false]] @events)))
    (reset! events [])
    (with-redefs [hffi/start-span!
                  (fn [_ _ label] (swap! events conj [:start label]))
                  hffi/stop-span!
                  (fn [_ _ _]
                    (swap! events conj [:stop false])
                    (throw close-error))]
      (is (identical? marker
                      (caught-error #(in-stateful-round-span
                                      test-case (fn [] (throw marker))))))
      (is (= [[:start hffi/label-stateful-rule] [:stop false]] @events)))
    (reset! events [])
    (with-redefs [hffi/start-span!
                  (fn [_ _ label] (swap! events conj [:start label]))
                  hffi/stop-span!
                  (fn [_ _ _]
                    (swap! events conj [:stop false])
                    (throw close-error))]
      (is (identical? close-error
                      (caught-error #(in-stateful-round-span
                                      test-case (constantly {:discard? false})))))
      (is (= [[:start hffi/label-stateful-rule] [:stop false]] @events)))
    (let [body-called? (atom false)]
      (reset! events [])
      (with-redefs [hffi/start-span! (fn [& _] (throw start-error))
                    hffi/stop-span! (fn [& _] (swap! events conj :stop))]
        (is (identical? start-error
                        (caught-error #(in-stateful-round-span
                                        test-case
                                        (fn []
                                          (reset! body-called? true)
                                          {:discard? false})))))
        (is (false? @body-called?))
        (is (empty? @events))))))

(deftest rejected-stateful-round-discard-does-not-erase-successful-state
  (let [groups (atom [0 0 nil])
        rules (atom [0 1 nil 1 nil])
        observations (atom [])
        rejected (atom 0)
        stopped (atom [])
        freed (atom 0)
        test-case {:context :context :handle :handle :verbosity :quiet}]
    (with-redefs [hffi/new-state-machine! (fn [& _] :machine)
                  hffi/state-machine-next-group!
                  (fn [& _] (let [group (first @groups)]
                              (swap! groups next)
                              group))
                  hffi/state-machine-next-rule!
                  (fn [& _] (let [rule (first @rules)]
                              (swap! rules next)
                              rule))
                  hffi/state-machine-rule-rejected!
                  (fn [& _] (swap! rejected inc))
                  hffi/start-span! (fn [& _] nil)
                  hffi/stop-span! (fn [_ _ discard?]
                                    (swap! stopped conj discard?))
                  hffi/state-machine-free! (fn [& _] (swap! freed inc))]
      (binding [h/*test-case* test-case]
        (is (= 2
               (hs/run!
                {:initial-state 0
                 :rules [(hs/rule :reject {:precondition (constantly false)} identity)
                         (hs/rule :success inc)]
                 :invariants [(hs/invariant :observe
                                            (fn [state]
                                              (swap! observations conj state)
                                              true))]}))))
      (is (= [0 1 2] @observations))
      (is (= 1 @rejected))
      ;; Discard marks the rejected round as a candidate shrink region. It
      ;; does not roll back the successful action in that same round; replay
      ;; remains the engine's authority for accepting any deletion.
      (is (= [true false false] @stopped))
      (is (= 1 @freed)))))

(deftest initial-invariant-failure-opens-no-round-span-and-frees-the-machine
  (let [starts (atom 0)
        frees (atom 0)
        test-case {:context :context :handle :handle :verbosity :quiet}
        error
        (with-redefs [hffi/new-state-machine! (fn [& _] :machine)
                      hffi/start-span! (fn [& _] (swap! starts inc))
                      hffi/state-machine-free! (fn [& _] (swap! frees inc))]
          (binding [h/*test-case* test-case]
            (caught-error
             #(hs/run! {:initial-state 0
                         :rules [(hs/rule :unused identity)]
                         :invariants [(hs/invariant :initially-false
                                                   (constantly false))]}))))]
    (is (= ::hs/invariant-failed (:type (ex-data error))))
    (is (zero? @starts))
    (is (= 1 @frees))))

(deftest v0363-seeded-generation-and-replay-preserve-a-51-step-property-failure
  (let [result (h/run-test! {:test-cases 1
                             :stateful-step-count 52
                             :seed 1
                             :database ""
                             :verbosity :quiet}
                            (fn [_] (hs/run! (step-51-machine))))
        failure (first (:failures result))
        direct-52 (replay-step-51 52)
        direct-50 (replay-step-51 50)]
    (testing "the recorded v0.36.3 seed produces the bounded property failure"
      (is (false? (:passed? result)))
      (is (= :failed (:status result)))
      (is (= v0363-step-51-blob (:reproduction-blob failure)))
      (is (true? (:reproduced? failure)))
      (is (property-failure-at-51? (:exception failure))))
    (testing "direct replay with the recorded budget is the same property failure"
      (is (= :threw (:outcome direct-52)))
      (is (property-failure-at-51? (:error direct-52))))
    (testing "the smaller budget is an ordinary bounded completion, not an error"
      (is (= :completed (:outcome direct-50)))
      (is (= {:count 50} (:value direct-50)))
      (is (not (property-failure-at-51? (:error direct-50)))))))

(deftest v0363-stateful-step-count-has-a-seeded-positive-variation-control
  (doseq [step-count (range 1 8)]
    (let [observations (atom [])
          result (h/run-test! {:test-cases 48
                               :stateful-step-count step-count
                               :seed (+ 20260904 step-count)
                               :database ""
                               :verbosity :quiet}
                              (fn [_]
                                (let [checks (atom 0)
                                      state (hs/run!
                                             {:initial-state []
                                              :rules [(hs/rule :step
                                                               #(conj % :step))]
                                              :invariants
                                              [(hs/invariant
                                                :observe-every-step
                                                (fn [_]
                                                  (swap! checks inc)
                                                  true))]})]
                                  (swap! observations conj
                                         {:steps (count state)
                                          :checks @checks}))))
          observed @observations
          lengths (mapv :steps observed)]
      (testing (str "step count " step-count)
        (is (:passed? result))
        (is (seq observed))
        (is (every? #(<= 1 % step-count) lengths))
        (is (some #(= step-count %) lengths))
        (is (every? #(= (inc (:steps %)) (:checks %)) observed))
        (when (> step-count 1)
          (is (some #(< % step-count) lengths)))))))

(deftest v0363-one-valid-case-retains-a-shrunk-drawn-property-failure
  (let [first-value (atom nil)
        result (h/run-test! {:test-cases 1
                             ;; This seed is pinned after the focused probe to
                             ;; begin above the minimum and then shrink to 5.
                             :seed 17
                             :database ""
                             :verbosity :quiet}
                            (fn [_]
                              (let [value (h/draw! (g/integer 5 100))]
                                (swap! first-value #(or % value))
                                (throw
                                 (ex-info "one-valid-case control"
                                          {:hegel/origin
                                           "hegel.libhegel-upgrade/one-valid-case"
                                           :value value})))))
        failure (first (:failures result))
        final-value (:value (ex-data (:exception failure)))]
    (is (false? (:passed? result)))
    (is (= :failed (:status result)))
    (is (= 1 (:n-failures result)))
    (is (= "hegel.libhegel-upgrade/one-valid-case" (:origin failure)))
    (is (not= 5 @first-value))
    (is (= 5 final-value))
    (is (true? (:reproduced? failure)))
    (is (= :interesting (:status failure)))))

(deftest v0363-raw-aggregate-time-and-datetime-preserve-nanoseconds
  (let [time-minimum {:hour 0 :minute 0 :second 0 :nanosecond 0}
        time-fixed {:hour 14 :minute 30 :second 15 :nanosecond 123456789}
        time-maximum {:hour 23 :minute 59 :second 59 :nanosecond 999999999}
        datetime-fixed {:date {:year 2024 :month 2 :day 29}
                        :time time-fixed}
        seen (atom [])
        result (h/run-test! {:test-cases 3 :seed 20260905 :database ""
                             :verbosity :quiet}
                            (fn [test-case]
                              ;; Call the aggregate FFI directly: public
                              ;; generators intentionally project to
                              ;; microseconds after this boundary.
                              (swap! seen conj
                                     {:minimum
                                      (hffi/generate-time!
                                       (:context test-case) (:handle test-case)
                                       time-minimum time-minimum)
                                      :fixed
                                      (hffi/generate-time!
                                       (:context test-case) (:handle test-case)
                                       time-fixed time-fixed)
                                      :maximum
                                      (hffi/generate-time!
                                       (:context test-case) (:handle test-case)
                                       time-maximum time-maximum)
                                      :datetime
                                      (hffi/generate-datetime!
                                       (:context test-case) (:handle test-case)
                                       datetime-fixed datetime-fixed)})))]
    (is (:passed? result))
    (is (seq @seen))
    (is (every? #(= time-minimum (:minimum %)) @seen))
    (is (every? #(= time-fixed (:fixed %)) @seen))
    (is (every? #(= time-maximum (:maximum %)) @seen))
    (is (every? #(= datetime-fixed (:datetime %)) @seen))))

(defn- upgrade-tree-generator []
  (g/recursive
   {:max-depth 7 :max-leaves 64}
   (g/just [:leaf])
   (fn [subtree]
     (g/fmap (fn [[left right]] [:branch left right])
             (g/tuple subtree subtree)))))

(defn- upgrade-tree-leaves [tree]
  (if (= :leaf (first tree))
    1
    (+ (upgrade-tree-leaves (second tree))
       (upgrade-tree-leaves (nth tree 2)))))

(deftest v0363-recursion-spans-a-meaningful-fraction-of-its-leaf-budget
  (let [leaf-counts (atom [])
        result (h/run-test! {:test-cases 160 :seed 20260906 :database ""
                             :verbosity :quiet}
                            (fn [_]
                              (swap! leaf-counts conj
                                     (upgrade-tree-leaves
                                      (h/draw! (upgrade-tree-generator))))))
        counts @leaf-counts]
    (is (:passed? result))
    (is (= 160 (count counts)))
    (is (every? #(<= 1 % 64) counts))
    ;; The 0.33.3 native regression collapsed a 64-leaf budget to a handful
    ;; of leaves.  A fixed seed reaching the upper half is a breadth control,
    ;; rather than merely proving that one nested branch is possible.
    (is (some #(>= % 32) counts))
    (is (some #(<= % 2) counts))))

(defn- draw-heavy-step [tag transition]
  (hs/rule
   tag
   (fn [state]
     ;; C 0.36.2 specifically calls out stateful steps with more than eight
     ;; choices. This collection span encloses ten integer choices; after the
     ;; frontend records the outer stateful-round span, it is a regression
     ;; target for native whole-region deletion.
     (h/draw! (g/vector {:size 10} (g/integer 0 100)))
     (transition state))))

(deftest v0363-stateful-shrinking-deletes-a-redundant-draw-heavy-step-region
  (let [discovered-traces (atom [])
        result (h/run-test!
                {:test-cases 240 :seed 20260907 :database "" :verbosity :quiet}
                (fn [_]
                  (try
                    (hs/run!
                     {:initial-state {:payloads 0 :noise 0}
                      :rules [(draw-heavy-step :noise identity)
                              (draw-heavy-step :payload
                                                #(update % :payloads inc))
                              (hs/rule
                               :check
                               {:precondition #(<= 2 (:payloads %))}
                               (fn [_]
                                 (throw
                                  (ex-info "two payloads observed"
                                           {:hegel/origin
                                            "hegel.libhegel-upgrade/draw-heavy-region"}))))]})
                    (catch Throwable error
                      (swap! discovered-traces conj (::hs/trace (ex-data error)))
                      (throw error)))))
        failure (first (:failures result))
        final-trace (::hs/trace (ex-data (:exception failure)))]
    (is (false? (:passed? result)))
    (is (= "hegel.libhegel-upgrade/draw-heavy-region" (:origin failure)))
    (is (= [:payload :payload :check] final-trace))
    (is (true? (:reproduced? failure)))
    ;; Pin a discovery with a redundant ten-draw :noise region, then require
    ;; the final replay to omit it. This verifies whole-region deletion only
    ;; when the recorded native spans make that deletion replay-reachable.
    (is (some #(and (some #{:noise} %)
                    (> (count %) (count final-trace)))
              @discovered-traces))))

(defn -main [& _]
  (let [{:keys [fail error]} (clojure.test/run-tests 'hegel.libhegel-upgrade-test)]
    (when (pos? (+ fail error))
      (throw (ex-info "libhegel upgrade regression failed" {:fail fail :error error})))))
