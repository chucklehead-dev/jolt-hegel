(ns consumer.resources
  "Standalone consumer: canonical data and a real native property, without sources."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [hegel.abi :as abi]
            [hegel.core :as h]
            [hegel.generator :as g]
            [hegel.host :as host]))

(defn- check! [condition message data]
  (when-not condition
    (throw (ex-info message data))))

(defn -main [& args]
  (check! (empty? args) "unexpected standalone probe arguments" {:args args})
  ;; Check resource-text directly as well as the delayed descriptor: a cached
  ;; descriptor alone could conceal a broken resource resolver at runtime.
  (let [descriptor (edn/read-string (host/resource-text "hegel/abi.edn"))
        missing-name "hegel/standalone-intentionally-absent.edn"
        missing (try
                  (host/resource-text missing-name)
                  nil
                  (catch Throwable error (ex-data error)))
        draws (atom 0)
        result (h/run-test!
                {:test-cases 8 :seed 81 :database "" :verbosity :quiet}
                (fn [_]
                  (let [n (h/draw! (g/integer -100 100))]
                    (swap! draws inc)
                    (check! (<= -100 n 100) "integer bounds violated"
                            {:hegel/origin "standalone-resources/bounds" :n n}))))]
    (check! (some? (io/resource "hegel/abi.edn"))
            "normal resource resolver cannot see canonical ABI" {})
    (check! (= descriptor (abi/validate! (abi/descriptor)))
            "canonical ABI mismatch" {})
    (check! (= {:type ::host/resource-not-found :resource missing-name} missing)
            "missing-resource control failed" {:actual missing})
    (check! (and (:passed? result) (pos? @draws))
            "standalone native property failed or did not execute" {:result result})
    (prn {:standalone-resources :passed
          :runtime (host/runtime)
          :functions (count (:functions descriptor))
          :draws @draws
          :missing-resource :rejected})))
