(ns hegel.report
  "Reporting helpers for framework-less Jolt property suites."
  (:refer-clojure :exclude [run!])
  (:require [hegel.host :as host]))

#?(:jank
   ;; CountingRunner is an associative value; jank can preserve that contract
   ;; with a map until defrecord support lands in the runtime.
   (defn ->CountingRunner [runs failures reporter]
     {:runs runs :failures failures :reporter reporter})
   :default
   (defrecord CountingRunner [runs failures reporter]))

(defn- default-reporter [{:keys [type description result exception]}]
  (case type
    :pass
    (println "PASS" description
             (str "(" (:valid-test-cases result) " cases)"))

    :fail
    (println "FAIL" description
             (pr-str
              (select-keys result
                           [:status :seed :n-failures :failures
                            :observed-failures :flaky? :error])))

    :error
    (println "ERROR" description
             (or (ex-message exception) (str exception))
             (pr-str (ex-data exception)))))

(defn counting-runner
  "Create an isolated runner that counts property failures without aborting.

  Pass `{:reporter f}` to receive structured :pass, :fail, and :error events
  instead of the default stdout report."
  ([]
   (counting-runner {}))
  ([opts]
   (let [reporter (or (:reporter opts) default-reporter)]
     (when-not (fn? reporter)
       (throw
        (ex-info "counting-runner :reporter must be a function"
                 {:type ::invalid-option
                  :reporter reporter})))
     (->CountingRunner (atom 0) (atom 0) reporter))))

(defn run-count
  "Return the number of property thunks submitted to runner."
  [runner]
  @(:runs runner))

(defn failure-count
  "Return the number of failed results and thrown run errors."
  [runner]
  @(:failures runner))

(defn passed?
  "True when runner has not recorded a failure or run error."
  [runner]
  (zero? (failure-count runner)))

(defn run!
  "Run one complete property thunk, report it, and continue after failures.

  Returned `{:passed? false}` results and thrown setup, health-check, or engine
  errors each increment the failure count. Returns the property result, or nil
  when the thunk threw."
  [runner description run]
  (when-not (fn? run)
    (throw
     (ex-info "counting runner requires a property thunk"
              {:type ::invalid-run
               :description description
               :run run})))
  (swap! (:runs runner) inc)
  (let [outcome (host/try-catch-all
                 {:result (run)}
                 error
                 {:exception error})]
    (if-let [error (:exception outcome)]
      (do
        (swap! (:failures runner) inc)
        ((:reporter runner)
         {:type :error
          :description description
          :exception error})
        nil)
      (let [result (:result outcome)
            type (if (:passed? result) :pass :fail)]
        (when (= :fail type)
          (swap! (:failures runner) inc))
        ((:reporter runner)
         {:type type
          :description description
          :result result})
        result))))
