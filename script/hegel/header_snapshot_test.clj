(ns hegel.header-snapshot-test
  (:require [clojure.test :refer [deftest is testing]]
            [hegel.header-snapshot :as snapshot]))

(deftest parse-constant-shifts
  (is (= 1 (snapshot/parse-int "(1 << 0)")))
  (is (= 2 (snapshot/parse-int "(1 << 1)")))
  (is (= 32 (snapshot/parse-int "(1 << 5)")))
  (is (= 31 (snapshot/parse-int "0x1F")))
  (is (= -10 (snapshot/parse-int "-10"))))

(deftest pinned-header-counts-and-callback
  (let [result (snapshot/snapshot)]
    (is (= 77 (count (:functions result))))
    (is (= 1 (count (:callbacks result))))
    (is (= {:return "void"
            :args [{:name "user_data" :type "void*"}
                   {:name "line" :type "const-char*"}
                   {:name "len" :type "size"}]}
           (get (:callbacks result) "hegel_output_callback_t")))))

(deftest parser-fails-closed
  (testing "unknown top-level declaration"
    (is (thrown? clojure.lang.ExceptionInfo
                 (snapshot/parse-header "typedef unsigned long mystery_t;"))))
  (testing "unknown preprocessor directive"
    (is (thrown? clojure.lang.ExceptionInfo
                 (snapshot/parse-header "#define MYSTERY 7\n"))))
  (testing "malformed callback is not silently erased"
    (is (thrown? clojure.lang.ExceptionInfo
                 (snapshot/parse-header "typedef void (*hegel_output_callback_t)(void *user_data);"))))
  (testing "duplicate function names"
    (is (thrown? clojure.lang.ExceptionInfo
                 (snapshot/parse-header
                  (str "hegel_result_t hegel_x(void);\n"
                       "hegel_result_t hegel_x(void);"))))))

(deftest provenance-fails-closed
  (is (thrown? clojure.lang.ExceptionInfo
               (snapshot/validate-provenance!
                "header"
                {:sha256 "wrong" :bytes 6 :lines 1}))))
