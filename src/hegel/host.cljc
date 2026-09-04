(ns hegel.host
  "Small host seam for resources and process identity."
  (:require [clojure.string :as str]
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
  #?(:cljr ([path] (System.IO.Path/IsPathFullyQualified path))
     :jank ([path] (jank-host/absolute-path? path))
     :default ([_] nil)))

(defn parent-path
  #?(:cljr ([path] (System.IO.Path/GetDirectoryName path))
     :jank ([path] (jank-host/parent-path path))
     :default ([_] nil)))

(defn join-path [parent child]
  #?(:cljr
     (System.IO.Path/Combine parent child)
     :jank
     (jank-host/join-path parent child)
     :default
     (str parent
          (when-not (or (str/ends-with? parent "/")
                        (str/ends-with? parent "\\"))
            (if (str/includes? parent "\\") "\\" "/"))
          child)))

(defn join-native-path [parent child]
  (join-path parent child))

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
     (if-let [path (jolt-resource-path resource-name)]
       (slurp path)
       (throw (ex-info (str "resource not found: " resource-name)
                       {:type ::resource-not-found
                        :resource resource-name})))

     :bb
     (if-let [resource (io/resource resource-name)]
       (slurp resource)
       (throw (ex-info (str "resource not found: " resource-name)
                       {:type ::resource-not-found
                        :resource resource-name})))

     :jank
     (throw (ex-info (str "resource lookup is not available on jank: " resource-name)
                     {:type ::resource-not-found
                      :resource resource-name}))

     :clj
     (if-let [resource (io/resource resource-name)]
       (slurp resource)
       (throw (ex-info (str "resource not found: " resource-name)
                       {:type ::resource-not-found
                        :resource resource-name})))))
