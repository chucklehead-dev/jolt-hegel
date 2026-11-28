(ns validate-history
  (:require [clojure.edn :as edn]
            [hegel.stateful.history :as history]))

;; These inputs are local CI fixtures. The consumer separately checks the digest.
(defn -main [old-file new-file]
  (let [read-value (fn [file] (-> file slurp edn/read-string :payload edn/read-string :values first))
        old (read-value old-file)
        new (read-value new-file)]
    (assert (= (:events old) (:events (history/upgrade-v0-history old))))
    (assert (= new (history/validate-history! new)))
    (doseq [bad [old (assoc new :schema-version 99)
                 (update new :rules conj (first (:rules new)))
                 (assoc-in new [:events 0 :rule] "unknown")
                 (assoc-in new [:events 0 :extra] "unexpected")
                 (assoc new :max-bytes 2) (assoc new :max-entries 1)]]
      (assert (try (history/validate-history! bad) false
                   (catch clojure.lang.ExceptionInfo e (:hegel/usage-error? (ex-data e))))))
    (assert (nil? (find-ns 'hegel.ffi)))
    (println "PASS engine-free history validation and explicit v0.7.0 migration")))
