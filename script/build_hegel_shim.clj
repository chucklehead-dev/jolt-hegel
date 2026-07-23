(ns build-hegel-shim
  "Compatibility wrapper for the source-visible native installer."
  (:require [hegel.install :as install]))

(def build! install/build-shim!)

(defn -main [& _]
  (build!)
  nil)
