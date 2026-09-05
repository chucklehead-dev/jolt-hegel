(ns hegel.suites.ffi
  "FFI contract scenarios, loaded only when selected."
  (:require [clojure.string :as str]
            [hegel.abi :as abi]
            [hegel.ffi :as hffi]
            [hegel.ffi.backend :as ffi-backend]
            [hegel.host :as host]
            [hegel.test-support :as support]))

(defn ffi-nullable-string-results [context]
  (let [native-result (atom ::native-result)
        calls (atom [])
        frees (atom [])]
    (with-redefs [ffi-backend/with-native-scope (fn [call] (call))
                  ffi-backend/sizeof (constantly 8)
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
        expected-route (case (host/runtime)
                         :bb #{:bb/trampoline :bb/libffi :bb/ffm}
                         :jvm #{:jvm/ffm}
                         nil)]
    (support/check! context "selected backend covers every canonical ABI function"
           (= {:supported 77 :unsupported 0 :total 77}
              (:summary report)))
    (when expected-route
      (support/check! context "babashka.ffi bindings report exact host call routes"
             (every? expected-route
                     (map :route (vals (:functions report)))))))
  (when (contains? #{:bb :jvm} (host/runtime))
    (let [layout (ffi-backend/layout :hegel/datetime)
          value {:date {:year 2024 :month 2 :day 29}
                 :time {:hour 1 :minute 2 :second 3 :microsecond 4}}
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
                              [[:time :microsecond] 4]]]
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
