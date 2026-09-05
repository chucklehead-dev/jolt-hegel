(ns hegel.corpus-digest-test
  (:require [clojure.test :refer [deftest is]]
            [hegel.corpus.digest :as digest]))

(deftest standard-sha256-vectors
  (doseq [[input expected]
          [["" "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"]
           ["abc" "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"]
           ["abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1"]]]
    (is (= expected (digest/sha256 input)))))

(deftest text-validation-before-encoding
  (is (= "a😀b" (digest/validate-text! "a😀b")))
  (doseq [text [nil (apply str (repeat 131073 "😀"))]]
    (let [error (try (digest/sha256 text) nil
                     (catch clojure.lang.ExceptionInfo error error))]
      (is (= :hegel.corpus.digest/invalid-text (:type (ex-data error))))
      (is (not (contains? (ex-data error) :value))))))

(deftest unpaired-surrogates-never-reach-encoding
  ;; Jolt rejects surrogate chars at construction; UTF-16 hosts permit them.
  ;; Check rejection at the earliest representable boundary on each host.
  (doseq [code [55296 56319 56320 57343]]
    (let [constructed (try {:text (str (char code))}
                           (catch IllegalArgumentException _ {:rejected? true}))]
      (if (:rejected? constructed)
        (is (:rejected? constructed))
        (doseq [text [(:text constructed) (str (:text constructed) "x")]]
          (let [data (try (digest/sha256 text) nil
                          (catch clojure.lang.ExceptionInfo error (ex-data error)))]
            (is (= :unpaired-surrogate (:reason data)))
            (is (not (contains? data :value)))))))))

(deftest exact-utf8-and-boundary-vectors
  ;; Independently calculated with Node's crypto SHA-256, not this adapter.
  ;; Composed/decomposed e-acute must not be normalized to the same bytes.
  (doseq [[text expected]
          [["a😀b" "6fba5b2ea783ded096fc2444d540ffbdf49168df30993b155b7efb683313f110"]
           ["é" "4a99557e4033c3539de2eb65472017cad5f9557f7a0625a09f1c3f6e2ba69c4c"]
           ["é" "bf12767b0f2a56b2190075bae8169f656e3ce8d6357d4aff184bc6c7ea48f9f6"]
           [(apply str (repeat 262144 "a"))
            "dd3dde87623d9a6b354c68c943d189c89c63652d945e7bbdf0986cae91a49521"]]]
    (is (= expected (digest/sha256 text)))))

(defn -main [& _]
  (let [{:keys [fail error]} (clojure.test/run-tests 'hegel.corpus-digest-test)]
    (when (pos? (+ fail error))
      (throw (ex-info "corpus digest tests failed" {:fail fail :error error})))))
