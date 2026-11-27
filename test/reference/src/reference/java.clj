(ns reference.java
  (:require [hegel-clj.core :as h]
            [hegel-clj.generator :as g]
            [reference.contracts :as contracts])
  (:import (dev.hegel Database Hegel Reporter RunStatus Settings Verbosity)))

(defn- replay [result property]
  ;; hegel-clj 0.1.1 omits :reproduce-failure when constructing Settings.
  (let [blob (:reproduce-blob (first (:failures result)))
        settings (-> (Settings.) (.database (Database/disabled))
                     (.verbosity Verbosity/QUIET) (.reproduceFailure blob))
        report (Hegel/run (fn [tc] (binding [h/*test-case* tc] (property tc)))
                          settings (Reporter/silent))]
    (assert (= RunStatus/FAILED (.status report)))
    (mapv h/failure->map (.failures report))))

(defn -main [& _]
  (contracts/check!
   {:integer g/long :set-gen g/set :draw h/draw! :assume h/assume!
    :run #(h/test-fn! (assoc contracts/opts :database (Database/disabled)) %)
    :invalid-count #(get-in % [:statistics :invalid]) :replay replay}))
