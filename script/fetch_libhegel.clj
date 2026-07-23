(ns fetch-libhegel
  "Compatibility wrapper for the source-visible native installer."
  (:require [hegel.install :as install]))

(def fetch! install/fetch-libhegel!)

(defn -main [& _]
  (fetch!)
  nil)
