(ns pilot.runner
  (:require [clojure.edn :as edn]
            [hegel.core :as h]
            [hegel.report :as report]
            [jolt.host :as jolt-host]
            [hegel.replay-bundle :as bundle]
            [hegel.replay-bundle.codec :as codec]
            [hegel.version :as version]))

(defn require! [id condition data]
  (when-not condition
    (throw (ex-info (str "External property failed: " id)
                    (assoc data :pilot/assertion id)))))

(defn- provenance [pack id]
  {:hegel-sha (System/getenv "PILOT_HEGEL_SHA")
   :libhegel-version version/libhegel-version
   :runtime {:host :jolt :version (jolt-host/build-id)
             :os (System/getProperty "os.name") :arch (System/getProperty "os.arch")}
   :property-id (str (name pack) "/" (name id))
   :generator-revision (System/getenv "PILOT_REVISION")
   :model-revision (System/getenv "PILOT_REVISION")})

(defn- property-body [pack {:keys [id generator check labels]} mutant?]
  (fn [_]
    (let [value (h/draw! generator "input")]
      (doseq [label (when labels (labels value))] (h/event! label))
      (try (check value mutant?)
           (catch Exception error
             (throw (ex-info (ex-message error)
                             (assoc (ex-data error) :pilot/input value
                                    :hegel/origin (str (name pack) "/" (name id))) error)))))))

(defn- summary [run]
  (select-keys run [:status :passed? :flaky? :seed :test-cases :valid-test-cases
                    :invalid-test-cases :overrun-test-cases :n-failures
                    :observations :coverage :error]))

(defn- run-case! [pack {:keys [id coverage] :as property} output]
  (let [base {:profile "base" :test-cases 200 :seed 424242 :database ""
              :verbosity :quiet :print-blob? false :observations? true}
        good (h/run-test! (assoc base :phases [:generate]
                                 :coverage {:scope :exploration
                                            :requirements (into {} (map #(vector % {:min-count 1}) coverage))})
                          (property-body pack property false))]
    (require! id (:passed? good) {:kind :target-or-coverage-failure :result good})
    (let [bad (h/run-test! base (property-body pack property true))
          failure (first (:failures bad))
          data (some-> failure :exception ex-data)
          fixtures (report/failure-fixtures bad :pilot/input)
          minimum (:input (first fixtures))
          expected (provenance pack id)
          prefix (str output "/" (name id))]
      (require! id (and (= :failed (:status bad)) (false? (:flaky? bad))
                       (= id (:pilot/assertion data)) (= 1 (count fixtures)))
                {:kind :broken-control-not-detected :result bad})
      (spit (str prefix ".replay.edn") (codec/encode (bundle/from-result expected bad)))
      (spit (str prefix ".value.edn")
            (pr-str {:property id :provenance expected :input minimum
                     :assertion (dissoc data :pilot/input)}))
      (println "PASS" pack id "and its broken control; minimum" (pr-str minimum))
      {:property id :positive (summary good) :control (summary bad)
       :minimum minimum})))

(defn- replay-case! [pack {:keys [id check] :as property} output]
  (let [prefix (str output "/" (name id))
        expected (provenance pack id)
        artifact (codec/decode (slurp (str prefix ".replay.edn")))
        result (h/replay-bundle! expected artifact (property-body pack property true))
        wrong (h/replay-bundle! (assoc expected :generator-revision "deliberately-incompatible")
                                artifact (fn [_] (throw (ex-info "Mismatch executed body" {}))))
        fixture (edn/read-string (slurp (str prefix ".value.edn")))
        control-error (try (check (:input fixture) true) nil
                           (catch Exception error error))]
    (require! id (= :reproduced (:status result)) {:kind :replay-failed :result result})
    (require! id (= :incompatible (:status wrong)) {:kind :mismatch-not-rejected})
    (check (:input fixture) false)
    (require! id (= id (:pilot/assertion (ex-data control-error))) {:kind :value-fixture-failed})
    (println "PASS fresh-process replay, mismatch rejection and value fixture" pack id)
    {:property id :replay :reproduced :mismatch :incompatible :value-fixture :passed}))

(defn main! [pack properties args]
  (let [[mode output] args
        manifest (edn/read-string (slurp "../../targets.edn"))
        target (get-in manifest [:targets pack])
        deps (:deps (edn/read-string (slurp "deps.edn")))
        dependency (some #(when (= (:url target) (:git/url %)) %) (vals deps))]
    (require! :pins (and (= (:sha target) (:git/sha dependency))
                         (= (:libhegel manifest) version/libhegel-version)
                         (= (get-in manifest [:runtime :version]) (System/getProperty "jolt.version")))
              {:kind :pin-mismatch :target target :dependency dependency})
    (when-not (and output (#{"run" "replay"} mode))
      (throw (ex-info "Expected: run|replay OUTPUT-DIRECTORY" {:args args})))
    (let [results (mapv #(if (= mode "run") (run-case! pack % output) (replay-case! pack % output)) properties)]
      (spit (str output "/" mode "-report.edn")
            (pr-str {:pack pack :mode mode :hegel-sha (System/getenv "PILOT_HEGEL_SHA")
                     :source-revision (System/getenv "PILOT_REVISION") :target target
                     :results results})))))
