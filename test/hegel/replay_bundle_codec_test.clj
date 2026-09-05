(ns hegel.replay-bundle-codec-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]
            [hegel.replay-bundle :as bundle]
            [hegel.replay-bundle.codec :as codec]))

(def fixture
  {:format :hegel/replay-bundle
   :schema-version 1
   :provenance {:hegel-sha "0123456789abcdef0123456789abcdef01234567"
                :libhegel-version "0.36.3"
                :runtime {:host :jvm :version "1.12.3" :os "Linux" :arch "amd64"}
                :property-id "codec/control" :generator-revision "v1"
                :model-revision nil}
   :seed "18446744073709551615"
   :options {:test-cases 18446744073709551615N}
   :failures [{:origin "codec/failure" :reproduction-blob "opaque"}]})

(defn- encoding-error [text]
  (try (codec/decode text) nil
       (catch clojure.lang.ExceptionInfo e (ex-data e))))

(deftest round-trip-preserves-data-not-printer-state
  (let [value (assoc fixture :trace
                     {:contract-id "codec" :contract-revision "1"
                      :events [{:text "#tag ;comment \"quote\" \\ slash\nline 😀"
                                :values [nil true false -9223372036854775808N
                                         18446744073709551615N 1.25 (float 0.1)]}]})]
    (binding [*print-meta* true *print-length* 1 *print-level* 1]
      (is (= value (codec/decode (codec/encode
                                 (with-meta value {:not-exported 'metadata}))))))
    (is (= value (codec/decode (str "; leading comment\n"
                                  (codec/encode value) " ; trailing comment"))))))

(deftest disallowed-reader-forms-never-reach-the-reader
  (let [calls (atom 0)]
    (with-redefs [edn/read-string (fn [& _] (swap! calls inc) [])]
      (doseq [text ["#=(System/exit 1)" "#inst \"2026-01-01\"" "#custom {}"
                    "#_{} {}" "#{1}" "(1)" "\\newline" "^{} {}"
                    "@thing" "`thing" "~thing" "'thing" "{]" "["
                    (str (apply str (repeat 34 "["))
                         (apply str (repeat 34 "]")))
                    (apply str (repeat (inc (:max-text-chars bundle/limits)) " "))
                    (str "[" (apply str (repeat 8193 "0 ")) "]")]]
        (is (= :hegel.replay-bundle.codec/invalid-encoding
               (:type (encoding-error text))) text))
      (is (zero? @calls)))))

(deftest exactly-one-form-and-sanitized-reader-errors
  (doseq [text ["" "; no form" "{} {}" "nil nil"]]
    (is (= :one-form-required (:reason (encoding-error text)))))
  (doseq [text ["{:secret \"DO-NOT-ECHO\" :unfinished}"
                "{:x 1 :x 2}" "\"bad\\qDO-NOT-ECHO\""]]
    (try
      (codec/decode text)
      (is false "malformed EDN must throw")
      (catch clojure.lang.ExceptionInfo e
        (is (= :invalid-edn (:reason (ex-data e))))
        (is (nil? (ex-cause e)))
        (is (not (.contains (str e) "DO-NOT-ECHO")))))))

(deftest schema-validation-is-not-skipped-by-codec
  (is (= :hegel.replay-bundle/invalid-bundle
         (:type (encoding-error "{}"))))
  (is (= :hegel.replay-bundle/invalid-bundle
         (:type (encoding-error (pr-str (assoc fixture :schema-version 2)))))))

(deftest data-depth-boundary-round-trips
  (let [nested (reduce (fn [v _] [v]) [] (range 28))
        value (assoc fixture :trace
                     {:contract-id "codec" :contract-revision "1"
                      :events [{:value nested}]})]
    (is (= value (bundle/validate! value)))
    (is (= value (codec/decode (codec/encode value))))
    (is (thrown? clojure.lang.ExceptionInfo
                 (codec/encode (assoc-in value [:trace :events 0 :value] [nested]))))))

(deftest unicode-bounds-use-the-same-unit-on-every-host
  (is (= 4 (bundle/text-size "a😀z")))
  (let [at-limit (apply str (repeat 4096 "😀"))
        over-limit (str at-limit "😀")
        value (assoc fixture :trace
                     {:contract-id "codec" :contract-revision "1"
                      :events [{:text at-limit}]})]
    (is (= value (codec/decode (codec/encode value))))
    (let [invalid-value (assoc-in value [:trace :events 0 :text] over-limit)]
      (is (thrown? clojure.lang.ExceptionInfo (codec/encode invalid-value)))
      (is (= :max-string-chars
             (:reason (encoding-error (pr-str invalid-value)))))))
  ;; The length guard must run before the EDN reader even when Jolt's
  ;; codepoint count fits but the portable UTF-16-unit count does not.
  (let [calls (atom 0)]
    (with-redefs [edn/read-string (fn [& _] (swap! calls inc) [])]
      (is (= :max-text-chars
             (:reason (encoding-error (str "\"" (apply str (repeat 131072 "😀")) "\"")))))
      (is (zero? @calls)))))

(deftest encoder-checks-escaped-budget-and-readable-keywords
  (testing "escaped strings can exceed the budget even when raw text fits"
    (let [value (assoc fixture :trace
                       {:contract-id "codec" :contract-revision "1"
                        :events [{:strings (vec (repeat 20
                                                       (apply str (repeat 8192 "\n"))))}]})]
      (is (= value (bundle/validate! value)))
      (try (codec/encode value)
           (is false "escaped representation must be bounded")
           (catch clojure.lang.ExceptionInfo e
             (is (= :max-text-chars (:reason (ex-data e))))))))
  (let [value (assoc fixture :trace
                     {:contract-id "codec" :contract-revision "1"
                      :events [{:value (keyword "bad keyword")}]})]
    (is (thrown? clojure.lang.ExceptionInfo (codec/encode value)))))

(defn -main [& _]
  (let [result (clojure.test/run-tests 'hegel.replay-bundle-codec-test)]
    (System/exit (if (zero? (+ (:fail result) (:error result))) 0 1))))
