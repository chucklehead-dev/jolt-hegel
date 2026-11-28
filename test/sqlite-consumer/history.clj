(ns history
  (:require [hegel.core :as h]
            [hegel.corpus :as corpus]
            [hegel.generator :as g]
            [hegel.materialize :as materialize]
            [hegel.stateful :as hs]
            [hegel.stateful.journal :as journal]
            [hegel.version :as version])
  (:import (java.sql DriverManager)))

(def scenario [{:rule "insert"} {:rule "revise" :outcome :updated}
               {:rule "retry" :outcome :stale}])

(defn- candidate [_]
  (with-open [c (DriverManager/getConnection "jdbc:sqlite::memory:")
              s (.createStatement c)]
    (.execute s "CREATE TABLE traces (id INTEGER PRIMARY KEY, rev INTEGER)")
    (let [id (h/draw! (g/integer 1 100))
          write! (fn [expected]
                   (with-open [stmt (.prepareStatement c "UPDATE traces SET rev=rev+1 WHERE id=? AND rev=?")]
                     (.setInt stmt 1 id)
                     (.setInt stmt 2 expected)
                     (.executeUpdate stmt)))
          config {:initial-state :new
                  :rules [(hs/rule :insert {:precondition #(= % :new)}
                           (fn [_]
                             (.executeUpdate s (str "INSERT INTO traces VALUES (" id ",0)"))
                             (journal/entry! {:input {:id id :secret "PRIVATE"} :outcome :inserted})
                             :inserted))
                          (hs/rule :revise {:precondition #(= % :inserted)}
                           (fn [_]
                             (assert (= 1 (write! 0)))
                             (journal/entry! {:input {:id id :expected 0} :outcome :updated})
                             :updated))
                          (hs/rule :retry {:precondition #(= % :updated)}
                           (fn [_]
                             (assert (zero? (write! 0)))
                             (journal/entry! {:input {:id id :expected 0} :outcome :stale})
                             :done))]}
          history (journal/history! {:redact #(update % :input dissoc :secret)} config)]
      (h/assume! (journal/covers? history scenario))
      (with-open [rows (.executeQuery s "SELECT rev FROM traces")]
        (assert (.next rows))
        (assert (= 1 (.getInt rows 1)))
        (assert (not (.next rows))))
      history)))

(defn -main [output]
  (let [provenance {:hegel-sha (System/getenv "REFERENCE_HEGEL_SHA")
                    :libhegel-version version/libhegel-version
                    :runtime {:host :jvm :version (clojure-version)
                              :os (System/getProperty "os.name") :arch (System/getProperty "os.arch")}
                    :property-id "sqlite/stale-retry" :generator-revision "1"
                    :model-revision "1" :seam-revision "1"}
        envelope (materialize/materialize! {:count 1 :seed "7" :provenance provenance} candidate)]
    (spit output (corpus/encode envelope))))
