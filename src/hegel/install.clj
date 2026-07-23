(ns hegel.install
  "Install pinned native dependencies for a jolt-hegel Git dependency.

  This namespace lives on the normal source path. Consumers activate whichever
  alias contains the dependency, for example
  `joltc -A:test -m hegel.install`; repository aliases are not inherited."
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str]
            [hegel.native :as native]
            [hegel.version :as version]
            [jolt.ffi :as ffi]))

(def ^:private libhegel-release-base
  (str "https://github.com/hegeldev/hegel-rust/releases/download/v"
       version/libhegel-version))

(def ^:private libhegel-assets
  {[:linux "amd64"]
   {:name "libhegel-linux-amd64.so"
    :sha256 "45bf1b2a8663893dfa6e4d1ef8bbe482a48483c6d9768c43655b0b6430251af2"}

   [:linux "arm64"]
   {:name "libhegel-linux-arm64.so"
    :sha256 "a41556331433abe42e9398a1e6a16310ed950e33ed83c8ceb4db002532087286"}

   [:darwin "arm64"]
   {:name "libhegel-darwin-arm64.dylib"
    :sha256 "6cd3173a4cff9b67d41e99a91bebbf85050dafa65edd5173d139844282b519e2"}

   [:windows "amd64"]
   {:name "libhegel-windows-amd64.dll"
    :sha256 "89e76699b1aa5480c647466b7eca3be1abc9ad62cc037834ea80eab029d3f94d"}

   [:windows "arm64"]
   {:name "libhegel-windows-arm64.dll"
    :sha256 "6b17646db92fa4d8a474ca86cbd78f2430cee9b4a22a3c1e7c65051ca6b7ee47"}})

;; Resolve libc's system(3) everywhere. Windows uses it because Jolt's current
;; ProcessBuilder path preflight rejects otherwise-valid C:\... executables.
(ffi/load-library)
(ffi/defcfn c-system "system" [:string] :int)
(ffi/defcfn c-sha256 "SHA256" [:pointer :size_t :pointer] :pointer)

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
  (let [exit (c-system (windows-command arguments))]
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
  (let [fetch (requiring-resolve 'jolt.mvn-http/fetch)]
    (when-not (fetch url path)
      (throw (ex-info (str "failed to download " url)
                      {:type ::download-failed
                       :url url
                       :path path})))))

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
             (ffi/load-library candidate)
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
          source (ffi/alloc (max 1 length))
          digest (ffi/alloc 32)]
      (try
        (ffi/write-array source data)
        (when (ffi/null? (c-sha256 source length digest))
          (throw
           (ex-info (str "SHA256 failed for " path)
                    {:type ::checksum-failed
                     :path path})))
        (apply str
               (map #(format "%02x" (bit-and % 0xff))
                    (seq (ffi/read-array digest 32))))
        (finally
          (ffi/free digest)
          (ffi/free source))))))

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
       (if (= :windows (:os (native/platform)))
         (windows-checksum-matches? path expected)
         (= expected (posix-sha256 path)))))

(defn- verify-file! [path expected]
  (when-not (checksum-matches? path expected)
    (throw
     (ex-info (str "SHA-256 mismatch for " path)
              {:type ::checksum-mismatch
               :expected expected
               :path path})))
  path)

(defn- expected-sha256 [sidecar]
  (or (some-> (re-find #"[0-9a-fA-F]{64}" (slurp sidecar))
              str/lower-case)
      (throw
       (ex-info (str "could not parse SHA-256 from " sidecar)
                {:type ::invalid-sidecar
                 :path sidecar}))))

(defn- cached-sha256 [sidecar]
  (when (.isFile (java.io.File. sidecar))
    (try
      (expected-sha256 sidecar)
      (catch Throwable _ nil))))

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

(defn- shim-asset-name []
  (let [{:keys [asset-os shim-asset-extension]} (native/platform)]
    (str "jolt-hegel-shim-" asset-os "-" (architecture) "."
         shim-asset-extension)))

(defn- shim-release-base []
  (or (native/nonblank-env "HEGEL_SHIM_RELEASE_BASE")
      (str "https://github.com/chucklehead-dev/jolt-hegel/releases/download/v"
           version/jolt-hegel-version)))

(defn fetch-shim!
  "Fetch and verify this jolt-hegel release's prebuilt aggregate shim."
  []
  (if (native/nonblank-env "HEGEL_SHIM_LIBRARY")
    (do
      (require-file! "HEGEL_SHIM_LIBRARY" native/shim-library-path)
      (println "hegel shim: using" native/shim-library-path
               "(HEGEL_SHIM_LIBRARY)")
      native/shim-library-path)
    (let [asset (shim-asset-name)
          base (shim-release-base)
          sidecar (str native/shim-library-path ".sha256")
          cached-expected (cached-sha256 sidecar)]
      (ensure-directory! native/cache-directory-path)
      (if (and cached-expected
               (checksum-matches? native/shim-library-path cached-expected))
        (do
          (println "hegel shim: already verified" native/shim-library-path)
          native/shim-library-path)
        (do
          (download! (str base "/" asset ".sha256") sidecar)
          (let [expected (expected-sha256 sidecar)]
            (if (checksum-matches? native/shim-library-path expected)
              (do
                (println "hegel shim: already verified"
                         native/shim-library-path "(sha256" expected ")")
                native/shim-library-path)
              (download-verified! (str base "/" asset)
                                  native/shim-library-path expected))))))))

(defn- compiler-platform []
  (case (:os (native/platform))
    :windows
    {:compile-flags ["-shared" "-static-libgcc" "-Wl,--no-undefined"]}

    :linux
    {:compile-flags ["-fPIC" "-shared" "-Wl,--no-undefined"]
     :link-flags ["-ldl"]}

    :darwin
    {:compile-flags ["-fPIC" "-dynamiclib"]}))

(defn- run-compiler [compiler arguments]
  (if (= :windows (:os (native/platform)))
    {:exit (c-system (windows-command (cons compiler arguments)))
     :out ""
     :err ""}
    (apply shell/sh compiler arguments)))

(defn build-shim!
  "Compile the shim for the current target, normally as a download fallback."
  []
  (let [{:keys [compile-flags link-flags]} (compiler-platform)
        compiler (or (native/nonblank-env "CC") "gcc")
        output native/shim-library-path
        output-directory (native/parent-path output)
        arguments (vec
                   (concat ["-std=c11" "-O2" "-Wall" "-Wextra" "-Werror"]
                           compile-flags
                           [native/shim-source-path]
                           link-flags
                           ["-o" output]))]
    (require-file! "shim source" native/shim-source-path)
    (when output-directory
      (ensure-directory! output-directory))
    (println "hegel shim: compiling" output)
    (let [{:keys [exit out err]} (run-compiler compiler arguments)]
      (when-not (zero? exit)
        (throw
         (ex-info (str "Hegel shim compiler failed with exit " exit
                       (when-not (str/blank? err) (str "\n" err)))
                  {:type ::compiler-failed
                   :compiler compiler
                   :arguments arguments
                   :exit exit
                   :stdout out
                   :stderr err}))))
    (require-file! "compiled Hegel shim" output)
    (delete-if-present! (str output ".sha256"))
    (println "hegel shim: built" output)
    output))

(defn setup!
  "Install libhegel and the shim, building the shim if no release exists yet."
  []
  (let [libhegel (fetch-libhegel!)
        shim (try
               (fetch-shim!)
               (catch Throwable cause
                 (if (= ::download-failed (:type (ex-data cause)))
                   (do
                     (println "hegel shim: prebuilt release unavailable;"
                              "falling back to a local C build")
                     (build-shim!))
                   (throw cause))))]
    {:libhegel libhegel
     :shim shim}))

(defn- print-paths! []
  (println "project-root:" native/project-root-path)
  (println "cache-directory:" native/cache-directory-path)
  (println "libhegel:" native/library-path)
  (println "shim:" native/shim-library-path)
  nil)

(defn- print-version! []
  (println version/jolt-hegel-version)
  nil)

(defn- usage! []
  (println
   (str "Usage: joltc [-A:alias] -m hegel.install "
        "[setup|fetch-libhegel|fetch-shim|build-shim|paths|version]"))
  nil)

(defn -main [& arguments]
  (case (first arguments)
    (nil "setup") (setup!)
    "fetch-libhegel" (fetch-libhegel!)
    "fetch-shim" (fetch-shim!)
    "build-shim" (build-shim!)
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
