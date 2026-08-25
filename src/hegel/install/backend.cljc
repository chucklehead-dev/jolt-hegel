(ns hegel.install.backend
  "Selected host mechanics for the portable native installer."
  (:require #?(:jolt [hegel.install.jolt :as impl]
               :bb [hegel.install.jvm :as impl]
               :clj [hegel.install.jvm :as impl])))

(defn property [name] (impl/property name))
(defn uname-machine [] (impl/uname-machine))
(defn path-exists? [path] (impl/path-exists? path))
(defn directory? [path] (impl/directory? path))
(defn mkdirs! [path] (impl/mkdirs! path))
(defn delete-file! [path] (impl/delete-file! path))
(defn rename-file! [source target] (impl/rename-file! source target))
(defn read-text [path] (impl/read-text path))
(defn download! [os url path] (impl/download! os url path))
(defn checksum-matches? [os path expected]
  (impl/checksum-matches? os path expected))
