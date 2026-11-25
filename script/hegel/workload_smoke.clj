(ns hegel.workload-smoke
  "Run in a fresh process with ANTITHESIS_OUTPUT_DIR set."
  (:require [hegel.core :as h]
            [hegel.generator :as g]))

(defn -main [& _]
  (assert (System/getenv "ANTITHESIS_OUTPUT_DIR"))
  (let [result (h/run-test! {:test-cases 1 :database "" :verbosity :quiet}
                          (fn [_]
                            (h/draw! (g/integer 1 10) "n")
                            (throw (ex-info "workload failure" {}))))
        failure (first (:failures result))]
    (assert (= :failed (:status result)))
    (assert (false? (:flaky? result)))
    (assert (:unrecorded? failure))
    (assert (false? (:reproduced? failure)))
    (assert (nil? (:reproduction-blob failure)))
    (assert (= "workload failure" (ex-message (:exception failure))))
    (println "PASS unrecorded workload failure")))
