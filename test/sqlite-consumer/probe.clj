(ns probe
  (:require [clojure.string :as str]
            [hegel.core :as h] [hegel.stateful :as hs]
            [hegel.stateful.journal :as journal]
            [hegel.replay-bundle.codec :as codec]
            [hegel.version :as version])
  (:import (java.io File) (java.nio.file Files) (java.sql DriverManager)))

(def opts {:name "sqlite/revise" :database "" :seed 7 :test-cases 100
           :stateful-step-count 8 :verbosity :quiet})

(defn- revision [statement]
  (with-open [rows (.executeQuery statement "SELECT rev FROM traces WHERE id=1")]
    (when (.next rows) (.getLong rows 1))))

(defn- property [opens closes journal-opts commit?]
  (fn [_]
    (let [file (File/createTempFile "hegel-revise-" ".sqlite")
          url (str "jdbc:sqlite:" (.getAbsolutePath file))]
      (swap! opens inc)
      (try
        (with-open [writer (DriverManager/getConnection url)
                    s (.createStatement writer)]
          (.executeUpdate s "CREATE TABLE traces (id INTEGER PRIMARY KEY, rev INTEGER)")
          (journal/run!
           (assoc journal-opts :final-check
                  (fn [expected]
                    (assert (= expected (revision s)) "writer must see its own updates")
                    (.close writer)
                    (with-open [reader (DriverManager/getConnection url)
                                query (.createStatement reader)]
                      (when (not= expected (revision query))
                        (throw (ex-info "revision missing after reopen" {:hegel/origin "missed-revision"}))))))
           {:initial-state nil
            :rules [(hs/rule :insert {:precondition nil?}
                             (fn [_]
                               (.executeUpdate s "INSERT INTO traces VALUES (1,0)")
                               (journal/entry! {:input {:id 1 :secret "PRIVATE-SENTINEL"} :outcome :inserted})
                               0))
                    (hs/rule :revise {:precondition some?}
                             (fn [revision]
                               (.setAutoCommit writer false)
                               (let [n (.executeUpdate s "UPDATE traces SET rev=rev+1 WHERE id=1")]
                                 (when commit? (.commit writer))
                                 (journal/entry! {:input {:id 1} :outcome n})
                                 (inc revision))))]}))
        (finally
          (Files/delete (.toPath file))
          (swap! closes inc))))))

(defn -main [& _]
  (Class/forName "org.sqlite.JDBC")
  (let [opens (atom 0) closes (atom 0)
        config {:redact #(update % :input dissoc :secret)}
        run-property (property opens closes config false)
        result (h/run-test! opts run-property)
        failure (first (:failures result))
        events (journal/fixture failure)
        provenance {:hegel-sha (System/getenv "REFERENCE_HEGEL_SHA")
                    :libhegel-version version/libhegel-version
                    :runtime {:host :jvm :version (clojure-version)
                              :os (System/getProperty "os.name") :arch (System/getProperty "os.arch")}
                    :property-id "sqlite/revise" :generator-revision "1" :model-revision "2"}
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
                {:rule "revise" :input {:id 1} :outcome 1}]
               events (get-in bundle [:trace :events]) (journal/fixture (first (:failures replay)))))
    (assert (= (hs/rule-id :revise) (:rule (last events))))
    (assert (= :reproduced (:status replay)))
    (assert (not (str/includes? encoded "PRIVATE-SENTINEL")))
    (assert (= :coverage-failed (:status coverage)))
    (assert (true? (get-in coverage [:stateful-coverage :checks :tick :passed?])))
    (assert (= 0 (get-in coverage [:stateful-coverage :checks :cycle :applied])))
    (doseq [limits [{:max-entries 1} {:max-bytes 16}]]
      (let [r (h/replay-bundle! provenance bundle (property opens closes (merge config limits) false))
            f (first (:failures r))]
        (assert (= :reproduced (:status r)))
        (assert (= :incomplete-journal
                   (try (journal/fixture f) nil (catch Exception e (:reason (ex-data e))))))))
    (assert (:passed? (h/run-test! opts (property opens closes config true))))
    (assert (= @opens @closes))
    (println "PASS SQLite shrink, journal, replay, coverage, redaction, bounds and cleanup" @closes)))
