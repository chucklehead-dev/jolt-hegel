(require (quote [yyjson.core :as json]))
(doseq [s ["-9223372036854775808" "-9223372036854775809" "9223372036854775808" "18446744073709551615" "18446744073709551616" "18446744073709551617"]]
 (let [v (json/read-str s)] (prn {:input s :value v :type (str (type v)) :rewritten (json/write-str v)})))
(assert (= 9007199254740993N (json/read-str "9007199254740993")))
(assert (= "9007199254740993" (json/write-str 9007199254740993N)))
(assert (float? (json/read-str "-9223372036854775809")))
(assert (float? (json/read-str "18446744073709551617")))
