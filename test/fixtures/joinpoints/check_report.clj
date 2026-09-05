(ns check-report
  (:require [clojure.edn :as edn]))

(defn- invalid-report! [check expected actual]
  (throw (ex-info "invalid compiler aspect report"
                  {:type ::invalid-report
                   :check check
                   :expected expected
                   :actual actual})))

(defn- require= [check expected actual]
  (when-not (= expected actual)
    (invalid-report! check expected actual)))

(defn- require! [check value]
  (when-not value
    (invalid-report! check true value)))

(defn check-report! [report]
  (let [expected [[:hegel.core/run-test :test/property-run 'hegel.core/run-test! 2]
                [:hegel.stateful/run :test/state-machine-run 'hegel.stateful/run! 1]]]
    (require= :schema 1 (:schema report))
    (require= :weaver "jolt.aspect-ir/v1" (:weaver report))
    (require= :control-enabled? false (:control-enabled? report))
    (require= :aspect-count 2 (count (:aspects report)))
    (doseq [[aspect [id role entry arity]] (map vector (:aspects report) expected)]
      (require= [:aspect id :id] id (:id aspect))
      (require= [:aspect id :advice-role] role (:advice-role aspect))
      (require= [:aspect id :match] {:entry entry :arity arity} (:match aspect))
      (require= [:aspect id :library]
                {:id 'chucklehead-dev/jolt-hegel
                 :version "86a70acf7880184707a77069b60b2e8fd4acbbbb"}
                (:library aspect))
      (require= [:aspect id :resource] "META-INF/jolt/aspects/hegel.edn" (:resource aspect))
      (require= [:aspect id :consumers]
                [{:advice 'fixture.provider/around :contract :args-v1 :ordinal 1
                  :provider 'fixture.provider/aspect-provider :roles :all
                  :selection-ordinal 1}]
                (:consumers aspect))
      (require= [:aspect id :site-count] 1 (count (:sites aspect)))
      (let [site (first (:sites aspect))]
        (require= [:aspect id :site :aspect] id (:aspect site))
        (require= [:aspect id :site :entry] entry (:entry site))
        (require= [:aspect id :site :arity] arity (:arity site))
        (require! [:aspect id :site :site-id]
                  (and (string? (:site-id site)) (seq (:site-id site))))))))

(defn report-mutants [report]
  [(assoc report :schema 0)
   (assoc report :aspects [])
   (assoc-in report [:aspects 0 :id] :wrong/id)
   (assoc-in report [:aspects 0 :advice-role] :wrong/role)
   (assoc-in report [:aspects 0 :match :arity] 3)
   (assoc-in report [:aspects 0 :library :version] "stale")
   (assoc-in report [:aspects 0 :sites] [])
   (update-in report [:aspects 1 :sites] #(into % %))
   (assoc-in report [:aspects 1 :sites 0 :entry] 'wrong/entry)
   (assoc-in report [:aspects 1 :consumers] [])])

(defn check-mutants! [report]
  (doseq [[index mutant] (map-indexed vector (report-mutants report))]
    (try
      (check-report! mutant)
      (throw (ex-info "report validator accepted mutant" {:mutant index}))
      (catch clojure.lang.ExceptionInfo error
        (when-not (= ::invalid-report (:type (ex-data error)))
          (throw error))))))

(defn check-file! [path]
  (let [report (edn/read-string (slurp path))]
    (check-report! report)
    (check-mutants! report)
    (check-report! report)
    (println "exact compiler report and 10 rejection controls passed")))
