(ns hegel.install
  "Install pinned native dependencies for a jolt-hegel Git dependency.

  This namespace lives on the normal source path. Consumers activate whichever
  alias contains the dependency, for example
  `jolt -A:test -m hegel.install`; repository aliases are not inherited."
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str]
            [hegel.host :as host]
            [hegel.native :as native]
            [hegel.version :as version]))

(def ^:private libhegel-release-base
  (str "https://github.com/hegeldev/hegel-rust/releases/download/v"
       version/libhegel-version))

(def ^:private libhegel-assets
  {[:linux "amd64"]
   {:name "libhegel-linux-amd64.so"
    :sha256 "9a14df0a6259ce83426e4015ce02d376b938323f7c210bab90b86bfbca970406"}

   [:linux "arm64"]
   {:name "libhegel-linux-arm64.so"
    :sha256 "0ed646c4dc560ba8755006c274ec4a5c10e9cc6919b731c2918906a78c246841"}

   [:darwin "arm64"]
   {:name "libhegel-darwin-arm64.dylib"
    :sha256 "86042e449e74020340001f31bf99f9abb0f06a33aff170d9ad6c7e03b2cbf64e"}

   [:windows "amd64"]
   {:name "libhegel-windows-amd64.dll"
    :sha256 "a08f48a0953f68bc2095f735ae3b521d530817eb2b91ab5d1ea215a6c6a0355d"}

   [:windows "arm64"]
   {:name "libhegel-windows-arm64.dll"
    :sha256 "ccb39178181c3bdf5b2cc549ab3b766b22c9b47fc2d4993ae165f7c94206e677"}})

;; Only the Jolt installer needs native libc/libcrypto helpers. Keeping them
;; dynamically resolved here prevents host-specific FFI from entering the
;; shared installer namespace on bb and JVM Clojure.
(def ^:private jolt? (= :jolt (host/runtime)))

(defn- jolt-var [symbol]
  (when jolt?
    (requiring-resolve symbol)))

(def ^:private jolt-load-library (jolt-var 'jolt.ffi/load-library))
(def ^:private jolt-alloc (jolt-var 'jolt.ffi/alloc))
(def ^:private jolt-free (jolt-var 'jolt.ffi/free))
(def ^:private jolt-null? (jolt-var 'jolt.ffi/null?))
(def ^:private jolt-read-array (jolt-var 'jolt.ffi/read-array))
(def ^:private jolt-write-array (jolt-var 'jolt.ffi/write-array))

(when jolt?
  (jolt-load-library))

(def ^:private c-system
  (when jolt?
    (eval '(jolt.ffi/foreign-fn "system" [:string] :int))))

(def ^:private c-sha256
  (when jolt?
    (eval '(jolt.ffi/foreign-fn "SHA256" [:pointer :size_t :pointer]
                                :pointer))))

(defn- nonblank [value]
  (when-not (str/blank? value)
    value))

(defn- property [name]
  (nonblank (System/getProperty name)))

(defn- normalize-architecture [machine]
  (case (some-> machine str/trim str/lower-case)
    ("x86_64" "amd64" "x64") "amd64"
    ("aarch64" "arm64") "arm64"
    nil))

(defn- uname-machine []
  (try
    (let [{:keys [exit out]} (shell/sh "uname" "-m")]
      (when (zero? exit)
        (nonblank (str/trim out))))
    (catch Throwable _ nil)))

(defn architecture
  "Return the release architecture name for the current Jolt process."
  []
  (let [{:keys [os]} (native/platform)
        candidates [(native/nonblank-env "HEGEL_NATIVE_ARCH")
                    (native/nonblank-env "HEGEL_LIBHEGEL_ARCH")
                    (property "os.arch")
                    (native/nonblank-env "RUNNER_ARCH")
                    (native/nonblank-env "PROCESSOR_ARCHITECTURE")
                    (native/nonblank-env "HOSTTYPE")
                    (when-not (= :windows os) (uname-machine))]
        architecture (some normalize-architecture candidates)]
    (or architecture
        (throw
         (ex-info
          (str "jolt-hegel has no native release mapping for architecture "
               (pr-str (first (remove str/blank? candidates))))
          {:type ::unsupported-architecture
           :candidates candidates})))))

(defn- ensure-directory! [path]
  (let [directory (java.io.File. path)]
    (when-not (or (.isDirectory directory) (.mkdirs directory))
      (throw
       (ex-info (str "could not create " path)
                {:type ::directory-failed
                 :path path}))))
  path)

(defn- require-file! [description path]
  (when-not (.isFile (java.io.File. path))
    (throw
     (ex-info (str description " does not exist: " path)
              {:type ::missing-file
               :description description
               :path path})))
  path)

(defn- version-source-path []
  (native/join-path
   (native/join-path native/project-root-path "src")
   "hegel/version.cljc"))

(defn- source-jolt-hegel-version []
  (let [path (require-file! "jolt-hegel version source"
                            (version-source-path))
        match (re-find
               #"(?s)\(def\s+jolt-hegel-version\s+\"[^\"]*\"\s+\"([^\"]+)\"\s*\)"
               (slurp path))]
    (or (second match)
        (throw
         (ex-info (str "could not read jolt-hegel version from " path)
                  {:type ::invalid-version-source
                   :path path})))))

(defn verify-source-version!
  "On Jolt, fail when AOT code came from a different release than the resolved
  source checkout. Other hosts do not AOT dependency namespaces and therefore
  return the loaded version without assuming the consumer's working directory
  is the dependency checkout."
  []
  (let [loaded version/jolt-hegel-version]
    (if-not jolt?
      loaded
      (let [source (source-jolt-hegel-version)]
        (when-not (= loaded source)
          (throw
           (ex-info
            (str "Jolt loaded jolt-hegel " loaded
                 " from a stale AOT cache, but the resolved source checkout is "
                 source ". Set JOLT_CACHE_DIR to a fresh directory keyed by the "
                 "pinned release SHA and rerun the install and test commands.")
            {:type ::stale-aot-cache
             :loaded-version loaded
             :source-version source
             :source-path (version-source-path)
             :jolt-cache-directory
             (native/nonblank-env "JOLT_CACHE_DIR")})))
        source))))

(defn- delete-if-present! [path]
  (let [file (java.io.File. path)]
    (when (and (.exists file) (not (.delete file)))
      (throw
       (ex-info (str "could not remove " path)
                {:type ::delete-failed
                 :path path})))))

(defn- replace-file! [source target]
  (let [source-file (java.io.File. source)
        target-file (java.io.File. target)]
    (when (and (.exists target-file) (not (.delete target-file)))
      (throw
       (ex-info (str "could not replace " target)
                {:type ::replace-failed
                 :path target})))
    (when-not (.renameTo source-file target-file)
      (throw
       (ex-info (str "could not move verified download to " target
                     "; verified file remains at " source)
                {:type ::replace-failed
                 :source source
                 :path target}))))
  target)

(defn- powershell-literal [value]
  (str "'" (str/replace value "'" "''") "'"))

(defn- windows-command [arguments]
  ;; cmd.exe requires an extra outer quote when the executable itself is
  ;; quoted: `\"\"C:\path\cc.exe\" \"arg\"\"`.
  (str "\""
       (str/join " "
                 (map #(str "\"" (str/replace % "\"" "\\\"") "\"")
                      arguments))
       "\""))

(defn- run-windows! [description arguments]
  (let [exit (if jolt?
               (c-system (windows-command arguments))
               (:exit (apply shell/sh arguments)))]
    (when-not (zero? exit)
      (throw
       (ex-info (str description " failed with exit " exit)
                {:type ::command-failed
                 :description description
                 :arguments arguments
                 :exit exit}))))
  nil)

(defn- windows-download! [url path]
  (let [script (str "$ErrorActionPreference='Stop';"
                    "$ProgressPreference='SilentlyContinue';"
                    "Invoke-WebRequest -UseBasicParsing -Uri "
                    (powershell-literal url)
                    " -OutFile " (powershell-literal path))]
    (run-windows! "PowerShell download"
                  ["powershell.exe" "-NoLogo" "-NoProfile"
                   "-NonInteractive" "-Command" script])))

(defn- posix-download! [url path]
  (if jolt?
    (let [fetch (requiring-resolve 'jolt.mvn-http/fetch)]
      (when-not (fetch url path)
        (throw (ex-info (str "failed to download " url)
                        {:type ::download-failed
                         :url url
                         :path path}))))
    ((requiring-resolve 'hegel.install.jvm/download!) url path)))

(defn- download! [url path]
  (println "native: downloading" url)
  (delete-if-present! path)
  (try
    (if (= :windows (:os (native/platform)))
      (windows-download! url path)
      (posix-download! url path))
    (require-file! "download" path)
    (catch Throwable cause
      (delete-if-present! path)
      (throw
       (ex-info (str "failed to download " url)
                {:type ::download-failed
                 :url url
                 :path path
                 :cause cause})))))

(defn- crypto-candidates []
  (if (= :darwin (:os (native/platform)))
    ["/opt/homebrew/opt/openssl@3/lib/libcrypto.dylib"
     "/opt/homebrew/lib/libcrypto.dylib"
     "/usr/local/opt/openssl@3/lib/libcrypto.dylib"]
    ["libcrypto.so.3" "libcrypto.so.1.1" "libcrypto.so"]))

(defn- ensure-crypto! []
  (when-not
   (some (fn [candidate]
             (try
             (jolt-load-library candidate)
             true
             (catch Throwable _ false)))
         (crypto-candidates))
    (throw
     (ex-info "could not load OpenSSL libcrypto to verify native downloads"
              {:type ::crypto-unavailable}))))

(defn- posix-sha256 [path]
  (ensure-crypto!)
  (with-open [input (java.io.FileInputStream. path)]
    (let [data (.readAllBytes input)
          length (alength data)
          source (jolt-alloc (max 1 length))
          digest (jolt-alloc 32)]
      (try
        (jolt-write-array source data)
        (when (jolt-null? (c-sha256 source length digest))
          (throw
           (ex-info (str "SHA256 failed for " path)
                    {:type ::checksum-failed
                     :path path})))
        (apply str
               (map #(format "%02x" (bit-and % 0xff))
                    (seq (jolt-read-array digest 32))))
        (finally
          (jolt-free digest)
          (jolt-free source))))))

(defn- windows-checksum-matches? [path expected]
  (let [script (str "$ErrorActionPreference='Stop';"
                    "$actual=(Get-FileHash -Algorithm SHA256 -LiteralPath "
                    (powershell-literal path) ").Hash.ToLowerInvariant();"
                    "if ($actual -ne " (powershell-literal expected)
                    ") { exit 9 }")]
    (zero? (c-system
            (windows-command
             ["powershell.exe" "-NoLogo" "-NoProfile" "-NonInteractive"
              "-Command" script])))))

(defn- checksum-matches? [path expected]
  (and (.isFile (java.io.File. path))
       (if jolt?
         (if (= :windows (:os (native/platform)))
           (windows-checksum-matches? path expected)
           (= expected (posix-sha256 path)))
         (= expected
            ((requiring-resolve 'hegel.install.jvm/sha256) path)))))

(defn- verify-file! [path expected]
  (when-not (checksum-matches? path expected)
    (throw
     (ex-info (str "SHA-256 mismatch for " path)
              {:type ::checksum-mismatch
               :expected expected
               :path path})))
  path)

(defn- download-verified! [url target expected]
  (let [staged (str target ".download")]
    (download! url staged)
    (try
      (verify-file! staged expected)
      (replace-file! staged target)
      (catch Throwable cause
        (delete-if-present! staged)
        (throw cause)))
    (println "native: verified" target "(sha256" expected ")")
    target))

(defn fetch-libhegel!
  "Fetch and verify the libhegel release pinned by hegel.version."
  []
  (verify-source-version!)
  (if (native/nonblank-env "HEGEL_LIBHEGEL_LIBRARY")
    (do
      (require-file! "HEGEL_LIBHEGEL_LIBRARY" native/library-path)
      (println "libhegel: using" native/library-path
               "(HEGEL_LIBHEGEL_LIBRARY)")
      native/library-path)
    (let [{:keys [os]} (native/platform)
          architecture (architecture)
          asset (get libhegel-assets [os architecture])]
      (when-not asset
        (throw
         (ex-info
          (str "libhegel v" version/libhegel-version
               " has no prebuilt release for " (name os) "/" architecture
               "; set HEGEL_LIBHEGEL_LIBRARY to a local build")
          {:type ::unavailable-libhegel-release
           :os os
           :architecture architecture})))
      (ensure-directory! native/cache-directory-path)
      (let [{:keys [name sha256]} asset
            url (str (or (native/nonblank-env "HEGEL_LIBHEGEL_RELEASE_BASE")
                         libhegel-release-base)
                     "/" name)]
        (if (checksum-matches? native/library-path sha256)
          (do
            (println "libhegel: already verified" native/library-path
                     "(sha256" sha256 ")")
            native/library-path)
          (download-verified! url native/library-path sha256))))))

(defn setup!
  "Install the libhegel release pinned by hegel.version."
  []
  (verify-source-version!)
  {:libhegel (fetch-libhegel!)})

(defn- print-paths! []
  (verify-source-version!)
  (println "project-root:" native/project-root-path)
  (println "cache-directory:" native/cache-directory-path)
  (println "libhegel:" native/library-path)
  nil)

(defn- print-version! []
  (println (verify-source-version!))
  nil)

(defn- usage! []
  (println
   (str "Usage: " (name (host/runtime)) " -m hegel.install "
        "[setup|fetch-libhegel|verify-source|paths|version]"))
  nil)

(defn -main [& arguments]
  (case (first arguments)
    (nil "setup") (setup!)
    "fetch-libhegel" (fetch-libhegel!)
    "verify-source" (println (verify-source-version!))
    "paths" (print-paths!)
    "version" (print-version!)
    (do
      (usage!)
      (throw
       (ex-info (str "unknown hegel.install command "
                     (pr-str (first arguments)))
                {:type ::unknown-command
                 :command (first arguments)}))))
  nil)
