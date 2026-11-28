(ns reference.java
  (:require [hegel-clj.core :as h]
            [hegel-clj.generator :as g]
            [reference.contracts :as contracts])
  (:import (dev.hegel Database Hegel Reporter RunStatus Settings Verbosity)))

(definterface StackActions
  (^void pushItem [^dev.hegel.TestCase tc])
  (^void popItem [^dev.hegel.TestCase tc])
  (^void checkStack [^dev.hegel.TestCase tc]))

(deftype StackMachine [state push-item pop-item check-stack]
  StackActions
  (^{dev.hegel.Rule {}} pushItem [_ _tc] (swap! state push-item))
  (^{dev.hegel.Rule {}} popItem [_ _tc] (swap! state pop-item))
  (^{dev.hegel.Invariant {:alwaysRun true}} checkStack [_ _tc] (check-stack @state)))

(defn- machine [initial push-item pop-item check-stack]
  (h/run-stateful! (StackMachine. (atom initial) push-item pop-item check-stack)))

(defn- replay [_id result property]
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
   {:integer g/long :set-gen g/set :vector-gen g/vector :map-gen g/map :fmap g/fmap :draw h/draw! :assume h/assume!
    :machine machine :run #(h/test-fn! (assoc contracts/opts :database (Database/disabled)) %)
    :invalid-count #(get-in % [:statistics :invalid]) :replay replay}))
