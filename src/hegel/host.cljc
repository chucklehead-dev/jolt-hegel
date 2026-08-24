(ns hegel.host
  "Small host seam for resources and process identity."
  (:require [clojure.string :as str]
            #?(:jolt [jolt.host :as jolt-host]
               :bb [clojure.java.io :as io]
               :clj [clojure.java.io :as io])))

(defn runtime []
  #?(:jolt :jolt
     :bb :bb
     :clj :jvm))

(defn- join-path [parent child]
  (str parent
       (when-not (or (str/ends-with? parent "/")
                     (str/ends-with? parent "\\"))
         (if (str/includes? parent "\\") "\\" "/"))
       child))

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

     :clj
     (if-let [resource (io/resource resource-name)]
       (slurp resource)
       (throw (ex-info (str "resource not found: " resource-name)
                       {:type ::resource-not-found
                        :resource resource-name})))))
