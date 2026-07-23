(ns consumer.smoke
  (:require [clojure.test :as t]
            [hegel.clojure-test :as ht]
            [hegel.core :as h]
            [hegel.generator :as g]
            [hegel.report :as report]
            [hegel.stateful :as hs]))

(defn -main [& _]
  (let [fixed-date {:year 2024 :month 2 :day 29}
        seen (atom [])
        runner (report/counting-runner {:reporter (fn [_] nil)})
        result (h/run-test!
                {:test-cases 3
                 :database ""
                 :verbosity :quiet}
                (fn [_]
                  (g/let [x (g/integer 2 2)
                          y (+ x 1)]
                    (let [pool (hs/pool)
                          _ (hs/add! pool :ready)
                          pooled (h/draw! (hs/values-consumed pool))
                          state
                          (hs/run!
                           {:initial-state {:count 0}
                            :rules [(hs/rule :increment
                                             #(update % :count inc))]
                            :invariants
                            [(hs/invariant :nonnegative
                                           #(<= 0 (:count %)))]})]
                      (swap! seen conj
                             {:date (h/draw! (g/date fixed-date fixed-date))
                              :uuid (h/draw! (g/uuid 4))
                              :token (h/draw! (g/string {:min-size 3
                                                        :max-size 3
                                                        :alphabet "abc"}))
                              :values (h/draw! (g/vector {:size 2}
                                                         (g/boolean)))
                              :octet (h/draw! (g/octet))
                              :chunks (h/draw! (g/chunkings [0 1 2 3]))
                              :dependent [x y]
                              :pooled pooled
                              :stateful-count (:count state)})))))
        counted-result
        (report/run!
         runner
         "consumer counting smoke"
         #(h/run-test!
           {:test-cases 1 :database "" :verbosity :quiet}
           (fn [_] (h/draw! (g/octet)))))
        test-result
        (ht/with {:test-cases 3 :database "" :verbosity :quiet}
          [value (g/integer 1 3)]
          (let [state
                (hs/run!
                 {:initial-state {:value value :steps 0}
                  :rules [(hs/rule :step #(update % :steps inc))]
                  :invariants
                  [(hs/invariant :value-in-range
                                 #(<= 1 (:value %) 3))]})]
            (t/is (pos? (:steps state)))))]
    (when-not (and (:passed? result)
                   (:passed? counted-result)
                   (:passed? test-result)
                   (= 1 (report/run-count runner))
                   (report/passed? runner)
                   (some? (:seed result))
                   (seq @seen)
                   (every? #(= "2024-02-29" (:date %)) @seen)
                   (every? #(some? (re-matches
                                    #"[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
                                    (:uuid %)))
                           @seen)
                   (every? #(and (= 3 (count (:token %)))
                                 (= 2 (count (:values %)))
                                 (<= 0 (:octet %) 255)
                                 (= [0 1 2 3]
                                    (vec (mapcat identity (:chunks %))))
                                 (every? seq (:chunks %))
                                 (= [2 3] (:dependent %))
                                 (= :ready (:pooled %))
                                 (<= 1 (:stateful-count %) 50))
                           @seen))
      (throw
       (ex-info "jolt-hegel consumer smoke test failed"
                {:result result
                 :clojure-test-result test-result
                 :seen @seen})))
    (println "consumer smoke passed with replayable seed" (:seed result)))
  nil)
