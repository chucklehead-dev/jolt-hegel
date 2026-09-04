(ns hegel.install.jolt
  "Jolt filesystem, process, download, and digest mechanics for hegel.install."
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str]
            [jolt.ffi :as ffi]
            [jolt.host :as jolt-host]
            [jolt.mvn-http :as mvn-http]))

(ffi/load-library)

(def ^:private c-system
  (ffi/foreign-fn "system" [:string] :int))

(def ^:private c-sha256
  (ffi/foreign-fn "SHA256" [:pointer :size_t :pointer] :pointer))

(defn property [name]
  (System/getProperty name))

(defn uname-machine []
  (try
    (let [{:keys [exit out]} (shell/sh "uname" "-m")]
      (when (zero? exit)
        (str/trim out)))
    (catch Throwable _ nil)))

(defn path-exists? [path]
  (jolt-host/file-exists? path))

(defn directory? [path]
  (jolt-host/directory? path))

(defn mkdirs! [path]
  (jolt-host/mkdirs! path))

(defn delete-file! [path]
  (jolt-host/delete-file! path))

(defn rename-file! [source target]
  (jolt-host/rename-file! source target))

(defn read-text [path]
  (slurp path))

(defn- powershell-literal [value]
  (str "'" (str/replace value "'" "''") "'"))

(defn- windows-command [arguments]
  ;; cmd.exe requires an extra outer quote when the executable itself is
  ;; quoted: `\"\"C:\\path\\cc.exe\" \"arg\"\"`.
  (str "\""
       (str/join " "
                 (map #(str "\"" (str/replace % "\"" "\\\"") "\"")
                      arguments))
       "\""))

(defn- run-windows! [description arguments]
  (let [exit (c-system (windows-command arguments))]
    (when-not (zero? exit)
      (throw
       (ex-info (str description " failed with exit " exit)
                {:type ::command-failed
                 :description description
                 :arguments arguments
                 :exit exit}))))
  nil)

(defn download! [os url path]
  (if (= :windows os)
    (let [script (str "$ErrorActionPreference='Stop';"
                      "$ProgressPreference='SilentlyContinue';"
                      "Invoke-WebRequest -UseBasicParsing -Uri "
                      (powershell-literal url)
                      " -OutFile " (powershell-literal path))]
      (run-windows! "PowerShell download"
                    ["powershell.exe" "-NoLogo" "-NoProfile"
                     "-NonInteractive" "-Command" script])
      true)
    (or (mvn-http/fetch url path)
        (throw (ex-info (str "failed to download " url)
                        {:type ::download-failed
                         :url url
                         :path path})))))

(defn- crypto-candidates [os]
  (if (= :darwin os)
    ["/opt/homebrew/opt/openssl@3/lib/libcrypto.dylib"
     "/opt/homebrew/lib/libcrypto.dylib"
     "/usr/local/opt/openssl@3/lib/libcrypto.dylib"]
    ["libcrypto.so.3" "libcrypto.so.1.1" "libcrypto.so"]))

(defn- ensure-crypto! [os]
  (when-not
   (some (fn [candidate]
           (try
             (ffi/load-library candidate)
             true
             (catch Throwable _ false)))
         (crypto-candidates os))
    (throw
     (ex-info "could not load OpenSSL libcrypto to verify native downloads"
              {:type ::crypto-unavailable}))))

(defn- digest-hex [bytes]
  (apply str
         (map #(format "%02x" (bit-and % 0xff))
              (seq bytes))))

(defn- read-all-bytes [path]
  (with-open [input (java.io.FileInputStream. path)]
    (.readAllBytes input)))

(defn- with-digest-buffers [data f]
  (let [length (alength data)
        source (ffi/alloc (max 1 length))
        digest (ffi/alloc 32)]
    (try
      (ffi/write-array source data)
      (f source length digest)
      (digest-hex (ffi/read-array digest 32))
      (finally
        (ffi/free digest)
        (ffi/free source)))))

(defn- posix-sha256 [os path data]
  (ensure-crypto! os)
  (with-digest-buffers
    data
    (fn [source length digest]
      (when (ffi/null? (c-sha256 source length digest))
        (throw
         (ex-info (str "SHA256 failed for " path)
                  {:type ::checksum-failed
                   :path path}))))))

(defn- windows-child-path [parent child]
  (str parent
       (when-not (or (str/ends-with? parent "\\")
                     (str/ends-with? parent "/"))
         "\\")
       child))

(defn- windows-sha256 [path]
  ;; Jolt 0.7.x resolves drive-rooted FileInputStream paths as if they were
  ;; relative. PowerShell already provides the Windows download boundary, so
  ;; retain its managed SHA-256 provider and return the digest through a unique
  ;; harness-owned file under user.dir. Jolt's slurp and java.io.File methods
  ;; resolve relative paths against that same user.dir base, avoiding a
  ;; drive-rooted path at both read and cleanup boundaries.
  (let [output-name (str ".hegel-sha256-" (random-uuid) ".txt")
        output-path (windows-child-path (System/getProperty "user.dir")
                                        output-name)
        script (str "$ErrorActionPreference='Stop';"
                    "$bytes=[System.IO.File]::ReadAllBytes("
                    (powershell-literal path) ");"
                    "$hash=[System.Security.Cryptography.SHA256]::Create()"
                    ".ComputeHash($bytes);"
                    "$actual=[System.BitConverter]::ToString($hash)"
                    ".Replace('-','').ToLowerInvariant();"
                    "[System.IO.File]::WriteAllText("
                    (powershell-literal output-path) ",$actual,"
                    "[System.Text.Encoding]::ASCII)")]
    (try
      (run-windows! "PowerShell SHA-256"
                    ["powershell.exe" "-NoLogo" "-NoProfile"
                     "-NonInteractive" "-Command" script])
      (str/trim (slurp output-name))
      (finally
        (.delete (java.io.File. output-name))))))

(defn sha256 [os path]
  (if (= :windows os)
    (windows-sha256 path)
    (posix-sha256 os path (read-all-bytes path))))
