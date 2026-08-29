(ns hegel.install
  "Install pinned native dependencies for a jolt-hegel Git dependency.

  This namespace lives on the normal source path. Consumers activate whichever
  alias contains the dependency, for example
  `jolt -A:test -m hegel.install`; repository aliases are not inherited."
  (:require [clojure.string :as str]
            [hegel.host :as host]
            [hegel.install.backend :as install-backend]
            [hegel.native :as native]
            [hegel.version :as version]))

(def ^:private libhegel-release-base
  (str "https://github.com/hegeldev/hegel-rust/releases/download/v"
       version/libhegel-version))

(def ^:private libhegel-assets
  {[:linux "amd64"]
   {:name "libhegel-linux-amd64.so"
    :sha256 "1ceb1636f3dd8e939fef88e99e3417b9da23675c7847e4cb22717ca8834c699b"}

   [:linux "arm64"]
   {:name "libhegel-linux-arm64.so"
    :sha256 "1476414c1912daa9611f3f286200f331c78efb88faefea5a75d8e296d63e03e3"}

   [:darwin "arm64"]
   {:name "libhegel-darwin-arm64.dylib"
    :sha256 "ccc239550a039fdd684609f3d1ee0c64532f0efacda43e1fe78cd4fce0c9abf7"}

   [:windows "amd64"]
   {:name "libhegel-windows-amd64.dll"
    :sha256 "bf399f9448187cef936e73301bd4f1def5e0c868372c7faa1573371e6322ed88"}

   [:windows "arm64"]
   {:name "libhegel-windows-arm64.dll"
    :sha256 "0c3700fbf921b15502f991818e2ce6aa03a3adefc3ac3387b15db85ad96eb249"}})

(def ^:private jolt? (= :jolt (host/runtime)))

(defn- nonblank [value]
  (when-not (str/blank? value)
    value))

(defn- property [name]
  (nonblank (install-backend/property name)))

(defn- normalize-architecture [machine]
  (case (some-> machine str/trim str/lower-case)
    ("x86_64" "amd64" "x64") "amd64"
    ("aarch64" "arm64") "arm64"
    nil))

(defn- uname-machine []
  (nonblank (install-backend/uname-machine)))

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

(defn- path-exists? [path]
  (install-backend/path-exists? path))

(defn- directory? [path]
  (install-backend/directory? path))

(defn- regular-file? [path]
  (and (path-exists? path) (not (directory? path))))

(defn- ensure-directory! [path]
  (when-not (or (directory? path)
                (install-backend/mkdirs! path))
    (throw
     (ex-info (str "could not create " path)
              {:type ::directory-failed
               :path path})))
  path)

(defn- require-file! [description path]
  (when-not (regular-file? path)
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
               (install-backend/read-text path))]
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
  (when (and (path-exists? path)
             (not (install-backend/delete-file! path)))
    (throw
     (ex-info (str "could not remove " path)
              {:type ::delete-failed
               :path path}))))

(defn- replace-file! [source target]
  (when (path-exists? target)
    (delete-if-present! target))
  (when-not (install-backend/rename-file! source target)
    (throw
     (ex-info (str "could not move verified download to " target
                   "; verified file remains at " source)
              {:type ::replace-failed
               :source source
               :path target})))
  target)

(defn- download! [url path]
  (println "native: downloading" url)
  (delete-if-present! path)
  (try
    (install-backend/download! (:os (native/platform)) url path)
    (require-file! "download" path)
    (catch #?(:cljr System.Exception
              :jank cpp/jank.runtime.object_ref
              :default Throwable) cause
      (delete-if-present! path)
      (throw
       (ex-info (str "failed to download " url)
                {:type ::download-failed
                 :url url
                 :path path
                 :cause cause})))))

(defn- checksum-matches? [path expected]
  (and (regular-file? path)
       (install-backend/checksum-matches?
        (:os (native/platform)) path expected)))

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
      (catch #?(:cljr System.Exception
                :jank cpp/jank.runtime.object_ref
                :default Throwable) cause
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
