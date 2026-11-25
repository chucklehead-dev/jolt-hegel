(ns probe
  (:require [hegel.core :as h] [hegel.generator :as g]
            [hegel.replay-bundle :as bundle] [hegel.replay-bundle.codec :as codec]
            [yyjson.core :as json] [clojure.edn :as edn]))

(def manifest {:hegel-sha "8179cb83eda81c708dc834ee38647f2354fe3d55"
 :libhegel-version "0.51.6"
 :runtime {:host :jolt :version "0.8.27" :os "Linux" :arch "x86_64"}
 :property-id "json-unicode-equivalence" :generator-revision "scalar-v1"
 :model-revision "independent-json-encoder-v1"})

(defn original [cps] (apply str (map char cps)))
(defn u4 [n] (format "\\u%04x" n))
(defn escaped-cp [cp broken?]
  (if (< cp 65536) (u4 cp)
    (let [n (- cp 65536) hi (+ 55296 (quot n 1024)) lo (mod n 1024)]
      (str (u4 hi) (u4 (+ 56320 (if broken? (mod (inc lo) 1024) lo)))))))
(defn encode [cps mode broken?]
 (str "\"" (apply str (map (fn [cp]
   (if (= mode :escaped) (escaped-cp cp broken?)
     (cond (< cp 32) (u4 cp) (= cp 34) "\\\"" (= cp 92) "\\\\"
           :else (str (char cp))))) cps)) "\""))
(def scalar (g/one-of [(g/integer 0 55295) (g/integer 57344 65535)
                       (g/integer 65536 1114111)
                       (g/sampled-from [0 1 8 9 10 13 31 34 92 127 128 2047 2048 55295 57344 65535 65536 128512 1114111])]))
(def inputs (g/vector {:min-size 0 :max-size 24} scalar))
(def labels ["empty" "nul" "control" "supplementary" "bmp-nonascii" "quote" "backslash"])
(defn mark! [cps]
 (when (empty? cps) (h/event! "empty"))
 (doseq [[label pred] [["nul" zero?] ["control" #(< % 32)] ["supplementary" #(>= % 65536)]
                       ["bmp-nonascii" #(<= 128 % 65535)] ["quote" #(= % 34)] ["backslash" #(= % 92)]]]
   (when (some pred cps) (h/event! label)))
 (h/observe! (count cps) "scalar-count"))
(defn check-input [cps broken?]
 (let [s (original cps) raw (encode cps :raw false) escaped (encode cps :escaped broken?)
       a (json/read-str raw) b (json/read-str escaped)]
  (when-not (= s a b)
   (throw (ex-info "JSON encodings did not decode to original"
      {:hegel/origin "json-unicode/equivalence" :codepoints cps :raw raw :escaped escaped
       :expected s :raw-decoded a :escaped-decoded b})))
  {:codepoints cps :raw raw :escaped escaped}))
(defn property [broken?]
 (fn [_] (let [cps (h/draw! inputs "codepoints")]
    (mark! cps) (h/note! {:codepoints cps}) (check-input cps broken?))))
(def opts {:test-cases 500 :seed 20261116 :database "" :verbosity :quiet
           :observations? true})
(defn save [path value] (spit path (pr-str value)))
(defn -main [& [mode]]
 (case mode
  "good" (let [r (h/run-test! (assoc opts :coverage {:scope :exploration :requirements
                       (into {} (map #(vector % {:min-count 1}) labels))}) (property false))]
     (save "good-result.edn" r) (prn (select-keys r [:passed? :status :valid-test-cases :coverage :observations]))
     (when-not (:passed? r) (throw (ex-info "good property failed" {}))))
  "broken" (let [r (h/run-test! opts (property true))]
     (save "broken-result.edn" r)
     (spit "replay.edn" (codec/encode (bundle/from-result manifest r)))
     (prn (select-keys r [:passed? :status :flaky? :counterexample :failures :final]))
     (when (:passed? r) (throw (ex-info "control failed to detect mutation" {}))))
  "replay" (let [r (h/replay-bundle! manifest (codec/decode (slurp "replay.edn")) (property true))]
     (save "replay-result.edn" r) (prn r)
     (when-not (:reproduced? r) (throw (ex-info "replay failed" {}))))
  "fixture" (let [cps (edn/read-string (slurp "fixture.edn"))]
     (prn :unmodified (check-input cps false))
     (try (check-input cps true) (throw (ex-info "control unexpectedly passed" {}))
       (catch Exception e (if (= "json-unicode/equivalence" (:hegel/origin (ex-data e)))
          (prn :control-detected (ex-data e)) (throw e)))))
  "witnesses" (doseq [cps [[] (vec (range 32)) [34 92] [128 2047 2048 55295 57344 65535]
                          [65536 128512 1114111] [65 0 66] [128104 8205 128105 65039]]]
                  (prn :witness (check-input cps false)))))
