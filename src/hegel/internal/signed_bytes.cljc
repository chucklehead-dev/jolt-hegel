(ns hegel.internal.signed-bytes
  "Portable signed two's-complement little-endian integer codec.

  `encode` returns a minimal, nonempty vector of unsigned octets. `decode`
  accepts only a nonempty vector whose integer octets are either unsigned
  (0..255) or signed host bytes (-128..-1), and returns an arbitrary-precision
  integer. Native adapters normalize byte-array reads before calling `decode`."
  (:require [hegel.validation :as validation]))

(defn- invalid-integer! [value]
  (validation/usage-error! ::invalid-integer
                           "signed-byte encoding requires an integer"
                           {:value value}))

(defn- invalid-bytes! [value]
  (validation/usage-error! ::invalid-bytes
                           "signed-byte decoding requires a nonempty vector of octets"
                           {:value value}))

(defn encode
  "Encode an integer as minimal nonempty signed two's-complement LE octets."
  [value]
  (when-not (integer? value)
    (invalid-integer! value))
  (loop [remaining value
         octets []]
    (let [octet (mod remaining 256)
          next-value (quot (-' remaining octet) 256)
          result (conj octets octet)]
      (if (or (and (zero? next-value) (< octet 128))
              (and (= -1 next-value) (>= octet 128)))
        result
        (recur next-value result)))))

(defn- unsigned-octet [octet]
  ;; The validated range makes this exactly equivalent to `bit-and octet 0xff`
  ;; without requiring bit operations to accept arbitrary-precision host ints.
  (if (neg? octet)
    (+' octet 256)
    octet))

(defn decode
  "Decode a nonempty vector of signed/unsigned octets into an integer."
  [octets]
  (when-not (and (vector? octets)
                 (seq octets)
                 (every? #(and (integer? %) (<= -128 % 255)) octets))
    (invalid-bytes! octets))
  (let [[unsigned place]
        (reduce (fn [[total multiplier] octet]
                  [(+' total (*' (unsigned-octet octet) multiplier))
                   (*' multiplier 256)])
                [0N 1N]
                octets)
        high-octet (unsigned-octet (peek octets))]
    (if (>= high-octet 128)
      (-' unsigned place)
      unsigned)))
