(ns hegel.suites.ffi
  "FFI contract scenarios, loaded only when selected."
  (:require [clojure.string :as str]
            [clojure.test :as t]
            [hegel.abi :as abi]
            [hegel.ffi :as hffi]
            [hegel.ffi.backend :as ffi-backend]
            [hegel.host :as host]
            [hegel.test-support :as support]))

(defn signature-policy-contract [context]
  (if (contains? #{:bb :jvm :jolt} (host/runtime))
    (do
      (require 'hegel.signature-policy-test)
      (let [result (t/run-tests 'hegel.signature-policy-test)]
        (support/check! context "canonical ABI signature policy"
                        (zero? (+ (:fail result) (:error result))))))
    (support/check! context
                    "signature policy remains unqualified on experimental host"
                    true)))

(defn ffi-nullable-string-results [context]
  (let [native-result (atom ::native-result)
        calls (atom [])
        frees (atom [])]
    (with-redefs [ffi-backend/with-native-scope (fn [call] (call))
                  ffi-backend/layout-size (constantly 16)
                  ffi-backend/alloc (fn [size]
                                      (swap! calls conj [:alloc size])
                                      ::out)
                  ffi-backend/read-value (fn [pointer type]
                                           (swap! calls conj
                                                  [:read pointer type])
                                           @native-result)
                  ffi-backend/free #(swap! frees conj %)
                  ffi-backend/null? #(= ::null %)
                  ffi-backend/native->string (fn [pointer]
                                               (swap! calls conj
                                                      [:decode pointer])
                                               "decoded")
                  hffi/c-run-result-error (fn [ctx result out]
                                            (swap! calls conj
                                                   [:call ctx result out])
                                            0)]
      (support/check! context "nullable FFI strings decode a non-NULL pointer"
             (= "decoded" (hffi/run-result-error! ::ctx ::result)))
      (reset! native-result ::null)
      (support/check! context "nullable FFI strings preserve a NULL result as nil"
             (nil? (hffi/run-result-error! ::ctx ::result)))
      (support/check! context "nullable FFI string calls retain checked pointer-out cleanup"
             (and (= [::out ::out] @frees)
                  (= 2 (count (filter #(= :call (first %)) @calls)))
                  (= 2 (count (filter #(= :read (first %)) @calls)))
                  (= 1 (count (filter #(= :decode (first %)) @calls))))))))

(defn- test-case-clone-pointer-out-contract [context]
  (let [calls (atom [])
        frees (atom [])]
    (with-redefs [ffi-backend/with-native-scope (fn [call] (call))
                  ffi-backend/sizeof (constantly 8)
                  ffi-backend/alloc (fn [size]
                                      (swap! calls conj [:alloc size])
                                      ::out)
                  ffi-backend/read-value (fn [pointer type]
                                           (swap! calls conj [:read pointer type])
                                           ::cloned)
                  ffi-backend/free (fn [pointer]
                                     (swap! frees conj pointer))
                  hffi/c-test-case-clone (fn [ctx test-case out]
                                           (swap! calls conj [:call ctx test-case out])
                                           0)]
      (support/check! context "test-case-clone! returns the decoded pointer-out value"
             (= ::cloned (hffi/test-case-clone! ::ctx ::test-case)))
      (support/check! context "test-case-clone! threads ctx and the source test case to libhegel"
             (some #{[:call ::ctx ::test-case ::out]} @calls))
      (support/check! context "test-case-clone! frees the pointer-out buffer exactly once"
             (= [::out] @frees)))))

(defn- new-state-machine-with-concurrency-contract [context]
  (let [with-c-string-array-calls (atom [])
        with-int64-array-calls (atom [])
        native-calls (atom [])]
    (with-redefs [hffi/with-c-string-array
                  (fn [values call]
                    (swap! with-c-string-array-calls conj values)
                    (call (keyword (str "rules-ptr-" (count @with-c-string-array-calls)))
                          (count values)))
                  hffi/with-int64-array
                  (fn [values call]
                    (swap! with-int64-array-calls conj (vec values))
                    (call ::rule-groups-ptr))
                  ffi-backend/with-native-scope (fn [call] (call))
                  ffi-backend/sizeof (constantly 8)
                  ffi-backend/alloc (fn [_size] ::out)
                  ffi-backend/free (fn [_pointer] nil)
                  ffi-backend/read-value (fn [_pointer type]
                                           (case type
                                             :pointer ::machine
                                             :int64 3))
                  hffi/c-new-state-machine
                  (fn [& arguments]
                    (swap! native-calls conj arguments)
                    0)]
      (let [result (hffi/new-state-machine-with-concurrency!
                    ::ctx ::test-case ["r1" "r2"] [5 6] ["inv"]
                    2 4)]
        (support/check! context "the configured constructor returns the owned handle and selected concurrency as a map"
               (= {:state-machine ::machine :concurrency 3} result))
        (support/check! context "the configured constructor threads rule names and rule groups through unchanged"
               (and (= [["r1" "r2"] ["inv"]] @with-c-string-array-calls)
                    (= [[5 6]] @with-int64-array-calls)))
        (support/check! context "the configured constructor passes every argument to libhegel in order"
               (= [[::ctx ::test-case :rules-ptr-1 ::rule-groups-ptr 2
                    :rules-ptr-2 1 2 4 ::out ::out]]
                  @native-calls))))
    (let [error (try
                  (hffi/new-state-machine-with-concurrency!
                   ::ctx ::test-case ["r1" "r2"] [5] ["inv"] 2 4)
                  nil
                  (catch Throwable e e))]
      (support/check! context "the configured constructor rejects non-parallel rule groups before native access"
             (and (= ::hffi/invalid-argument (:type (ex-data error)))
                  (= :rule-groups (:argument (ex-data error)))
                  (= 2 (:expected (ex-data error)))
                  (= 1 (:actual (ex-data error))))))))

(defn- new-state-machine-post-creation-cleanup-contract [context]
  (let [reads (atom 0)
        frees (atom [])]
    (with-redefs [hffi/with-c-string-array
                  (fn [values call] (call ::strings (count values)))
                  hffi/with-int64-array
                  (fn [_values call] (call ::groups))
                  ffi-backend/with-native-scope (fn [call] (call))
                  ffi-backend/sizeof (constantly 8)
                  ffi-backend/alloc (constantly ::out)
                  ffi-backend/free (fn [_pointer] nil)
                  ffi-backend/read-value
                  (fn [_pointer type]
                    (if (and (= :pointer type) (= 1 (swap! reads inc)))
                      ::machine
                      (throw (ex-info "mocked concurrency read failure" {}))))
                  hffi/c-new-state-machine (fn [& _arguments] 0)
                  hffi/c-state-machine-free
                  (fn [ctx machine]
                    (swap! frees conj [ctx machine])
                    0)]
      (let [error (try
                    (hffi/new-state-machine-with-concurrency!
                     ::ctx ::test-case ["r1"] [0] [] 1 2)
                    nil
                    (catch Throwable e e))]
        (support/check! context "the configured constructor preserves a post-creation read failure"
               (= "mocked concurrency read failure" (ex-message error)))
        (support/check! context "the configured constructor frees its owned machine after a post-creation failure"
               (= [[::ctx ::machine]] @frees)))))
  (let [allocations (atom 0)
        frees (atom [])]
    (with-redefs [hffi/with-c-string-array
                  (fn [values call] (call ::strings (count values)))
                  hffi/with-int64-array
                  (fn [_values call] (call ::groups))
                  ffi-backend/with-native-scope (fn [call] (call))
                  ffi-backend/sizeof (constantly 8)
                  ffi-backend/alloc
                  (fn [_size]
                    (if (= 1 (swap! allocations inc))
                      ::machine-out
                      (throw (ex-info "mocked second allocation failure" {}))))
                  ffi-backend/free #(swap! frees conj %)]
      (let [error (try
                    (hffi/new-state-machine-with-concurrency!
                     ::ctx ::test-case ["r1"] [0] [] 1 2)
                    nil
                    (catch Throwable e e))]
        (support/check! context "a second out-buffer allocation failure preserves its cause"
               (= "mocked second allocation failure" (ex-message error)))
        (support/check! context "a second out-buffer allocation failure frees the first buffer"
               (= [::machine-out] @frees))))))

(defn- new-state-machine-sequential-wrapper-cleanup-contract [context]
  (let [frees (atom [])]
    (with-redefs [hffi/new-state-machine-with-concurrency!
                  (fn [_ctx _test-case _rule-names _rule-groups _invariant-names
                       _min-concurrency _max-concurrency]
                    {:state-machine ::machine :concurrency 4})
                  hffi/state-machine-free!
                  (fn [ctx state-machine]
                    (swap! frees conj [ctx state-machine])
                    (throw (ex-info "mocked cleanup failure" {})))]
      (let [error (try
                    (hffi/new-state-machine! ::ctx ::test-case ["r1"] ["inv"])
                    nil
                    (catch Throwable e e))]
        (support/check! context "the sequential wrapper preserves unexpected concurrency over cleanup failure"
               (and (some? error)
                    (= ::hffi/invalid-state-machine-concurrency (:type (ex-data error)))
                    (= 4 (:concurrency (ex-data error)))))
        (support/check! context "the sequential wrapper frees the machine before throwing on unexpected concurrency"
               (= [[::ctx ::machine]] @frees))))))

(defn- state-machine-worker-index-contract [context]
  (let [rule-worker-indices (atom [])
        rejected-worker-indices (atom [])]
    (with-redefs [ffi-backend/with-native-scope (fn [call] (call))
                  ffi-backend/sizeof (constantly 8)
                  ffi-backend/alloc (fn [_size] ::out)
                  ffi-backend/free (fn [_pointer] nil)
                  ffi-backend/read-value (fn [_pointer _type] 7)
                  hffi/c-state-machine-next-rule
                  (fn [_ctx _test-case _state-machine worker-index _out]
                    (swap! rule-worker-indices conj worker-index)
                    0)
                  hffi/c-state-machine-rule-rejected
                  (fn [_ctx _test-case _state-machine worker-index]
                    (swap! rejected-worker-indices conj worker-index)
                    0)]
      (hffi/state-machine-next-rule! ::ctx ::test-case ::machine)
      (hffi/state-machine-next-rule! ::ctx ::test-case ::machine 3)
      (hffi/state-machine-rule-rejected! ::ctx ::test-case ::machine)
      (hffi/state-machine-rule-rejected! ::ctx ::test-case ::machine 5)
      (support/check! context "state-machine-next-rule! defaults worker-index to 0 and threads an explicit index"
             (= [0 3] @rule-worker-indices))
      (support/check! context "state-machine-rule-rejected! defaults worker-index to 0 and threads an explicit index"
             (= [0 5] @rejected-worker-indices)))))

(defn- collect-safe-wrapper-selection-contract [context]
  (let [calls (atom [])]
    (with-redefs [ffi-backend/with-native-scope (fn [call] (call))
                  ffi-backend/sizeof (constantly 8)
                  ffi-backend/alloc (fn [_size] ::out)
                  ffi-backend/free (fn [_pointer] nil)
                  ffi-backend/read-value (fn [_pointer _type] 7)
                  hffi/c-pool-add (fn [& _] (swap! calls conj :pool-add/ordinary) 0)
                  hffi/c-pool-add-collect-safe (fn [& _] (swap! calls conj :pool-add/collect-safe) 0)
                  hffi/c-pool-generate (fn [& _] (swap! calls conj :pool-generate/ordinary) 0)
                  hffi/c-pool-generate-collect-safe (fn [& _] (swap! calls conj :pool-generate/collect-safe) 0)
                  hffi/c-state-machine-next-rule (fn [& _] (swap! calls conj :next-rule/ordinary) 0)
                  hffi/c-state-machine-next-rule-collect-safe (fn [& _] (swap! calls conj :next-rule/collect-safe) 0)
                  hffi/c-state-machine-rule-rejected (fn [& _] (swap! calls conj :rejected/ordinary) 0)
                  hffi/c-state-machine-rule-rejected-collect-safe (fn [& _] (swap! calls conj :rejected/collect-safe) 0)]
      ;; Existing public wrappers must retain the ordinary route.
      (hffi/pool-add! ::ctx ::test-case ::pool)
      (hffi/pool-generate! ::ctx ::test-case ::pool false)
      (hffi/state-machine-next-rule! ::ctx ::test-case ::machine 1)
      (hffi/state-machine-rule-rejected! ::ctx ::test-case ::machine 1)
      ;; The future concurrent executor has explicit helpers; it cannot
      ;; accidentally select a route by changing worker-index alone.
      (hffi/pool-add-collect-safe! ::ctx ::test-case ::pool)
      (hffi/pool-generate-collect-safe! ::ctx ::test-case ::pool true)
      (hffi/state-machine-next-rule-collect-safe! ::ctx ::test-case ::machine 1)
      (hffi/state-machine-rule-rejected-collect-safe! ::ctx ::test-case ::machine 1)
      (support/check! context "ordinary and collect-safe wrappers select distinct raw bindings"
                      (= [:pool-add/ordinary
                          :pool-generate/ordinary
                          :next-rule/ordinary
                          :rejected/ordinary
                          :pool-add/collect-safe
                          :pool-generate/collect-safe
                          :next-rule/collect-safe
                          :rejected/collect-safe]
                         @calls)))))

(defn upstream-babashka-ffi-adapter [context]
  (test-case-clone-pointer-out-contract context)
  (new-state-machine-with-concurrency-contract context)
  (new-state-machine-post-creation-cleanup-contract context)
  (new-state-machine-sequential-wrapper-cleanup-contract context)
  (state-machine-worker-index-contract context)
  (collect-safe-wrapper-selection-contract context)
  (let [report (abi/backend-report)
        function-count (count (abi/functions))
        expected-route (case (host/runtime)
                         :bb #{:bb/trampoline :bb/libffi :bb/ffm}
                         :jvm #{:jvm/ffm}
                         nil)]
    (support/check! context "selected backend covers every canonical ABI function"
           (= {:supported function-count :unsupported 0 :total function-count}
              (:summary report)))
    (when expected-route
      (support/check! context "babashka.ffi bindings report exact host call routes"
             (every? expected-route
                     (map :route (vals (:functions report)))))))
  (when (contains? #{:bb :jvm} (host/runtime))
    (let [layout (ffi-backend/layout :hegel/datetime)
          value {:date {:year 2024 :month 2 :day 29}
                 :time {:hour 1 :minute 2 :second 3 :nanosecond 4}}
          escaped (atom nil)]
      (support/check! context "upstream babashka.ffi uses canonical nested struct maps"
             (= value
                (ffi-backend/with-native-scope
                 (fn []
                   (let [pointer (ffi-backend/alloc
                                  (ffi-backend/layout-size layout))]
                     (reset! escaped pointer)
                     (doseq [[path field-value]
                             [[[:date :year] 2024]
                              [[:date :month] 2]
                              [[:date :day] 29]
                              [[:time :hour] 1]
                              [[:time :minute] 2]
                              [[:time :second] 3]
                              [[:time :nanosecond] 4]]]
                       (ffi-backend/write-field pointer layout path field-value))
                     (ffi-backend/by-value pointer layout))))))
      (support/check! context "length-delimited UTF-8 preserves embedded NUL bytes"
             (ffi-backend/with-native-scope
              (fn []
                (let [value "a\u0000😀z"
                      pointer (ffi-backend/string->native value)
                      length (ffi-backend/write-utf8 pointer value)]
                  (and (= 7 length)
                       (= value (ffi-backend/read-utf8 pointer length)))))))
      (support/check! context "arena-scoped pointers cannot be read after lexical release"
             (try
               (ffi-backend/read-value @escaped :uint8)
               false
               (catch Throwable _ true)))))
  (when (contains? #{:bb :jvm} (host/runtime))
    (let [cfn-var (ns-resolve 'babashka.ffi 'cfn)
          make-binding-var (ns-resolve 'hegel.ffi.babashka 'make-binding)
          raw-calls (atom [])
          raw (fn [& values]
                (swap! raw-calls conj values)
                :called)
          function {:symbol "hegel_arity_probe"
                    :args [:c/uint64]
                    :return :c/int32}
          binding (with-redefs-fn
                    {cfn-var (fn [& _] raw)}
                    #(make-binding-var :library function {:types {}}))
          extra-error (try
                        (binding 1 2)
                        nil
                        (catch Throwable error error))]
      (support/check! context "unsigned coercion preserves exact native binding arity"
             (and extra-error
                  (= :hegel.ffi.babashka/wrong-arity
                     (:type (ex-data extra-error)))
                  (= {:symbol "hegel_arity_probe" :expected 1 :actual 2}
                     (select-keys (ex-data extra-error)
                                  [:symbol :expected :actual]))
                  (empty? @raw-calls)))
      (support/check! context "unsigned coercion forwards an exact-arity call"
             (and (= :called (binding 1))
                  (= [[1]] @raw-calls)))))
  (when (= :bb (host/runtime))
    (let [cfn-var (ns-resolve 'babashka.ffi 'cfn)
          preflight-var (ns-resolve 'hegel.ffi.babashka
                                    'ensure-runtime-capable!)
          error (with-redefs-fn
                  {cfn-var (fn [& _]
                             (throw (ex-info "this build has no libffi" {})))}
                  #(try
                     (preflight-var)
                     nil
                     (catch Throwable error error)))]
      (support/check! context "Babashka capability failure precedes libhegel path lookup"
             (and (= :hegel.ffi/unsupported-runtime-build
                     (:type (ex-data error)))
                  (= :libffi (:required-capability (ex-data error)))
                  (str/includes? (ex-message error) "not the -static asset"))))))

(defn- attempt-check! [context description thunk]
  (try
    (support/check! context description (boolean (thunk)))
    (catch Throwable error
      (support/check! context (str description " (raised " (ex-message error) ")") false))))

(defn- printer-value-result-ownership-and-error-precedence [context]
  (let [calls (atom [])]
    (with-redefs [ffi-backend/with-native-scope (fn [call] (call))
                  ffi-backend/sizeof (constantly 8)
                  ffi-backend/alloc (fn [size]
                                      (swap! calls conj [:alloc size])
                                      ::result)
                  ffi-backend/free (fn [pointer]
                                     (swap! calls conj [:free pointer]))
                  ffi-backend/write-value (fn [pointer type offset value]
                                            (swap! calls conj [:write pointer type offset value]))
                  ffi-backend/read-field (fn [pointer _layout path]
                                           (swap! calls conj [:read pointer path])
                                           (if (= path [:data]) ::data 5))
                  ffi-backend/read-utf8 (fn [pointer length]
                                          (swap! calls conj [:decode pointer length])
                                          "printed")
                  hffi/c-printer-value (fn [ctx printer result]
                                        (swap! calls conj [:call ctx printer result])
                                        0)
                  hffi/c-printer-value-result-free (fn [ctx result]
                                                     (swap! calls conj [:release ctx result])
                                                     0)]
      (support/check! context "printer-value decodes the engine-owned buffer before releasing it"
             (= "printed" (hffi/printer-value! ::ctx ::printer)))
      (let [order (mapv first @calls)]
        (support/check! context "printer-value releases the engine-owned buffer exactly once, after decoding"
               (and (< (.indexOf order :decode) (.indexOf order :release))
                    (= 1 (count (filter #{:release} order)))
                    (= 1 (count (filter #{:free} order)))))))
    (reset! calls [])
    (with-redefs [ffi-backend/with-native-scope (fn [call] (call))
                  ffi-backend/layout-size (constantly 16)
                  ffi-backend/alloc (fn [size]
                                      (swap! calls conj [:alloc size])
                                      ::result)
                  ffi-backend/free (fn [pointer]
                                     (swap! calls conj [:free pointer]))
                  ffi-backend/write-value (fn [pointer type offset value]
                                            (swap! calls conj [:write pointer type offset value]))
                  ffi-backend/null? (constantly true)
                  hffi/c-printer-value (fn [ctx printer result]
                                        (swap! calls conj [:call ctx printer result])
                                        9)
                  hffi/c-printer-value-result-free (fn [ctx result]
                                                     (swap! calls conj [:release ctx result])
                                                     (throw (ex-info "mocked cleanup failure" {})))]
      (let [error (try (hffi/printer-value! ::ctx ::printer) nil (catch Throwable e e))]
        (support/check! context "a primary printer-value error is preserved over a cleanup failure"
               (and (some? error)
                    (= ::hffi/error (:type (ex-data error)))
                    (= :printer-value (:operation (ex-data error)))))
        (support/check! context "printer-value releases and frees exactly once even when cleanup fails"
               (let [order (mapv first @calls)]
                 (and (= 1 (count (filter #{:release} order)))
                      (= 1 (count (filter #{:free} order))))))))))

(defn- native-printer-protocol [context]
  (if (contains? #{:bb :jvm :jolt} (host/runtime))
    (let [ctx (hffi/context-new!)]
      (try
        (let [options (hffi/printer-options-new! ctx)]
          (try
            (hffi/printer-options-set-max-width! ctx options 40)
            (let [printer (hffi/printer-new! ctx options)
                  parent (hffi/printer-new! ctx options)]
              (try
                (attempt-check!
                 context
                 "group layout, speculative abort/commit, sealing, and idempotent reads compose on one printer"
                 (fn []
                   (hffi/printer-begin-group! ctx printer 1 "")
                   (hffi/printer-text! ctx printer "open")
                   (hffi/printer-breakable! ctx printer " ")
                   (hffi/printer-begin-speculative! ctx printer)
                   (hffi/printer-text! ctx printer "dropped")
                   (hffi/printer-abort-speculative! ctx printer)
                   (hffi/printer-begin-speculative! ctx printer)
                   (hffi/printer-text! ctx printer "kept")
                   (hffi/printer-commit-speculative! ctx printer)
                   (hffi/printer-end-group! ctx printer "close")
                   (let [live-before (hffi/printer-is-live! ctx printer)
                         first-value (hffi/printer-value! ctx printer)
                         live-after (hffi/printer-is-live! ctx printer)
                         second-value (hffi/printer-value! ctx printer)]
                     (and live-before
                          (not live-after)
                          (= "open keptclose" first-value)
                          (= first-value second-value)))))
                (attempt-check!
                 context
                 "a resolved deferred printer merges its content into the parent's value"
                 (fn []
                   (hffi/printer-text! ctx parent "before-deferred")
                   (let [deferred (hffi/printer-deferred! ctx parent)]
                     (try
                       (hffi/printer-text! ctx deferred "deferred-text")
                       (finally
                         (hffi/printer-free! ctx deferred))))
                   (hffi/printer-resolve! ctx parent)
                   (let [rendered (hffi/printer-value! ctx parent)]
                     (= "before-deferreddeferred-text" rendered))))
                (finally
                  (attempt-check! context "printer and deferred-parent handles free without error"
                    (fn []
                      (hffi/printer-free! ctx printer)
                      (hffi/printer-free! ctx parent)
                      true)))))
            (finally
              (attempt-check! context "printer options handle frees without error"
                (fn []
                  (hffi/printer-options-free! ctx options)
                  true)))))
        (finally
          (hffi/context-free! ctx))))
    (support/check! context "native printer protocol is exercised on a real libhegel host" true)))

(defn printer-protocol-contract
  "Standalone native-printer scenario covering the Stage 0 structured-printing
  FFI surface: group layout, speculative abort/commit, deferred resolve,
  sealing/liveness, idempotent value reads, and handle cleanup. Also
  exercises a mocked ownership/error-precedence control for printer-value's
  result buffer that does not require a loaded libhegel."
  [context]
  (printer-value-result-ownership-and-error-precedence context)
  (native-printer-protocol context))

(defn jolt-ffi-write-order-contract [context]
  (if (= :jolt (host/runtime))
    (let [write-var (ns-resolve 'jolt.ffi 'write)
          write-value-var (ns-resolve 'hegel.ffi.jolt 'write-value)
          calls (atom [])]
      (with-redefs-fn
        {write-var
         (fn [& arguments]
           (swap! calls conj arguments)
           nil)}
        #(write-value-var 17 :int64 24 42))
      (support/check! context "Jolt scalar writes use the 0.8 value-before-offset contract"
             (= [[17 :int64 42 24]] @calls)))
    (support/check! context "Jolt scalar write contract is Jolt-only" true)))
