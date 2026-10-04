(ns hegel.latest-native-test
  "Focused, real-native libhegel 0.44.1 migration and negative controls."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]
            [hegel.abi :as abi]
            [hegel.clojure-test :as hc]
            [hegel.core :as h]
            [hegel.ffi :as f]
            [hegel.ffi.backend :as backend]
            [hegel.generator :as g]
            [hegel.host :as host]
            [hegel.internal.observations :as observations]
            [hegel.label :as label]
            [hegel.replay-bundle :as bundle]
            [hegel.stateful :as hs]
            [hegel.version :as version]))

(def opts {:profile "base" :test-cases 12 :seed 1 :database "" :verbosity :quiet})

(defn caught [thunk]
  (try (thunk) nil (catch Throwable error error)))

(deftest complete-abi-and-uint64-label-agreement
  (is (= 128 (count (abi/functions))))
  (is (true? (f/ensure-compatible-version!)))
  (is (= "0.44.1" (f/version)))
  (let [ctx (f/context-new!)]
    (try
      (doseq [name ["" "jolt-hegel.list" "λ😀" "a\nb"]]
        (is (= (label/from-name name) (f/label-from-name! ctx name))))
      (doseq [parts [[] [0] [18446744073709551615N] [1 2] [2 1]]]
        (is (= (label/combine parts) (f/label-combine! ctx parts))))
      (is (not= (label/combine [1 2]) (label/combine [2 1])))
      (finally (f/context-free! ctx)))))

(deftest uint64-array-offsets-preserve-neighbors
  (backend/with-native-scope
   (fn []
     (let [ptr (backend/alloc 24)]
       (try
         (is (= 8 (backend/sizeof :uint64)))
         (doseq [[offset value] [[0 17] [8 18446744073709551615N] [16 23]]]
           (backend/write-value ptr :uint64 offset value))
         (is (= [17 18446744073709551615N 23]
                (mapv #(backend/read-value ptr :uint64 %) [0 8 16])))
         (backend/write-value ptr :uint64 8 9223372036854775808N)
         (is (= [17 9223372036854775808N 23]
                (mapv #(backend/read-value ptr :uint64 %) [0 8 16])))
         (finally (backend/free ptr)))))))

(deftest clone-stop-is-draw-control-not-a-harness-error
  (let [reads (atom 0)
        original backend/read-value
        error (with-redefs [f/c-test-case-clone (fn [& _] -1)
                            backend/read-value (fn [& args]
                                                 (swap! reads inc) (apply original args))]
                (caught #(f/test-case-clone! :context :case)))]
    (is (f/stop-test? error))
    (is (not (f/error? error)))
    (is (zero? @reads))))

(deftest native-blocks-indent-and-attribute-output-with-owned-cleanup
  (let [documents (atom [])
        result
        (h/run-test!
         (assoc opts :test-cases 1)
         (fn [{:keys [context handle]}]
           (let [printer (f/test-case-printer! context handle nil)]
             (try
               (f/note! context handle "heading")
               (let [block (f/test-case-block! context handle 2)]
                 (try
                   (let [error (caught #(f/test-case-set-worker! context block -1))]
                     (is (f/error? error))
                     (is (= :test-case-set-worker (:operation (ex-data error)))))
                   (f/test-case-set-worker! context block 2)
                   (f/note! context block "child")
                   (is (= 7 (f/generate-integer! context block 7 7)))
                   (finally (f/test-case-free! context block))))
               (f/note! context handle "footer")
               (f/printer-resolve! context printer)
               (swap! documents conj (f/printer-value! context printer))
               (finally (f/printer-free! context printer))))))]
    (is (:passed? result))
    (is (= 1 (count @documents)))
    (is (re-find #"(?m)^heading$" (first @documents)))
    (is (re-find #"(?m)^\[worker 2 \+[^\]]+ms\]   child$" (first @documents)))
    (is (re-find #"(?m)^footer$" (first @documents)))))

(deftest composed-labels-are-stable-component-sensitive-and-ordered
  (let [integers (g/vector (g/integer))
        booleans (g/vector (g/boolean))]
    (is (= (label/combine [f/label-list (g/generator-label (g/integer))])
           (g/generator-label integers)))
    (is (= (g/generator-label integers) (g/generator-label (g/vector (g/integer)))))
    (is (not= (g/generator-label integers) (g/generator-label booleans)))
    (is (not= (g/generator-label (g/tuple (g/integer) (g/boolean)))
              (g/generator-label (g/tuple (g/boolean) (g/integer)))))
    (is (= (label/from-name "upgrade.custom")
           (g/generator-label (g/composite-fn "upgrade.custom" (constantly nil)))))
    (doseq [bad [nil -1 18446744073709551616N "bad\u0000name"]]
      (is (:hegel/usage-error?
           (ex-data (caught #(g/composite-fn bad (constantly nil)))))))))

(deftest native-profiles-and-explicit-overrides
  (h/register-profile! "jolt_hegel_upgrade" {:profile "base" :test-cases 4
                                           :database "" :verbosity :quiet
                                           :seed 18446744073709551615N})
  (let [resolved (h/resolved-options {:profile "jolt_hegel_upgrade"})
        run (h/run-test! {:profile "jolt_hegel_upgrade"} (fn [_] (h/draw! (g/boolean))))]
    (is (= 4 (:test-cases resolved)))
    (is (= 18446744073709551615N (:seed resolved)))
    (is (= "18446744073709551615" (:seed run))))
  (is (= 7 (:test-cases (h/resolved-options {:profile "jolt_hegel_upgrade" :test-cases 7}))))
  (try
    (h/set-default-profile! "jolt_hegel_upgrade")
    (is (= 4 (:test-cases (h/resolved-options))))
    (is (= 7 (:test-cases (h/resolved-options {:test-cases 7}))))
    (finally (h/set-default-profile! nil)))
  (is (= {:nondeterminism-strictness :error :unbounded-choices? true :print-blob? false}
         (select-keys (h/resolved-options {:profile "base" :nondeterminism-strictness :error
                                          :unbounded-choices? true :print-blob? false})
                      [:nondeterminism-strictness :unbounded-choices? :print-blob?])))
  (is (f/error? (caught #(h/resolved-options {:profile "jolt_hegel_missing"}))))
  (doseq [bad ["bad\u0000name" "" nil 12]]
    (is (:hegel/usage-error? (ex-data (caught #(h/register-profile! bad {}))))))
  (doseq [bad ["bad\u0000name" "" 12]]
    (is (:hegel/usage-error? (ex-data (caught #(h/set-default-profile! bad))))))
  (doseq [invalid [{:profile "bad\u0000name"} {:nondeterminism-strictness :wat}
                   {:unbounded-choices? :yes} {:print-blob? 1} {:backend :auto}
                   {:test-location {:file "x" :line -1 :class-name "m" :function "f"}}]]
    (is (:hegel/usage-error? (ex-data (caught #(h/run-test! invalid (fn [_]))))))))

(deftest weighted-machines-and-per-machine-budgets
  (let [states (atom [])
        run (h/run-test! (assoc opts :stateful-step-count 2)
                         (fn [_]
                           (swap! states conj
                                  (hs/run! {:initial-state 0 :step-count 4
                                            :rules [(hs/rule :inc {:weight 2.5} inc)]
                                            :invariants [(hs/invariant :bounded #(<= % 4))]}))))]
    (is (:passed? run))
    (is (every? #(<= 1 % 4) @states))
    (is (some #(= 4 %) @states)))
  (doseq [weight [0 -1 ##Inf ##NaN nil "2"]]
    (is (:hegel/usage-error? (ex-data (caught #(hs/rule :invalid {:weight weight} inc))))))
  (is (:hegel/usage-error?
       (ex-data (caught #(h/run-test! opts
                          (fn [_] (hs/run! {:initial-state 0 :step-count 0
                                            :rules [(hs/rule :inc inc)]}))))))))

(deftest replay-records-the-resolved-profile-not-development-assumptions
  (h/register-profile! "jolt_hegel_replay_profile"
                       {:profile "base" :database "" :test-cases 1
                        :derandomize? true :suppress-health-checks [:too-slow]})
  (let [result (h/run-test! {:profile "jolt_hegel_replay_profile"
                            :seed 1 :verbosity :quiet}
                           (fn [_] (throw (ex-info "profile failure"
                                                   {:hegel/origin "upgrade/profile"}))))]
    (is (false? (:passed? result)))
    (is (false? (:flaky? result)))
    (is (= {:derandomize? true :suppress-health-checks [:too-slow]}
           (select-keys (:replay-options result)
                        [:derandomize? :suppress-health-checks])))))

(defn threshold-case [_]
  (let [n (h/draw! (g/integer 0 100) :n)]
    (when (>= n 7)
      (throw (ex-info "threshold" {:hegel/origin "upgrade/threshold" :n n})))))

(deftest engine-captures-fresh-minimal-failure-without-frontend-replay
  (let [captures (atom 0)
        original f/test-case-should-capture?
        result (with-redefs [f/test-case-from-blob! (fn [& _] (throw (ex-info "extra replay" {})))
                            f/test-case-should-capture? (fn [& args]
                                                         (let [value (apply original args)]
                                                           (when value (swap! captures inc)) value))]
                 (h/run-test! opts threshold-case))
        failure (first (:failures result))]
    (is (false? (:passed? result)))
    (is (false? (:flaky? result)))
    (is (pos? @captures))
    (is (= 7 (:n (ex-data (:exception failure)))))
    (is (= "upgrade/threshold" (:replay-origin failure)))
    (is (nil? (:caveat failure)))
    (is (string? (:reproduction-blob failure)))))

(deftest clojure-test-publishes-only-the-selected-capture
  (let [events (atom [])
        result (binding [clojure.test/report #(swap! events conj %)]
                 (hc/with opts [n (g/integer 0 100)] (is (< n 7))))]
    (is (false? (:passed? result)))
    (is (= 1 (count @events)))
    (is (= :fail (:type (first @events))))
    (is (= '(not (< 7 7)) (:actual (first @events))))))

(deftest native-strict-nondeterminism-remains-a-failure
  (let [attempt (atom 0)
        result (h/run-test! (assoc opts :nondeterminism-strictness :error)
                            (fn [_]
                              (h/draw! (g/integer 0 1))
                              (when (odd? (swap! attempt inc))
                                (throw (ex-info "flip" {:hegel/origin "upgrade/flip"})))))]
    (is (false? (:passed? result)))
    (is (:flaky? result))
    (is (= :error (:status result)))))

(def provenance
  {:hegel-sha "0123456789abcdef0123456789abcdef01234567"
   :libhegel-version version/libhegel-version
   :runtime {:host (host/runtime) :version "upgrade-control" :os "test" :arch "test"}
   :property-id "upgrade/threshold" :generator-revision "component-label-v1"
   :model-revision nil})

(deftest unconfirmed-failures-keep-caveats-and-cannot-export-stable-bundles
  (doseq [strictness [:quiet :warn]]
    (let [attempt (atom 0)
          result (h/run-test! (assoc opts :nondeterminism-strictness strictness)
                              (fn [_]
                                (h/draw! (g/integer 0 1))
                                (when (= 1 (swap! attempt inc))
                                  (throw (ex-info "once" {:hegel/origin "upgrade/once"})))))
          failure (first (:failures result))]
      (is (= :failed (:status result)))
      (is (:flaky? result))
      (is (= 1 (:n-failures result)))
      (is (str/includes? (:caveat failure) "unconfirmed failure"))
      (is (false? (:reproduced? failure)))
      (is (pos? (count (:observed-failures result))))
      (is (some? (caught #(bundle/from-result provenance result)))))))

(deftest blob-runs-replay-without-generation-or-extra-frontend-replay
  (let [result (h/run-test! opts threshold-case)
        artifact (bundle/from-result provenance result)
        starts (atom 0)
        original f/run-start-blob!
        replay (with-redefs [f/run-start! (fn [& _] (throw (ex-info "generation forbidden" {})))
                             f/test-case-from-blob! (fn [& _] (throw (ex-info "single replay forbidden" {})))
                             f/run-start-blob! (fn [& args] (swap! starts inc) (apply original args))]
                 (h/replay-bundle! provenance artifact threshold-case))]
    (is (= 1 @starts))
    (is (= :reproduced (:status replay)))
    (is (= 7 (-> replay :failures first :exception ex-data :n)))
    (is (false? (:flaky? replay)))
    (is (= :not-reproduced
           (:status (h/replay-bundle! provenance artifact (constantly nil)))))
    (let [calls (atom 0)
          malformed (assoc-in artifact [:failures 0 :reproduction-blob] "not-base64!")
          error (caught #(h/replay-bundle! provenance malformed (fn [_] (swap! calls inc))))]
      (is (f/error? error))
      (is (= :run-start-blob (:operation (ex-data error))))
      (is (zero? @calls)))))

(deftest clojure-test-supplies-location-and-keeps-caveats-visible
  (let [locations (atom [])
        original f/settings-set-test-location!
        events (atom [])
        attempt (atom 0)]
    (binding [clojure.test/report #(swap! events conj %)]
      (with-redefs [f/settings-set-test-location!
                    (fn [ctx settings location]
                      (swap! locations conj location) (original ctx settings location))]
        (hc/with opts [n (g/integer 0 1)]
          (when (= 1 (swap! attempt inc)) (is (= 3 n))))))
    (is (= 1 (count @locations)))
    (is (str/ends-with? (:file (first @locations)) "latest_native_test.clj"))
    (is (pos? (:line (first @locations))))
    (is (= "hegel.latest-native-test" (:class-name (first @locations))))
    (is (= [:fail] (mapv :type @events)))
    (is (str/includes? (:message (first @events)) "unconfirmed failure"))))

(deftest automatic-location-does-not-turn-invalid-options-into-valid-ones
  (doseq [invalid [nil [] 12 :not-options]]
    (is (:hegel/usage-error? (ex-data (caught #(hc/with invalid [] (is true))))))))

(deftest stamped-valid-confirmations-cannot-satisfy-exploration-coverage
  (let [handles (atom [:exploration :capture])
        data (atom {:exploration (observations/empty-summary)
                    :final-replay (observations/empty-summary)})
        drive (ns-resolve 'hegel.core 'drive-run!)
        completed (atom [])]
    (with-redefs [f/next-test-case! (fn [& _] (let [handle (first @handles)]
                                              (swap! handles next) handle))
                  f/test-case-should-capture? (fn [_ handle] (= :capture handle))
                  f/mark-complete! (fn [_ handle & _] (swap! completed conj handle))
                  f/test-case-free! (fn [& _])
                  f/event! (fn [& _])]
      (let [result (drive :context :run :quiet
                          (fn [_] (h/event! (if (h/final?) "capture-only" "explore")))
                          data {} 3)]
        (is (= 2 (:valid-test-cases result)))
        (is (= [:exploration :capture] @completed))
        (is (= 1 (get-in @data [:exploration :cases :valid])))
        (is (nil? (get-in @data [:exploration :events "capture-only"])))))))

(defn -main [& _]
  (let [result (run-tests 'hegel.latest-native-test)]
    (System/exit (if (zero? (+ (:fail result) (:error result))) 0 1))))
