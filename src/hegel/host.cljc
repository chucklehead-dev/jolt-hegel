(ns hegel.host
  "Small host seam for resources and process identity."
  (:require [clojure.string :as str]
            #?(:jolt [clojure.java.io :as io])
            #?(:jolt [jolt.host :as jolt-host]
               :bb [clojure.java.io :as io]
               :jank [hegel.host.jank-host :as jank-host]
               :clj [clojure.java.io :as io])))

(defn runtime []
  #?(:cljr :clr
     :jolt :jolt
     :bb :bb
     :jank :jank
     :clj :jvm))

(defmacro try-catch-all
  "Evaluate `try-form`, catching the broad throwable type for the active host.

  Catch types are compiler syntax and cannot be selected through a runtime
  value. This macro keeps that target distinction in the host seam while
  expanding to an ordinary zero-overhead `try`/`catch`. `try-form` is exactly
  one protected form; use `do` or `let` when it contains multiple steps."
  [try-form binding & catch-forms]
  (list 'try
        try-form
        (list* 'catch
               #?(:cljr 'System.Exception
                  :jank 'cpp/jank.runtime.object_ref
                  :default 'Throwable)
               binding
               catch-forms)))

(defn getenv [name]
  #?(:cljr (System.Environment/GetEnvironmentVariable name)
     :jank (jank-host/getenv name)
     :default (System/getenv name)))

(defn current-directory []
  #?(:cljr (System.IO.Directory/GetCurrentDirectory)
     :jank (jank-host/current-directory)
     :default (System/getProperty "user.dir")))

(defn absolute-path?
  "Recognize POSIX-rooted, Windows-rooted/UNC, and drive-rooted paths
  independently of the current host operating system."
  [path]
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

(defn parent-path
  "Return the lexical parent before the final slash or backslash, or nil."
  [path]
  (let [index (max (or (str/last-index-of path "/") -1)
                   (or (str/last-index-of path "\\") -1))]
    (cond
      (neg? index) nil
      (zero? index) (subs path 0 1)
      ;; Retain the separator for both a drive root and the direct child of
      ;; one: C:/src -> C:/ and C:\src -> C:\.
      (and (= 2 index)
           (= \: (nth path 1))
           (or (= \/ (nth path 2))
               (= \\ (nth path 2))))
      (subs path 0 (inc index))
      ;; UNC shares are roots, not children of their server names.
      (and (str/starts-with? path "\\\\")
           (= index (str/index-of path "\\" 2)))
      path
      :else (subs path 0 index))))

(defn join-path
  "Join a parent and relative child while preserving the parent's separator
  style. An empty component is an identity."
  [parent child]
  (cond
    (empty? parent) child
    (empty? child) parent
    :else
    (str parent
         (when-not (or (str/ends-with? parent "/")
                       (str/ends-with? parent "\\"))
           (if (str/includes? parent "\\") \\ \/))
         child)))

(defn os-name []
  #?(:cljr (cond
             (System.OperatingSystem/IsWindows) "Windows"
             (System.OperatingSystem/IsLinux) "Linux"
             (System.OperatingSystem/IsMacOS) "macOS"
             :else (System.Runtime.InteropServices.RuntimeInformation/OSDescription))
     :jank (jank-host/os-name)
     :default (System/getProperty "os.name")))

(defn nano-time []
  #?(:cljr (* 1000000 (System.Environment/TickCount64))
     :jank (jank-host/nano-time)
     :default (System/nanoTime)))

(defn current-time-millis []
  #?(:cljr (.ToUnixTimeMilliseconds (System.DateTimeOffset/UtcNow))
     :jank (jank-host/current-time-millis)
     :default (System/currentTimeMillis)))

#?(:jolt
   (defn- jolt-resource-path [resource-name]
     (some (fn [root]
             (let [direct (join-path root resource-name)
                   nested (join-path (join-path root "resources") resource-name)]
               (cond
                 (.isFile (java.io.File. direct)) direct
                 (.isFile (java.io.File. nested)) nested
                 :else nil)))
           (jolt-host/source-roots))))

(defn- resource-text* [resource-name resolver fallback]
  (if-let [resource (resolver resource-name)]
    (slurp resource)
    (if-let [path (and fallback (fallback resource-name))]
      (slurp path)
      (throw (ex-info (str "resource not found: " resource-name)
                      {:type ::resource-not-found
                       :resource resource-name})))))

(defn resource-text
  "Read a classpath/source-root resource as text, or throw without loading any
  native library."
  [resource-name]
  #?(:cljr
     (if-let [resource (clojure.lang.RT/FindFile resource-name)]
       (slurp (.FullName resource))
       (throw (ex-info (str "resource not found: " resource-name)
                       {:type ::resource-not-found
                        :resource resource-name})))

     :jolt
     (resource-text* resource-name io/resource jolt-resource-path)

     :bb
     (resource-text* resource-name io/resource nil)

     :jank
     (throw (ex-info (str "resource lookup is not available on jank: " resource-name)
                     {:type ::resource-not-found
                      :resource resource-name}))

     :clj
     (resource-text* resource-name io/resource nil)))
