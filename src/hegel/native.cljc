(ns hegel.native
  "Shared target and path resolution for libhegel."
  (:require [clojure.string :as str]
            [hegel.host :as host]
            #?(:jolt [jolt.host :as jolt-host])))

(defn nonblank-env
  "Return an environment value only when it contains non-whitespace text."
  [name]
  (let [value (host/getenv name)]
    (when-not (str/blank? value)
      value)))

#?(:jank nil
   :default
   (defn- last-separator-index [path]
     (max (or (str/last-index-of path "/") -1)
          (or (str/last-index-of path "\\") -1))))

(defn parent-path [path]
  #?(:jank (host/parent-path path)
     :default
     (let [index (last-separator-index path)]
       (when (pos? index)
         (subs path 0 index)))))

(defn join-path [parent child]
  #?(:jank (host/join-native-path parent child)
     :default
     (str parent
          (when-not (or (str/ends-with? parent "/")
                        (str/ends-with? parent "\\"))
            (if (str/includes? parent "\\") "\\" "/"))
          child)))

(defn absolute-path? [path]
  #?(:jank (host/absolute-path? path)
     :default
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
                         (= \\ separator))))))))

(defn- resolve-from-launch-directory [path]
  (let [launch-dir #?(:jolt (nonblank-env "JOLT_PWD")
                      :default (host/current-directory))]
    (cond
      (absolute-path? path) path
      (not (str/blank? launch-dir)) (join-path launch-dir path)
      :else #?(:cljr (System.IO.Path/GetFullPath path)
               :jank (join-path (host/current-directory) path)
               :default (.getAbsolutePath (java.io.File. path))))))

#?(:jolt
   (defn- dependency-source-root []
     ;; *file* is not reliable after Jolt loads an AOT namespace. Resolve the
     ;; current dependency root and accept either portable or legacy suffixes.
     (some (fn [root]
             (when (or (.isFile (java.io.File. (join-path root "hegel/native.cljc")))
                       (.isFile (java.io.File. (join-path root "hegel/native.clj"))))
               root))
           (jolt-host/source-roots))))

(def project-root-path
  (if-let [override (nonblank-env "HEGEL_PROJECT_ROOT")]
    (resolve-from-launch-directory override)
    #?(:jolt (or (some-> (dependency-source-root) parent-path)
                   (resolve-from-launch-directory "."))
       :default (resolve-from-launch-directory "."))))

(defn platform
  "Return the supported native target description for the current process."
  []
  (let [name (str/lower-case (or (host/os-name) ""))]
    (cond
      (str/includes? name "windows")
      {:os :windows
       :asset-os "windows"
       :library-name "libhegel_c.dll"
       :library-extension "dll"}

      (str/includes? name "linux")
      {:os :linux
       :asset-os "linux"
       :library-name "libhegel_c.so"
       :library-extension "so"}

      (or (str/includes? name "mac") (str/includes? name "darwin"))
      {:os :darwin
       :asset-os "darwin"
       :library-name "libhegel_c.dylib"
       :library-extension "dylib"}

      :else
      (throw
       (ex-info
        (str "jolt-hegel has no native mapping for operating system "
             (pr-str (host/os-name)))
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
