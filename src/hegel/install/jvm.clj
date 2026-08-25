(ns hegel.install.jvm
  "JVM and Babashka download/checksum mechanics for hegel.install."
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str])
  (:import [java.io File FileInputStream FileOutputStream]
           [java.net URI]
           [java.security MessageDigest]))

(defn property [name]
  (System/getProperty name))

(defn uname-machine []
  (try
    (let [{:keys [exit out]} (shell/sh "uname" "-m")]
      (when (zero? exit)
        (str/trim out)))
    (catch Throwable _ nil)))

(defn path-exists? [path]
  (.exists (File. path)))

(defn directory? [path]
  (.isDirectory (File. path)))

(defn mkdirs! [path]
  (.mkdirs (File. path)))

(defn delete-file! [path]
  (.delete (File. path)))

(defn rename-file! [source target]
  (.renameTo (File. source) (File. target)))

(defn read-text [path]
  (slurp path))

(defn- powershell-literal [value]
  (str "'" (str/replace value "'" "''") "'"))

(defn- windows-download! [url path]
  (let [script (str "$ErrorActionPreference='Stop';"
                    "$ProgressPreference='SilentlyContinue';"
                    "Invoke-WebRequest -UseBasicParsing -Uri "
                    (powershell-literal url)
                    " -OutFile " (powershell-literal path))
        arguments ["powershell.exe" "-NoLogo" "-NoProfile"
                   "-NonInteractive" "-Command" script]
        {:keys [exit]} (apply shell/sh arguments)]
    (when-not (zero? exit)
      (throw
       (ex-info (str "PowerShell download failed with exit " exit)
                {:type ::command-failed
                 :arguments arguments
                 :exit exit}))))
  true)

(defn- url-download! [url path]
  (with-open [input (.openStream (.toURL (URI/create url)))
              output (FileOutputStream. path)]
    (let [buffer (byte-array 65536)]
      (loop []
        (let [n (.read input buffer)]
          (when-not (= -1 n)
            (.write output buffer 0 n)
            (recur))))))
  true)

(defn download! [os url path]
  (if (= :windows os)
    (windows-download! url path)
    (url-download! url path)))

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

(defn checksum-matches? [_os path expected]
  (= expected (sha256 path)))
