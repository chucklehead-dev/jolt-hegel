(ns hegel.native
  "Shared target and path resolution for libhegel and the aggregate shim."
  (:require [clojure.string :as str]
            [jolt.host :as host]))

(defn nonblank-env
  "Return an environment value only when it contains non-whitespace text."
  [name]
  (let [value (System/getenv name)]
    (when-not (str/blank? value)
      value)))

(defn- last-separator-index [path]
  (max (or (str/last-index-of path "/") -1)
       (or (str/last-index-of path "\\") -1)))

(defn parent-path [path]
  (let [index (last-separator-index path)]
    (when (pos? index)
      (subs path 0 index))))

(defn join-path [parent child]
  (str parent
       (when-not (or (str/ends-with? parent "/")
                     (str/ends-with? parent "\\"))
         (if (str/includes? parent "\\") "\\" "/"))
       child))

(defn absolute-path? [path]
  (or (str/starts-with? path "/")
      (str/starts-with? path "\\")
      (and (<= 3 (count path))
           (= \: (nth path 1))
           (let [drive (nth path 0)
                 separator (nth path 2)]
             (and (str/includes?
                   "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
                   (str drive))
                  (or (= \/ separator)
                      (= \\ separator)))))))

(defn- resolve-from-launch-directory [path]
  (let [launch-dir (nonblank-env "JOLT_PWD")]
    (cond
      (absolute-path? path) path
      (not (str/blank? launch-dir)) (join-path launch-dir path)
      :else (.getAbsolutePath (java.io.File. path)))))

(defn- dependency-source-root []
  ;; *file* is not a reliable dependency-root coordinate after Jolt loads a namespace from its AOT
  ;; cache: its metadata can name the checkout that first populated the cache.
  ;; Current source roots are resolved for every launch, so locate this
  ;; namespace in those roots instead.
  (some (fn [root]
          (when (.isFile
                 (java.io.File. (join-path root "hegel/native.clj")))
            root))
        (host/source-roots)))

(def project-root-path
  (if-let [override (nonblank-env "HEGEL_SHIM_PROJECT_ROOT")]
    (resolve-from-launch-directory override)
    ;; Jolt's source-root resolver already anchors dependency roots to the
    ;; launch directory. On Windows, resolving that rooted value a second time
    ;; can prefix the launch directory again before java.io.File sees it.
    (or (some-> (dependency-source-root) parent-path)
        (resolve-from-launch-directory "."))))

(defn platform
  "Return the supported native target description for the current process."
  []
  (let [name (str/lower-case (or (System/getProperty "os.name") ""))]
    (cond
      (str/includes? name "windows")
      {:os :windows
       :asset-os "windows"
       :library-name "libhegel_c.dll"
       :library-extension "dll"
       :shim-name "jolt_hegel_shim.dll"
       :shim-asset-extension "dll"}

      (str/includes? name "linux")
      {:os :linux
       :asset-os "linux"
       :library-name "libhegel_c.so"
       :library-extension "so"
       :shim-name "libjolt_hegel_shim.so"
       :shim-asset-extension "so"}

      (or (str/includes? name "mac") (str/includes? name "darwin"))
      {:os :darwin
       :asset-os "darwin"
       :library-name "libhegel_c.dylib"
       :library-extension "dylib"
       :shim-name "libjolt_hegel_shim.dylib"
       :shim-asset-extension "dylib"}

      :else
      (throw
       (ex-info
        (str "jolt-hegel has no native mapping for operating system "
             (pr-str (System/getProperty "os.name")))
        {:type ::unsupported-platform})))))

(def cache-directory-path
  "Writable native cache. HEGEL_CACHE_DIR may be relative to the launch dir."
  (if-let [override (nonblank-env "HEGEL_CACHE_DIR")]
    (resolve-from-launch-directory override)
    (join-path project-root-path ".hegel-lib")))

(def library-path
  "The libhegel shared library selected for this process."
  (or (some-> (nonblank-env "HEGEL_LIBHEGEL_LIBRARY")
              resolve-from-launch-directory)
      (join-path cache-directory-path (:library-name (platform)))))

(def shim-library-path
  "The jolt-hegel aggregate shim selected for this process."
  (or (some-> (nonblank-env "HEGEL_SHIM_LIBRARY")
              resolve-from-launch-directory)
      (join-path cache-directory-path (:shim-name (platform)))))

(def shim-source-path
  "The C source used by the local-build fallback."
  (join-path (join-path project-root-path "native") "hegel_shim.c"))
