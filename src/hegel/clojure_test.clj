(ns hegel.clojure-test
  "Shrinking property tests embedded in clojure.test deftests."
  (:refer-clojure :exclude [with])
  (:require [clojure.test :as ct]
            [hegel.core :as h]
            [hegel.generator :as g]))

(def ^:private report-lock
  ;; clojure.test/report is not dynamic on Jolt. with-redefs is process-global,
  ;; so serialize property runs which replace it.
  (atom nil))

(defn pass?
  "True when every captured clojure.test event is a passing assertion."
  [reports]
  (every? #(= :pass (:type %)) reports))

(defn- first-nonpassing [reports]
  (first
   (keep-indexed (fn [index report]
                   (when-not (= :pass (:type report))
                     [index report]))
                 reports)))

(defn- report-origin [base reports]
  (let [[index report] (first-nonpassing reports)
        assertion (cond
                    (contains? report :expected) (:expected report)
                    (contains? report :form) (:form report)
                    :else (symbol (str "assertion-" index)))]
    ;; expected/form is quoted assertion syntax in clojure.test. Never include
    ;; :actual or :message: both may contain generated values and would split
    ;; one bug into a new Hegel origin for every draw.
    (str base ":" (pr-str assertion))))

(defn- evaluate-case [base final-reports body]
  (let [reports (atom [])
        outcome
        (try
          {:value
           (with-redefs [ct/report (fn [event]
                                     (swap! reports conj event))]
             (body))}
          (catch Throwable error
            {:error error}))]
    (when (h/final?)
      (swap! final-reports into @reports))
    (if-let [error (:error outcome)]
      (if (:hegel/origin (ex-data error))
        (throw error)
        (throw
         (ex-info (or (ex-message error) "property body threw")
                  {:type ::body-error
                   :hegel/origin (str base ":exception")}
                  error)))
      (if (pass? @reports)
        (:value outcome)
        (throw
         (ex-info "clojure.test assertion failed"
                  {:type ::assertion-failed
                   :hegel/origin (report-origin base @reports)
                   :reports @reports}))))))

(defn- fallback-failure-event [base result]
  (let [failure (first (:failures result))
        error (:exception failure)]
    {:type :fail
     :message (str "Hegel property failed at " base)
     :expected 'property-to-pass
     :actual (cond
               (:flaky? result) :failure-did-not-reproduce
               error (or (ex-message error) (str error))
               :else :counterexample-found)}))

(defn- publish-result! [reporter base result final-reports]
  (if (:passed? result)
    (reporter {:type :pass
               :message (str "Hegel property passed at " base)})
    (let [nonpassing? (some #(not= :pass (:type %)) final-reports)]
      (if nonpassing?
        (doseq [event final-reports]
          (reporter event))
        (reporter (fallback-failure-event base result)))))
  result)

(defn run-with-reports!
  "Implementation for `with`; public only so macro expansions can call it."
  [opts base body]
  (locking report-lock
    (let [reporter ct/report
          final-reports (atom [])
          opts (if (or (contains? opts :name)
                       (contains? opts :database-key))
                 opts
                 (assoc opts :name base))
          result (h/run-test!
                  opts
                  (fn [_]
                    (evaluate-case base final-reports body)))]
      (publish-result! reporter base result @final-reports))))

(defmacro with
  "Run a shrinking Hegel property inside a clojure.test deftest.

  bindings use `hegel.generator/let`: tagged generator expressions are drawn,
  while ordinary dependent expressions are evaluated normally. Only the final
  shrunk assertion reports are sent to clojure.test."
  [opts bindings & body]
  (let [line (or (:line (meta &form)) 0)
        file (if (= *file* "NO_SOURCE_PATH")
               (str (ns-name *ns*))
               *file*)
        base (str file ":" line)]
    `(hegel.clojure-test/run-with-reports!
      ~opts
      ~base
      (fn []
        (hegel.generator/let ~bindings
          ~@body)))))
