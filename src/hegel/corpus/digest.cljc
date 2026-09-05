(ns hegel.corpus.digest
  "Bounded UTF-8 SHA-256 for corpus integrity; never loads libhegel.

  Hashes identify exact payload bytes relative to an independent trusted pin.
  They are not signatures or evidence that generated values are safe to publish."
  (:require [hegel.internal.portable-data :as data]
            #?(:jolt [hegel.corpus.digest.jolt :as native-digest]))
  #?@(:jolt []
      :cljr []
      :jank []
      :clj [(:import [java.security MessageDigest])]))

(def ^:private max-text-units 262144)

(defn- invalid! [reason]
  (throw (ex-info "invalid corpus digest input"
                  {:type ::invalid-text :hegel/usage-error? true :reason reason})))

(defn validate-text!
  "Return bounded Unicode text unchanged, rejecting unpaired UTF-16 surrogates.
  Jolt enumerates codepoints; JVM/BB enumerate UTF-16 code units."
  [text]
  (when-not (string? text) (invalid! :string-required))
  (when (or (> (count text) max-text-units)
            (> (data/text-size text) max-text-units))
    (invalid! :max-text-chars))
  (loop [chars (seq text)]
    (when-let [c (first chars)]
      (let [code (int c)]
        (cond
          (<= 55296 code 56319)
          (if (and (next chars) (<= 56320 (int (second chars)) 57343))
            (recur (nnext chars))
            (invalid! :unpaired-surrogate))

          (<= 56320 code 57343) (invalid! :unpaired-surrogate)
          (> code 1114111) (invalid! :invalid-codepoint)
          :else (recur (next chars))))))
  text)

(defn- hex [bytes]
  (let [digits "0123456789abcdef"]
    (apply str (mapcat (fn [b]
                         (let [n (bit-and (int b) 255)]
                           [(nth digits (quot n 16)) (nth digits (mod n 16))]))
                       bytes))))

(defn sha256
  "Lowercase SHA-256 of the exact UTF-8 bytes of bounded Unicode text."
  [text]
  (validate-text! text)
  #?(:jolt (hex (native-digest/sha256-bytes (.getBytes text "UTF-8")))
     :cljr (let [provider (System.Security.Cryptography.SHA256/Create)]
             (try
               (hex (.ComputeHash provider
                                  (.GetBytes System.Text.Encoding/UTF8 text)))
               (finally (.Dispose provider))))
     :jank (throw (ex-info "corpus digest is not yet supported on experimental jank"
                           {:type ::unsupported-host :host :jank}))
     :clj (hex (.digest (MessageDigest/getInstance "SHA-256")
                        (.getBytes ^String text "UTF-8")))))
