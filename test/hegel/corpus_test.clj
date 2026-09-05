(ns hegel.corpus-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is]]
            [hegel.corpus :as corpus]
            [hegel.corpus.digest :as digest]))

(def provenance
  {:hegel-sha "0123456789abcdef0123456789abcdef01234567"
   :libhegel-version "0.36.3"
   :runtime {:host :bb :version "1.13.220" :os "linux" :arch "x86_64"}
   :property-id "example/corpus"
   :generator-revision "generator-v1"
   :model-revision nil
   :seam-revision "seam-v1"})

(def payload
  {:provenance provenance
   :seed "18446744073709551615"
   :count 2
   :valid-case-policy :exact-valid-count
   :values [{:ordinary [1 :value true]} "second"]})

(defn- error-data [thunk]
  (try (thunk) nil (catch clojure.lang.ExceptionInfo error (ex-data error))))

(defn- reason-of [thunk] (:reason (error-data thunk)))

(defn- expected-for [envelope]
  {:sha256 (:sha256 envelope)
   :provenance provenance
   :count 2
   :valid-case-policy :exact-valid-count})

(deftest seals-encodes-decodes-and-consumes-portable-values
  (let [envelope (corpus/seal payload)
        expected (expected-for envelope)]
    (is (= payload (corpus/validate-payload! payload)))
    (is (= provenance (corpus/validate-provenance! provenance)))
    (is (= envelope (corpus/decode (corpus/encode envelope))))
    (is (= payload (corpus/consume! expected envelope)))
    (is (= (:sha256 envelope) (digest/sha256 (:payload envelope))))))

(deftest closed-schema-bounds-and-reader-controls-fail-with-sanitized-errors
  (let [envelope (corpus/seal payload)
        raw-key (apply str (repeat 128 "private-key"))]
    (doseq [thunk [#(corpus/validate-payload! (assoc payload :extra true))
                   #(corpus/validate-payload! (assoc payload :seed "01"))
                   #(corpus/validate-payload! (assoc payload :count 257))
                   #(corpus/validate-payload! (assoc payload :values [1]))
                   #(corpus/validate-provenance! (assoc provenance :seam-revision ""))
                   #(corpus/decode "#{:forbidden}")
                   #(corpus/encode (assoc envelope raw-key true))]]
      (let [data (error-data thunk)]
        (is (= :hegel.corpus/invalid-corpus (:type data)))
        (is (true? (:hegel/usage-error? data)))
        (is (keyword? (:reason data)))
        (is (not (contains? data :value)))))
    (let [data (error-data #(corpus/encode (assoc envelope raw-key true)))]
      (is (= [:unknown-key] (:path data)))
      (is (not-any? #(= raw-key %) (:path data))))
    (is (= :max-string-chars
           (reason-of #(corpus/validate-payload!
                        (assoc payload :values [(apply str (repeat 8193 "x"))
                                                "second"])))))
    (is (= :max-string-chars
           (reason-of #(corpus/encode
                        (assoc envelope :payload
                               (apply str (repeat 262145 "x")))))))))

(deftest independent-pin-prevents-self-hash-and-field-mutations
  (let [envelope (corpus/seal payload)
        expected (expected-for envelope)
        changed-payload (assoc payload :values ["changed" "second"])
        changed-text (pr-str changed-payload)
        self-rehashed (assoc envelope :payload changed-text
                              :sha256 (digest/sha256 changed-text))]
    (is (= :payload-digest-mismatch
           (reason-of #(corpus/consume! expected (assoc envelope :sha256 (apply str (repeat 64 "0")))))))
    (is (= :expected-digest-mismatch
           (reason-of #(corpus/consume! expected self-rehashed))))
    (is (= :expected-provenance-mismatch
           (reason-of #(corpus/consume! (assoc expected :provenance
                                                (assoc provenance :seam-revision "stale"))
                                       envelope))))
    (is (= :expected-count-mismatch
           (reason-of #(corpus/consume! (assoc expected :count 1) envelope))))))

(deftest expected-pin-is-validated-before-artifact-text-or-digest-work
  (let [envelope (corpus/seal payload)
        invalid-expected (dissoc (expected-for envelope) :sha256)
        hostile-envelope (assoc envelope :payload [:not-text])]
    ;; The artifact contains an invalid payload.  Pin validation is deliberately
    ;; first, so the caller receives the pin schema error without hashing or
    ;; parsing the artifact payload.
    (is (= {:type :hegel.corpus/invalid-corpus
            :hegel/usage-error? true
            :path [:sha256]
            :reason :required-key}
           (error-data #(corpus/consume! invalid-expected hostile-envelope))))))

(deftest escaped-surrogates-are-rejected-after-inner-edn-decoding
  ;; The outer EDN text is valid ASCII.  The escaped inner value becomes an
  ;; unpaired UTF-16 surrogate only after parsing the payload.
  (let [envelope (corpus/seal payload)
        bad-text (str "{:provenance " (pr-str provenance)
                      " :seed \"0\" :count 1"
                      " :valid-case-policy :exact-valid-count"
                      " :values [\"\\uD800\"]}")
        bad-envelope (assoc envelope :payload bad-text
                              :sha256 (digest/sha256 bad-text))
        expected (assoc (expected-for envelope) :sha256 (:sha256 bad-envelope))]
    ;; Codepoint hosts can reject the escape in their EDN reader; UTF-16 hosts
    ;; need the explicit post-read guard. Neither route may release the value.
    (is (contains? #{:invalid-unicode :invalid-edn}
                   (reason-of #(corpus/consume! expected bad-envelope))))))

(deftest fixed-utf8-artifact-is-portable-with-an-independent-pin
  ;; Exact text and an independently computed Node crypto digest. Do not seal
  ;; or derive the expected hash from the artifact in this consumer control.
  (let [text (str "{:provenance {:hegel-sha \"0123456789abcdef0123456789abcdef01234567\""
                  " :libhegel-version \"0.36.3\""
                  " :runtime {:host :bb :version \"1.13.220\" :os \"linux\" :arch \"x86_64\"}"
                  " :property-id \"example/corpus\" :generator-revision \"generator-v1\""
                  " :model-revision nil :seam-revision \"seam-v1\"}"
                  " :seed \"18446744073709551615\" :count 2"
                  " :valid-case-policy :exact-valid-count"
                  " :values [{:ordinary [1 :value true]} \"λ😀\"]}")
        expected {:sha256 "8b7dcca740073b4bdcaf0d57804a0d4b27f8d90f4d919e18e1a3b5d469fb8357"
                  :provenance provenance :count 2
                  :valid-case-policy :exact-valid-count}
        envelope {:format :hegel/materialized-corpus :schema-version 1
                  :sha256 "8b7dcca740073b4bdcaf0d57804a0d4b27f8d90f4d919e18e1a3b5d469fb8357"
                  :payload text}]
    (is (= (assoc payload :values [{:ordinary [1 :value true]} "λ😀"])
           (corpus/consume! expected (corpus/decode (corpus/encode envelope)))))
    ;; Equal EDN data with different payload whitespace is different evidence.
    (is (= :payload-digest-mismatch
           (reason-of #(corpus/consume! expected (update envelope :payload str " ")))))
    (is (= :expected-digest-mismatch
           (reason-of #(corpus/consume! expected
                                       (assoc envelope :payload (str text " ")
                                              :sha256 (digest/sha256 (str text " ")))))))))

(deftest malformed-and-oversized-text-never-bypasses-trust-order
  (let [envelope (corpus/seal payload)
        expected (expected-for envelope)
        hash-calls (atom 0)
        read-calls (atom 0)]
    (with-redefs [digest/sha256 (fn [_] (swap! hash-calls inc) (:sha256 expected))
                  edn/read-string (fn [_] (swap! read-calls inc) [])]
      (is (= :required-key
             (reason-of #(corpus/consume! (dissoc expected :sha256) envelope))))
      (is (= :unsupported-schema-version
             (reason-of #(corpus/consume! expected (assoc envelope :schema-version 2)))))
      (is (= :max-string-chars
             (reason-of #(corpus/consume! expected
                                         (assoc envelope :payload
                                                (apply str (repeat 262145 "x")))))))
      (is (zero? @hash-calls))
      (is (zero? @read-calls)))
    (with-redefs [edn/read-string (fn [_] (swap! read-calls inc) [])]
      (is (= :expected-digest-mismatch
             (reason-of #(corpus/consume!
                          (assoc expected :sha256 (apply str (repeat 64 "0")))
                          envelope))))
      (is (zero? @read-calls)))))

(deftest a-matching-pin-does-not-waive-inner-schema-or-reader-validation
  (doseq [[text reasons]
          [["#custom {}" #{:unsupported-reader-syntax}]
           ["{} {}" #{:one-form-required}]
           ["{:secret \"DO-NOT-ECHO\" :unfinished}" #{:invalid-edn}]
           [(str (apply str (repeat 34 "[")) (apply str (repeat 34 "]")))
            #{:max-depth}]
           [(pr-str (assoc payload :values [1])) #{:count-mismatch}]
           [(pr-str (assoc payload :seed "18446744073709551616"))
            #{:canonical-unsigned64-seed-required}]]]
    (let [hash (digest/sha256 text)
          envelope {:format :hegel/materialized-corpus :schema-version 1
                    :sha256 hash :payload text}
          data (error-data #(corpus/consume! (assoc (expected-for envelope) :sha256 hash)
                                             envelope))]
      (is (= :hegel.corpus/invalid-corpus (:type data)))
      (is (contains? reasons (:reason data)))
      (is (not (.contains (pr-str data) "DO-NOT-ECHO"))))))

(defn -main [& _]
  (let [{:keys [fail error]} (clojure.test/run-tests 'hegel.corpus-test)]
    (when (pos? (+ fail error))
      (throw (ex-info "corpus tests failed" {:fail fail :error error})))))
