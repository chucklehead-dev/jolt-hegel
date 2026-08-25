(ns hegel.host
  "Small host seam for resources and process identity."
  (:require [clojure.string :as str]
            #?(:jolt [jolt.host :as jolt-host]
               :bb [clojure.java.io :as io]
               :jank [hegel.host.jank-host :as jank-host]
               :clj [clojure.java.io :as io])))

(defn runtime []
  #?(:jolt :jolt
     :bb :bb
     :jank :jank
     :clj :jvm))

(defn getenv [name]
  #?(:jank (jank-host/getenv name)
     :default (System/getenv name)))

(defn current-directory []
  #?(:jank (jank-host/current-directory)
     :default (System/getProperty "user.dir")))

(defn absolute-path?
  #?(:jank ([path] (jank-host/absolute-path? path))
     :default ([_] nil)))

(defn parent-path
  #?(:jank ([path] (jank-host/parent-path path))
     :default ([_] nil)))

(defn join-path [parent child]
  #?(:jank
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
  #?(:jank (jank-host/os-name)
     :default (System/getProperty "os.name")))

(defn nano-time []
  #?(:jank (jank-host/nano-time)
     :default (System/nanoTime)))

(defn current-time-millis []
  #?(:jank (jank-host/current-time-millis)
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
  #?(:jolt
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
