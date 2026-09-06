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

(defn upstream-babashka-ffi-adapter [context]
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
                   (let [live-before (hffi/printer-is-live! ctx printer)]
                     (let [first-value (hffi/printer-value! ctx printer)
                           live-after (hffi/printer-is-live! ctx printer)
                           second-value (hffi/printer-value! ctx printer)]
                       (and live-before
                            (not live-after)
                            (= "open keptclose" first-value)
                            (= first-value second-value))))))
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
