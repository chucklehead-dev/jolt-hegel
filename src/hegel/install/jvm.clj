(ns hegel.install.jvm
  "JVM and Babashka download/checksum mechanics for hegel.install."
  (:import [java.io FileInputStream FileOutputStream]
           [java.net URI]
           [java.security MessageDigest]))

(defn download! [url path]
  (with-open [input (.openStream (.toURL (URI/create url)))
              output (FileOutputStream. path)]
    (let [buffer (byte-array 65536)]
      (loop []
        (let [n (.read input buffer)]
          (when-not (= -1 n)
            (.write output buffer 0 n)
            (recur))))))
  true)

(defn sha256 [path]
  (let [digest (MessageDigest/getInstance "SHA-256")]
    (with-open [input (FileInputStream. path)]
      (let [buffer (byte-array 65536)]
        (loop []
          (let [n (.read input buffer)]
            (when-not (= -1 n)
              (.update digest buffer 0 n)
              (recur))))))
    (apply str (map #(format "%02x" (bit-and % 0xff)) (.digest digest)))))
