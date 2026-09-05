(ns hegel.corpus-contract-runner
  "Engine-free corpus checks. Run before libhegel installation in host CI."
  (:require [clojure.test :as t]
            [hegel.corpus-digest-test]
            [hegel.corpus-test]
            [hegel.host :as host]))

(defn run-checks! []
  (let [namespaces (cond-> ['hegel.corpus-digest-test 'hegel.corpus-test]
                     (= :jolt (host/runtime))
                     (conj 'hegel.corpus-digest-jolt-test))]
    (when (= :jolt (host/runtime)) (require 'hegel.corpus-digest-jolt-test))
    (let [{:keys [fail error]} (apply t/run-tests namespaces)]
      (when (pos? (+ fail error))
        (throw (ex-info "corpus contract tests failed" {:fail fail :error error}))))))

(defn -main [& _]
  (doseq [ns-name ['hegel.core 'hegel.ffi 'hegel.install]]
    (when (find-ns ns-name)
      (throw (ex-info "engine-free corpus runner loaded a native engine dependency"
                      {:namespace ns-name}))))
  (run-checks!))
