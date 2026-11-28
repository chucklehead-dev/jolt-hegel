(ns reference.java
  (:require [hegel-clj.core :as h]
            [hegel-clj.generator :as g]
            [reference.contracts :as contracts])
  (:import (dev.hegel Database Hegel Reporter RunStatus Settings Verbosity)))

(definterface TwoRuleActions
  (^void firstStep [^dev.hegel.TestCase tc])
  (^void secondStep [^dev.hegel.TestCase tc])
  (^void checkState [^dev.hegel.TestCase tc]))

(deftype TwoRuleMachine [state first-step second-step check-state]
  TwoRuleActions
  (^{dev.hegel.Rule {}} firstStep [_ _tc] (swap! state first-step))
  (^{dev.hegel.Rule {}} secondStep [_ _tc] (swap! state second-step))
  (^{dev.hegel.Invariant {:alwaysRun true}} checkState [_ _tc] (check-state @state)))

(defn- machine [initial first-step second-step check-state]
  (h/run-stateful! (TwoRuleMachine. (atom initial) first-step second-step check-state)))

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
