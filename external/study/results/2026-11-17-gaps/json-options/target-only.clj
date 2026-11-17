(require '[yyjson.core :as json])
(assert (= {"a" false} (json/read-str "{\"a\":true,\"a\":false}")))
(assert (= {nil false} (json/read-str "{\"a\":true,\"b\":false}" :key-fn (constantly nil))))
(assert (= {"K\u0000a" [ {"K\u0000b" "b\u0000😀"} ]}
           (json/read-str (json/write-str {"a" [{"b" "😀"}]}
            :pretty true :key-fn #(str "K\u0000" %) :value-fn (fn [k v] (if (string? v) (str k "\u0000" v) v))))))
(assert (= "😀\u0000" (json/read-str "\"\\ud83d\\ude00\\u0000\"")))
(println "Target-only contract witnesses passed")
