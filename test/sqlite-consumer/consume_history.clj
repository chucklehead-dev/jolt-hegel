(ns consume-history
  (:require [clojure.edn :as edn])
  (:import (java.security MessageDigest)
           (java.util HexFormat)))

;; Independent fixture consumer. Runs with Clojure alone.
(defn -main [file]
  (let [{:keys [format schema-version payload sha256]} (edn/read-string (slurp file))
        digest (.digest (MessageDigest/getInstance "SHA-256") (.getBytes payload "UTF-8"))]
    (assert (= :hegel/materialized-corpus format))
    (assert (contains? #{1 2} schema-version))
    (assert (= sha256 (.formatHex (HexFormat/of) digest)))
    (let [{:keys [values provenance]} (edn/read-string payload)
          events (:events (first values))
          outcomes (mapv :outcome events)
          end (reduce (fn [store {:keys [rule input outcome]}]
                        (let [{:keys [id expected]} input
                              rev (get store id)]
                          (assert (integer? id))
                          (case rule
                            "insert" (do (assert (= #{:id} (set (keys input))))
                                         (assert (nil? rev))
                                         (assert (= :inserted outcome))
                                         (assoc store id 0))
                            ("revise" "retry")
                            (do (assert (= #{:id :expected} (set (keys input))))
                                (assert (some? rev))
                                (if (= expected rev)
                                  (do (assert (= :updated outcome)) (update store id inc))
                                  (do (assert (= :stale outcome)) store)))))) {} events)]
      (assert (= "sqlite/stale-retry" (:property-id provenance)))
      (assert (= 1 (count values)))
      (if (= schema-version 1)
        (do (assert (= [:inserted :updated :stale] outcomes))
            (assert (= [1] (vec (vals end)))))
        (let [[a b revised retried] (map #(get-in % [:input :id]) events)]
          (assert (= [:inserted :inserted :updated :stale] outcomes))
          (assert (= a revised retried))
          (assert (not= a b))
          (assert (= {a 1 b 0} end))))
      (println "PASS stale retry preserves the accepted revision"))))
