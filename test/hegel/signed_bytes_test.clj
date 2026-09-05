(ns hegel.signed-bytes-test
  (:require [clojure.test :refer [deftest is]]
            [hegel.internal.signed-bytes :as signed-bytes]))

(defn- power-of-two [exponent]
  (loop [remaining exponent
         value 1N]
    (if (zero? remaining)
      value
      (recur (dec remaining) (*' value 2)))))

(defn- error-type [thunk]
  (try
    (thunk)
    nil
    (catch Throwable error
      (:type (ex-data error)))))

(deftest literal-signed-little-endian-boundaries
  (doseq [[value octets]
          [[0 [0]]
           [127 [127]]
           [128 [128 0]]
           [255 [255 0]]
           [256 [0 1]]
           [-1 [255]]
           [-128 [128]]
           [-129 [127 255]]
           [-256 [0 255]]
           [(power-of-two 63) [0 0 0 0 0 0 0 128 0]]
           [(-' (power-of-two 63)) [0 0 0 0 0 0 0 128]]]]
    (is (= octets (signed-bytes/encode value)))
    (is (= value (signed-bytes/decode octets)))))

(deftest arbitrary-width-round-trips-and-sign-extension
  (let [two-to-128 (power-of-two 128)
        two-to-256 (power-of-two 256)
        values [two-to-128 (-' two-to-128)
                (+' two-to-128 37) (-' (-' two-to-128) 37)
                two-to-256 (-' two-to-256)]]
    (doseq [value values]
      (is (= value (signed-bytes/decode (signed-bytes/encode value)))))
    (is (= -1N (signed-bytes/decode [-1])))
    (is (= -1N (signed-bytes/decode [255 255 255])))
    (is (= -129N (signed-bytes/decode [127 -1])))
    (is (= 128N (signed-bytes/decode [-128 0])))))

(deftest invalid-arguments-are-usage-errors
  (doseq [value [nil 1.0 1/2 :integer]]
    (is (= ::signed-bytes/invalid-integer
           (error-type #(signed-bytes/encode value)))))
  (doseq [octets [nil [] '() [256] [-129] [1 :octet]]]
    (is (= ::signed-bytes/invalid-bytes
           (error-type #(signed-bytes/decode octets))))))
