(ns fixture.app
  (:require [fixture.provider :as provider]
            [hegel.core :as h]
            [hegel.stateful :as stateful]))

(defn assert-elision-probe!
  "Called only by the assertions-disabled source-loading control, never AOT."
  []
  (assert false "fixture assertions were not elided during source loading"))

(defn- reject! [check expected actual]
  (throw (ex-info "join-point runtime control failed"
                  {:type ::runtime-control-failed
                   :check check
                   :expected expected
                   :actual actual})))

(defn- require= [check expected actual]
  (when-not (= expected actual)
    (reject! check expected actual)))

(defn- require! [check value]
  (when-not value
    (reject! check true value)))

(defn- check-events! [mode]
  (let [events @provider/events]
    (if (= mode "plain")
      (require= :plain-events [] events)
      (do
        (require= :woven-aspects #{:hegel.core/run-test :hegel.stateful/run}
                  (set (map second events)))
        (require= :woven-first-event [:enter :hegel.core/run-test] (first events))
        (require= :woven-last-event [:exit :hegel.core/run-test] (last events))
        (doseq [id [:hegel.core/run-test :hegel.stateful/run]]
          (let [phases (map first (filter #(= id (second %)) events))]
            (require! [:woven-events id] (seq phases))
            (require! [:woven-event-pairs id]
                      (every? #(= [:enter :exit] (vec %))
                              (partition-all 2 phases)))))))))

(defn- expect-runtime-rejection! [check thunk]
  (let [outcome (try
                  (thunk)
                  :accepted
                  (catch clojure.lang.ExceptionInfo error
                    (if (= ::runtime-control-failed (:type (ex-data error)))
                      :rejected
                      (throw error))))]
    (require= check :rejected outcome)))

(defn- check-malformed-events! [mode]
  (let [saved @provider/events]
    (try
      (reset! provider/events [[:enter :wrong/join-point]])
      (expect-runtime-rejection! :malformed-event-journal
                                  #(check-events! mode))
      (finally
        (reset! provider/events saved)))))

(defn -main [& [mode]]
  (require! :mode (contains? #{nil "plain"} mode))
  (let [result (h/run-test!
                {:test-cases 1 :database "" :seed 1 :verbosity :quiet}
                (fn [_]
                  (stateful/run!
                    {:initial-state 0
                     :rules [(stateful/rule :increment inc)]
                     :invariants [(stateful/invariant :nonnegative
                                                     #(<= 0 %))]})))]
    ;; Verdict checks live outside advice because advice failures are fail-open.
    (require! :property-passed? (:passed? result))
    (check-events! mode)
    (check-malformed-events! mode))
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
    (require! :invalid-stateful-threw? (some? @seen))
    (require! :woven-exception-identity (identical? @seen escaped))
    (check-events! mode))
  (println "join-point runtime control passed"))
