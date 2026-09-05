(ns fixture.app
  (:require [fixture.provider :as provider]
            [hegel.core :as h]
            [hegel.stateful :as stateful]))

(defn- check-events! [mode]
  (let [events @provider/events]
    (if (= mode "plain")
      (assert (empty? events))
      (do
        (assert (= #{:hegel.core/run-test :hegel.stateful/run}
                   (set (map second events))))
        (assert (= [:enter :hegel.core/run-test] (first events)))
        (assert (= [:exit :hegel.core/run-test] (last events)))
        (doseq [id [:hegel.core/run-test :hegel.stateful/run]]
          (let [phases (map first (filter #(= id (second %)) events))]
            (assert (seq phases))
            (assert (every? #(= [:enter :exit] (vec %))
                            (partition-all 2 phases)))))))))

(defn -main [& [mode]]
  (assert (contains? #{nil "plain"} mode))
  (let [result (h/run-test!
                {:test-cases 1 :database "" :seed 1 :verbosity :quiet}
                (fn [_]
                  (stateful/run!
                    {:initial-state 0
                     :rules [(stateful/rule :increment inc)]
                     :invariants [(stateful/invariant :nonnegative
                                                     #(<= 0 %))]})))]
    ;; Assertions live outside advice because advice failures are fail-open.
    (assert (:passed? result))
    (check-events! mode))
  (reset! provider/events [])
  ;; Invalid stateful declarations abort rather than becoming property failures.
  ;; Both woven entry boundaries must preserve the original exception object.
  (let [seen (atom nil)
        escaped (try
                  (h/run-test!
                    {:test-cases 1 :database "" :seed 1 :verbosity :quiet}
                    (fn [_]
                      (try
                        (stateful/run! nil)
                        (catch Throwable error
                          (reset! seen error)
                          (throw error)))))
                  nil
                  (catch Throwable error error))]
    (assert (some? @seen))
    (assert (identical? @seen escaped))
    (check-events! mode))
  (println "join-point runtime control passed"))
