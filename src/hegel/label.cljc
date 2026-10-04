(ns hegel.label
  "Portable opaque span labels, matching libhegel's UTF-8 FNV-1a contract."
  (:require [clojure.string :as str]
            [hegel.validation :as validation]
            #?(:jank [hegel.host.jank-host :as jank-host])))

(def ^:private offset-basis 14695981039346656037N)
(def ^:private prime 1099511628211N)
(def ^:private modulus 18446744073709551616N)

(defn- step [hash octet]
  (let [low (mod hash 256)]
    (mod (*' (+ (- hash low) (bit-xor (long low) octet)) prime) modulus)))

(defn from-name
  "Hash a NUL-free UTF-8 name into a uint64 label. No native library is needed."
  [name]
  (when-not (and (string? name) (not (str/includes? name "\u0000")))
    (validation/usage-error! ::invalid-name "label name must be a NUL-free string" {}))
  (reduce step offset-basis
          (map #(bit-and 255 %)
               #?(:jank (jank-host/utf8-octets name)
                  :cljr (.GetBytes System.Text.Encoding/UTF8 name)
                  :default (.getBytes name "UTF-8")))))

(defn combine
  "Hash labels' little-endian bytes in order. A singleton is also hashed."
  [labels]
  (when-not (or (vector? labels) (list? labels))
    (validation/usage-error! ::invalid-labels "labels must be a vector or list" {}))
  (reduce
   (fn [hash label]
     (when-not (and (integer? label) (<= 0 label (dec modulus)))
       (validation/usage-error! ::invalid-label "label must fit uint64" {}))
     (loop [hash hash value label remaining 8]
       (if (zero? remaining)
         hash
         (recur (step hash (long (mod value 256))) (quot value 256) (dec remaining)))))
   offset-basis labels))
