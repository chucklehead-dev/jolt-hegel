(ns hegel.report
  "Reporting helpers for framework-less Jolt property suites."
  (:refer-clojure :exclude [run!])
  (:require [hegel.host :as host]
            [hegel.validation :as validation]))

#?(:jank
   ;; CountingRunner is an associative value; jank can preserve that contract
   ;; with a map until defrecord support lands in the runtime.
   (defn ->CountingRunner [runs failures reporter]
     {:runs runs :failures failures :reporter reporter})
   :default
   (defrecord CountingRunner [runs failures reporter]))

(defn- confirmed-fixture [{:keys [origin reproduced? caveat exception]} input-key]
  (let [data (when exception (ex-data exception))]
    (when (and reproduced? (not caveat) (contains? data input-key))
      {:origin origin :input (get data input-key)})))

(defn- print-failure! [{:keys [origin exception caveat reproduced? status] :as failure}]
  (println " " (str origin (when exception (str " — " (ex-message exception)))))
  (if-let [fixture (confirmed-fixture failure :input)]
    (println "  input"
             #?(:jank (pr-str (:input fixture))
                :default (binding [*print-length* 32 *print-level* 8]
                           (pr-str (:input fixture)))))
    (println "  input unavailable:"
             (cond caveat caveat
                   (not reproduced?) (str "failure unconfirmed (" (name (or status :unknown)) ")")
                   :else "confirmed failure has no :input in its exception data"))))

(defn- default-reporter [{:keys [type description result exception]}]
  (case type
    :pass
    (println "PASS" description
             (str "(" (:valid-test-cases result) " cases)"))

    :fail
    (do
      (println "FAIL" description)
      (doseq [failure (:failures result)] (print-failure! failure))
      (when (or (:error result) (empty? (:failures result)))
        (println " " (or (:error result) "No confirmed failure was retained.")))
      (println "  seed" (:seed result)))

    :error
    (println "ERROR" description
             (or (ex-message exception) (str exception))
             (pr-str (ex-data exception)))))

(defn failure-fixtures
  "Return [{:origin ... :input ...}] for confirmed failures that carry an
  explicit input in their exception data. The input key defaults to :input.
  Preserves nil and false inputs. Choose plain EDN inputs for cross-host use."
  ([result] (failure-fixtures result :input))
  ([result input-key]
   (into []
         (keep #(confirmed-fixture % input-key))
         (:failures result))))

(defn counting-runner
  "Create an isolated runner that counts property failures without aborting.

  Pass `{:reporter f}` to receive structured :pass, :fail, and :error events
  instead of the default stdout report."
  ([]
   (counting-runner {}))
  ([opts]
   (validation/reject-unknown-keys! ::invalid-option "counting-runner options"
                                    #{:reporter} opts)
   (let [reporter (if (contains? opts :reporter)
                    (if (nil? (:reporter opts))
                      default-reporter
                      (:reporter opts))
                    default-reporter)]
     (when-not (fn? reporter)
       (validation/usage-error!
        ::invalid-option "counting-runner :reporter must be a function"
        {:reporter reporter}))
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
    (validation/usage-error!
     ::invalid-run "counting runner requires a property thunk"
     {:description description :run run}))
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
