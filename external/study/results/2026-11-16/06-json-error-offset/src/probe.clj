(ns probe
  (:require [hegel.core :as h] [hegel.generator :as g]
            [hegel.replay-bundle :as bundle] [hegel.replay-bundle.codec :as codec]
            [clojure.edn :as edn] [yyjson.core :as json]))

;; Each token is one Unicode scalar, with its UTF-8 width independently specified.
;; No target serialization, UTF-8 encoder or target offset implementation is used.
(def tokens ["a" "é" "中" "😀" " "])
(def widths [1 2 3 4 1])
(def prefix-gen (g/vector {:min-size 0 :max-size 32} (g/integer 0 4)))
(def provenance
  {:hegel-sha "8179cb83eda81c708dc834ee38647f2354fe3d55"
   :libhegel-version "0.51.6"
   :runtime {:host :jolt :version "0.8.27" :os (System/getProperty "os.name") :arch (System/getProperty "os.arch")}
   :property-id "yyjson/raw-nul-offset/character-offset-control"
   :generator-revision "token-vector-v1" :model-revision "utf8-width-table-v1"})
(def captured (atom nil))
(defn check-input! [indices broken?]
  (let [prefix (apply str (map tokens indices))
        input (str "\"" prefix "\u0000z\"")
        expected (inc (reduce + 0 (map widths indices)))
        actual (try {:accepted (json/read-str input)}
                    (catch Throwable e (ex-data e)))
        ;; Deliberate mutation: expose a Unicode character offset as a byte offset.
        actual (if (and broken? (= :invalid (:yyjson/error actual)))
                 (assoc actual :position (inc (count indices))) actual)
        recovery (json/read-str "{\"ok\":[true,42,\"😀\"]}")
        safe (json/read-str (str "\"" prefix "z\""))
        evidence {:indices indices :prefix prefix :expected expected :actual actual
                  :recovery recovery :safe safe}]
    (when (or (not= :invalid (:yyjson/error actual))
              (not= expected (:position actual))
              (not= {"ok" [true 42 "😀"]} recovery)
              (not= (str prefix "z") safe))
      (throw (ex-info "raw NUL rejection/byte offset/recovery mismatch"
                      (assoc evidence :hegel/origin "raw-nul/offset-and-recovery"))))
    evidence))
(defn property [broken?]
  (fn [_]
    (let [indices (h/draw! prefix-gen)]
      (h/event! (if (empty? indices) "empty" "nonempty"))
      (doseq [w (set (map widths indices))] (h/event! (str "width-" w)))
      (when (> (count (set (map widths indices))) 1) (h/event! "mixed-width"))
      (h/observe! (count indices) "prefix-scalars")
      (when (h/final?) (reset! captured {:indices indices}))
      (check-input! indices broken?))))
(def opts {:test-cases 300 :seed 20261116 :database "" :verbosity :quiet :observations? true})
(defn -main [& [mode]]
  (case mode
    "baseline"
    (let [result (h/run-test! (assoc opts :coverage {:scope :exploration :requirements
                          (zipmap ["empty" "nonempty" "width-1" "width-2" "width-3" "width-4" "mixed-width"]
                                  (repeat {:min-count 1}))}) (property false))]
      (spit "baseline-result.edn" (pr-str result))
      (prn result)
      (when-not (:passed? result) (System/exit 1)))
    "control"
    (let [result (h/run-test! opts (property true))]
      (spit "control-result.edn" (pr-str result))
      (prn result)
      (when (or (:passed? result) (:flaky? result) (not= :failed (:status result)))
        (throw (ex-info "Expected stable control failure" {:result result})))
      (spit "control-bundle.edn" (codec/encode (bundle/from-result provenance result)))
      (spit "fixture.edn" (pr-str @captured)))
    "replay"
    (let [result (h/replay-bundle! provenance (codec/decode (slurp "control-bundle.edn")) (property true))]
      (spit "replay-result.edn" (pr-str result)) (prn result)
      (when-not (:reproduced? result) (System/exit 1)))
    "fixture"
    (let [indices (:indices (edn/read-string (slurp "fixture.edn")))
          baseline (check-input! indices false)
          control (try (check-input! indices true) :unexpected-pass
                       (catch Throwable e (ex-data e)))
          result {:baseline baseline :control control}]
      (spit "fixture-result.edn" (pr-str result)) (prn result)
      (when-not (= "raw-nul/offset-and-recovery" (:hegel/origin control)) (System/exit 1)))))
