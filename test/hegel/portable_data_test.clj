(ns hegel.portable-data-test
  (:require [clojure.test :refer [deftest is]]
            [hegel.internal.portable-data :as data]
            [hegel.internal.portable-edn :as portable-edn]))

(def limits
  {:max-text-chars 64
   :max-depth 3
   :max-nodes 16
   :max-string-chars 8})

(defn- data-invalid! [path reason]
  (throw (ex-info "data rejected" {:path path :reason reason})))

(defn- edn-invalid! [reason]
  (throw (ex-info "edn rejected" {:reason reason})))

(defn- data-error [thunk]
  (try (thunk) nil (catch clojure.lang.ExceptionInfo error (ex-data error))))

(deftest portable-data-is-parameterized-by-limits-and-error-callback
  (let [value {:message "ok" :items [1 true :keyword]}]
    (is (= value (data/validate! value limits data-invalid!))))
  (is (= {:path [:entry 0 :value] :reason :max-string-chars}
         (data-error #(data/validate! {:message "123456789"}
                                      limits data-invalid!))))
  (is (= :max-depth
         (:reason (data-error #(data/validate! [[[[0]]]] limits data-invalid!)))))
  (is (= 4 (data/text-size "a😀b"))))

(deftest portable-edn-keeps-caller-owned-validation-and-errors
  (let [validated (atom [])
        validate! (fn [value] (swap! validated conj value) value)
        decoded (portable-edn/decode "{:x [1 true]}" limits edn-invalid! validate!)]
    (is (= {:x [1 true]} decoded))
    (is (= [decoded] @validated))
    (is (= decoded
           (portable-edn/decode
            (portable-edn/encode decoded limits edn-invalid! validate!)
            limits edn-invalid! validate!))))
  (is (= :unsupported-reader-syntax
         (:reason (data-error #(portable-edn/decode "#{:x}" limits
                                                   edn-invalid! identity)))))
  (is (= :max-depth
         (:reason (data-error #(portable-edn/decode "[[[[[0]]]]]" limits
                                                   edn-invalid! identity))))))

(deftest independent-budgets-and-validation-order
  (is (= :max-nodes
         (:reason (data-error #(data/validate! {:a 1}
                                              (assoc limits :max-nodes 2)
                                              data-invalid!)))))
  (is (= :max-text-chars
         (:reason (data-error #(data/validate! ["aaaa" "bbbb"]
                                              (assoc limits :max-text-chars 7)
                                              data-invalid!)))))
  (let [validate! #(data/validate! % limits data-invalid!)
        small (assoc limits :max-text-chars 10)]
    (is (= :max-text-chars
           (:reason (data-error #(portable-edn/encode "\n\n\n\n\n" small
                                                     edn-invalid! validate!)))))
    (is (= "a😀b"
           (portable-edn/decode
            (portable-edn/encode "a😀b" limits edn-invalid! validate!)
            limits edn-invalid! validate!))))
  (let [calls (atom 0)
        validate! (fn [value] (swap! calls inc) value)]
    (is (= :one-form-required
           (:reason (data-error #(portable-edn/decode "1 2" limits
                                                     edn-invalid! validate!)))))
    (is (= :invalid-edn
           (:reason (data-error #(portable-edn/decode "{:a}" limits
                                                     edn-invalid! validate!)))))
    (is (zero? @calls)))
  (let [failure (ex-info "caller validation failed" {:source :caller})
        caught (try (portable-edn/decode "1" limits edn-invalid!
                                         (fn [_] (throw failure)))
                    (catch clojure.lang.ExceptionInfo error error))]
    (is (identical? failure caught))))

(defn -main [& _]
  (let [{:keys [fail error]} (clojure.test/run-tests 'hegel.portable-data-test)]
    (when (pos? (+ fail error))
      (throw (ex-info "portable data tests failed" {:fail fail :error error})))))
