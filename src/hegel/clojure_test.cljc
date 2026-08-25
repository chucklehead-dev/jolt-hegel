(ns hegel.clojure-test
  "Shrinking property tests embedded in clojure.test deftests."
  (:refer-clojure :exclude [with])
  (:require [clojure.string :as str]
            [clojure.test :as ct]
            [hegel.core :as h]
            [hegel.ffi :as hffi]
            [hegel.generator :as g]))

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

(defn- nonblank-text [value]
  (let [text (when (some? value) (str value))]
    (when-not (str/blank? text)
      text)))

(defn- throwable-details [error]
  (let [throwable (try
                    (Throwable->map error)
                    (catch #?(:jank cpp/jank.runtime.object_ref :default Throwable) _
                      nil))
        error-data (ex-data error)
        wrapped-body-error? (and (= ::body-error (:type error-data))
                                 (contains? error-data ::cause-type))
        cause-data (if wrapped-body-error?
                     (::cause-data error-data)
                     error-data)
        cause-type (or (when wrapped-body-error?
                         (::cause-type error-data))
                       (some-> throwable :via last :type)
                       (class error))
        summary (or (nonblank-text (ex-message error))
                    (nonblank-text (:cause throwable))
                    (nonblank-text (some-> throwable :via last :message))
                    (nonblank-text cause-type)
                    (nonblank-text (str error))
                    "property body threw")]
    {:message (str summary
                   (when (and (not wrapped-body-error?)
                              (seq cause-data))
                     (str " ex-data: " (pr-str cause-data))))
     :type cause-type
     :data cause-data}))

(defn- evaluate-case [base final-reports body]
  (let [reports (atom [])
        outcome
        (try
          {:value
           (binding [ct/report (fn [event]
                                 (swap! reports conj event))]
             (body))}
          (catch #?(:jank cpp/jank.runtime.object_ref :default Throwable) error
            {:error error}))]
    (when (h/final?)
      (swap! final-reports into @reports))
    (if-let [error (:error outcome)]
      (if (or (:hegel/origin (ex-data error))
              (:hegel/usage-error? (ex-data error))
              (hffi/error? error)
              (hffi/stop-test? error)
              (hffi/assumption-rejected? error)
              (= :hegel.core/assumption-rejected
                 (:type (ex-data error))))
        (throw error)
        (let [details (throwable-details error)]
          (throw
           (ex-info (:message details)
                    (merge
                     {:type ::body-error
                      :hegel/origin (str base ":exception")
                      ::cause-type (:type details)}
                     (when (some? (:data details))
                       {::cause-data (:data details)}))
                    error))))
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
               (:error result) (:error result)
               (:flaky? result) :failure-did-not-reproduce
               error (:message (throwable-details error))
               :else :counterexample-found)}))

(defn- annotate-failure-seed [result event]
  (if (= :pass (:type event))
    event
    (let [message (nonblank-text (:message event))
          seed-message (str "Hegel seed: " (:seed result))]
      (assoc event :message (if message
                              (str message "; " seed-message)
                              seed-message)))))

(defn- publish-result! [reporter base result final-reports]
  (if (:passed? result)
    (reporter {:type :pass
               :message (str "Hegel property passed at " base)})
    (let [nonpassing? (some #(not= :pass (:type %)) final-reports)]
      (if nonpassing?
        (doseq [event final-reports]
          (reporter (annotate-failure-seed result event)))
        (reporter
         (annotate-failure-seed result
                                (fallback-failure-event base result))))))
  result)

(defn run-with-reports!
  "Implementation for `with`; public only so macro expansions can call it."
  [opts base body]
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
    (publish-result! reporter base result @final-reports)))

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
