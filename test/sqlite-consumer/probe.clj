(ns probe
  (:require [clojure.string :as str]
            [hegel.core :as h] [hegel.stateful :as hs]
            [hegel.stateful.journal :as journal]
            [hegel.replay-bundle.codec :as codec]
            [hegel.version :as version])
  (:import (java.sql DriverManager)))

(def opts {:name "sqlite/revise" :database "" :seed 7 :test-cases 100
           :stateful-step-count 8 :verbosity :quiet})

(defn- property [opens closes journal-opts]
  (fn [_]
    (let [c (DriverManager/getConnection "jdbc:sqlite::memory:")]
      (swap! opens inc)
      (try
        (with-open [s (.createStatement c)]
          (.executeUpdate s "CREATE TABLE traces (id INTEGER PRIMARY KEY, rev INTEGER)")
          (journal/run!
           (assoc journal-opts :final-check
                  (fn [expected-rev]
                    (with-open [rows (.executeQuery s "SELECT COALESCE(MAX(rev),0) FROM traces")]
                      (.next rows)
                      (when (not= (or expected-rev 0) (.getLong rows 1))
                        (throw (ex-info "revision missing in final scan" {:hegel/origin "missed-revision"}))))))
           {:initial-state nil
            :rules [(hs/rule :insert {:precondition nil?}
                             (fn [_]
                               (.executeUpdate s "INSERT INTO traces VALUES (1,0)")
                               (journal/entry! {:input {:id 1 :secret "PRIVATE-SENTINEL"} :outcome :inserted})
                               0))
                    (hs/rule :revise {:precondition some?}
                             (fn [revision]
                               ;; Planted bug: the inserted row has id 1.
                               (let [n (.executeUpdate s "UPDATE traces SET rev=rev+1 WHERE id=2")]
                                 (journal/entry! {:input {:id 2} :outcome n})
                                 (inc revision))))]}))
        (finally (.close c) (assert (.isClosed c)) (swap! closes inc))))))

(defn -main [& _]
  (Class/forName "org.sqlite.JDBC")
  (let [opens (atom 0) closes (atom 0)
        config {:redact #(update % :input dissoc :secret)}
        run-property (property opens closes config)
        result (h/run-test! opts run-property)
        failure (first (:failures result))
        events (journal/fixture failure)
        provenance {:hegel-sha (System/getenv "REFERENCE_HEGEL_SHA")
                    :libhegel-version version/libhegel-version
                    :runtime {:host :jvm :version (clojure-version)
                              :os (System/getProperty "os.name") :arch (System/getProperty "os.arch")}
                    :property-id "sqlite/revise" :generator-revision "1" :model-revision "1"}
        encoded (codec/encode (journal/to-bundle provenance result "missed-revision"))
        bundle (codec/decode encoded)
        replay (h/replay-bundle! provenance bundle run-property)
        coverage (h/run-test! (assoc opts :stateful-coverage
                                    {:census? true :rules {:tick {:min-applied 2 :min-selected 2 :min-eligible 2}
                                                           :cycle {:min-applied 1 :min-guard-rejected 1}}})
                              (fn [_] (hs/run! {:initial-state 0
                                               :rules [(hs/rule :tick inc)
                                                       (hs/rule :cycle {:precondition (constantly false)} inc)]})))]
    (assert (= [{:rule "insert" :input {:id 1} :outcome :inserted}
                {:rule "revise" :input {:id 2} :outcome 0}]
               events (get-in bundle [:trace :events]) (journal/fixture (first (:failures replay)))))
    (assert (= (hs/rule-id :revise) (:rule (last events))))
    (assert (= :reproduced (:status replay)))
    (assert (not (str/includes? encoded "PRIVATE-SENTINEL")))
    (assert (= :coverage-failed (:status coverage)))
    (assert (true? (get-in coverage [:stateful-coverage :checks :tick :passed?])))
    (assert (= 0 (get-in coverage [:stateful-coverage :checks :cycle :applied])))
    (doseq [limits [{:max-entries 1} {:max-bytes 16}]]
      (let [r (h/replay-bundle! provenance bundle (property opens closes (merge config limits)))
            f (first (:failures r))]
        (assert (= :reproduced (:status r)))
        (assert (= :incomplete-journal
                   (try (journal/fixture f) nil (catch Exception e (:reason (ex-data e))))))))
    (assert (= @opens @closes))
    (println "PASS SQLite shrink, journal, replay, coverage, redaction, bounds and cleanup" @closes)))
