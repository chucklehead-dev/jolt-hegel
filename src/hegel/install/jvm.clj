(ns hegel.install.jvm
  "JVM and Babashka download/checksum mechanics for hegel.install."
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str])
  (:import [java.io File FileInputStream FileOutputStream]
           [java.net URI]
           [java.nio.file Files CopyOption StandardCopyOption]
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
  ;; Both paths are siblings. Require atomic publication: do not fall back to
  ;; copy/delete on an unsupported filesystem. Existing-target behavior is
  ;; provider-specific; shared policy handles a verified concurrent winner.
  (Files/move (.toPath (File. source)) (.toPath (File. target))
              (into-array CopyOption [StandardCopyOption/ATOMIC_MOVE]))
  true)

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

(defn- proxy-configured? [url]
  (let [scheme (.getScheme (URI/create url))
        names (case scheme
                "https" ["https_proxy" "HTTPS_PROXY" "all_proxy" "ALL_PROXY"]
                "http" ["http_proxy" "all_proxy" "ALL_PROXY"]
                [])]
    (and (nil? (System/getProperty (str scheme ".proxyHost")))
         (some #(not (str/blank? (System/getenv %))) names))))

(defn download! [os url path]
  (cond
    (proxy-configured? url)
    (let [command (if (= :windows os) "curl.exe" "curl")
          hint "A proxy is configured. Check curl on PATH, proxy and NO_PROXY settings, or set HEGEL_LIBHEGEL_LIBRARY."
          exit (try (:exit (shell/sh command "--disable" "--fail" "--silent" "--show-error"
                                     "--location" "--output" path "--" url))
                    (catch java.io.IOException _ nil))]
      (when-not (= 0 exit)
        (throw (ex-info hint {:type ::proxy-download :hegel.install/download-hint hint :exit exit})))
      true)
    (= :windows os) (windows-download! url path)
    :else (url-download! url path)))

(defn sha256 [_os path]
  (let [digest (MessageDigest/getInstance "SHA-256")]
    (with-open [input (FileInputStream. path)]
      (let [buffer (byte-array 65536)]
        (loop []
          (let [n (.read input buffer)]
            (when-not (= -1 n)
              (.update digest buffer 0 n)
              (recur))))))
    (apply str (map #(format "%02x" (bit-and % 0xff)) (.digest digest)))))
