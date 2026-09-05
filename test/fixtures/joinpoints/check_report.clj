(require '[clojure.edn :as edn])

(defn check-report! [report]
  (let [expected [[:hegel.core/run-test :test/property-run 'hegel.core/run-test! 2]
                [:hegel.stateful/run :test/state-machine-run 'hegel.stateful/run! 1]]]
  (assert (= 1 (:schema report)))
  (assert (= "jolt.aspect-ir/v1" (:weaver report)))
  (assert (false? (:control-enabled? report)))
  (assert (= 2 (count (:aspects report))))
  (doseq [[aspect [id role entry arity]] (map vector (:aspects report) expected)]
    (assert (= id (:id aspect)))
    (assert (= role (:advice-role aspect)))
    (assert (= {:entry entry :arity arity} (:match aspect)))
    (assert (= {:id 'chucklehead-dev/jolt-hegel
                :version "86a70acf7880184707a77069b60b2e8fd4acbbbb"}
               (:library aspect)))
    (assert (= "META-INF/jolt/aspects/hegel.edn" (:resource aspect)))
    (assert (= [{:advice 'fixture.provider/around :contract :args-v1 :ordinal 1
                :provider 'fixture.provider/aspect-provider :roles :all
                :selection-ordinal 1}]
               (:consumers aspect)))
    (assert (= 1 (count (:sites aspect))))
    (let [site (first (:sites aspect))]
      (assert (= id (:aspect site)))
      (assert (= entry (:entry site)))
      (assert (= arity (:arity site)))
      (assert (and (string? (:site-id site)) (seq (:site-id site))))))))

(let [report (edn/read-string (slurp (first *command-line-args*)))
      mutants [(assoc report :schema 0)
               (assoc report :aspects [])
               (assoc-in report [:aspects 0 :id] :wrong/id)
               (assoc-in report [:aspects 0 :advice-role] :wrong/role)
               (assoc-in report [:aspects 0 :match :arity] 3)
               (assoc-in report [:aspects 0 :library :version] "stale")
               (assoc-in report [:aspects 0 :sites] [])
               (update-in report [:aspects 1 :sites] #(into % %))
               (assoc-in report [:aspects 1 :sites 0 :entry] 'wrong/entry)
               (assoc-in report [:aspects 1 :consumers] [])]]
  (check-report! report)
  (doseq [[index mutant] (map-indexed vector mutants)]
    (assert (try (check-report! mutant) false
                 (catch AssertionError _ true))
            (str "report validator accepted mutant " index)))
  (check-report! report)
  (println "exact compiler report and 10 rejection controls passed"))
