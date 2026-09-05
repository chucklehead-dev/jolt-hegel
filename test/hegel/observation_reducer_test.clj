(ns hegel.observation-reducer-test
  (:require [clojure.test :refer [deftest is]]
            [hegel.internal.observations :as observations]))

(defn- error-data [thunk]
  (try (thunk) nil (catch clojure.lang.ExceptionInfo error (ex-data error))))

(deftest case-events-are-idempotent-and-numeric-values-are-aggregated
  (let [case-data (-> (observations/empty-case)
                      (observations/event :branch/a)
                      (observations/event :branch/a)
                      (observations/observe :size 4.5)
                      (observations/observe :size -2.0)
                      (observations/observe :size 9.0)
                      (observations/event :size))]
    (is (= #{:branch/a :size} (:events case-data)))
    (is (= {:count 3 :min -2.0 :max 9.0} (get-in case-data [:numeric :size])))
    (is (not (some #(contains? case-data %) [:samples :values :mean])))))

(deftest records-every-outcome-with-per-case-event-and-numeric-aggregates
  (let [valid (-> (observations/empty-case)
                  (observations/event :branch)
                  (observations/observe :size 5.0)
                  (observations/observe :size 1.0))
        invalid (-> (observations/empty-case)
                    (observations/event :branch)
                    (observations/observe :size 8.0))
        overrun (observations/event (observations/empty-case) :branch)
        interesting (observations/observe (observations/empty-case) :size 3.0)
        summary (-> (observations/empty-summary)
                    (observations/record-case :valid valid)
                    (observations/record-case :invalid invalid)
                    (observations/record-case :overrun overrun)
                    (observations/record-case :interesting interesting))]
    (is (= {:valid 1 :invalid 1 :overrun 1 :interesting 1} (:cases summary)))
    (is (= {:valid 1 :invalid 1 :overrun 1} (get-in summary [:events :branch])))
    (is (= {:valid {:count 2 :min 1.0 :max 5.0}
            :invalid {:count 1 :min 8.0 :max 8.0}
            :interesting {:count 1 :min 3.0 :max 3.0}}
           (get-in summary [:numeric :size])))))

(deftest label-bound-is-a-union-across-kinds-cases-and-summary
  (let [labels (mapv #(keyword (str "label-" %)) (range 256))
        full-case (reduce observations/event (observations/empty-case) labels)
        summary (observations/record-case (observations/empty-summary)
                                          :valid full-case)
        extra :one-too-many]
    ;; A label present in both categorical and numeric forms remains one slot.
    (is (= 256 (count (into (:events full-case) (keys (:numeric full-case))))))
    (is (= 1 (:count (get-in (observations/observe full-case (first labels) 1.0)
                              [:numeric (first labels)]))))
    (is (= :hegel.observation/too-many-labels
           (:type (error-data #(observations/event full-case extra)))))
    (is (= :hegel.observation/too-many-labels
           (:type (error-data #(observations/record-case
                                summary :invalid
                                (observations/observe (observations/empty-case)
                                                      extra 2.0))))))))

(defn -main [& _]
  (let [{:keys [fail error]}
        (clojure.test/run-tests 'hegel.observation-reducer-test)]
    (when (pos? (+ fail error))
      (throw (ex-info "observation reducer tests failed"
                      {:fail fail :error error})))))
