(ns pilot.runner-test
  (:require [clojure.edn :as edn]
            [hegel.core :as h]
            [hegel.generator :as g]
            [pilot.runner :as runner]))

(defn check-nil [input broken?]
  (runner/require! :nil-input (not (and broken? (nil? input))) {:expected false}))

(def property
  {:id :nil-input :generator (g/just nil)
   :labels (constantly ["nil"]) :coverage ["nil"] :check check-nil})

(defn expect-rejection [kind f]
  (let [observed (try (f) nil (catch Exception e (:kind (ex-data e))))]
    (assert (= kind observed) (str "Expected " kind ", got " observed))))

(let [out (first *command-line-args*)
      run! (fn [mode p] (runner/main! :instaparse [p] [mode out]))
      path (str out "/nil-input.value.edn")]
  (expect-rejection :pin-mismatch #(runner/main! :absent-pack [] ["run" out]))
  (run! "run" property)
  (run! "replay" property)
  (let [fixture (edn/read-string (slurp path))]
    (doseq [invalid [(dissoc fixture :input) (assoc fixture :input false)
                     (assoc fixture :property :other) (assoc fixture :provenance {})]]
      (spit path (pr-str invalid))
      (expect-rejection :fixture-mismatch #(run! "replay" property)))
    (spit path (pr-str fixture)))
  ;; A different exception that claims the same origin can reproduce the
  ;; native blob. It must still fail the pilot's assertion identity check.
  (expect-rejection :unconfirmed-control
                    #(run! "replay" (assoc property :check
                                          (fn [v broken?]
                                            (if (and h/*test-case* broken?)
                                              (throw (ex-info "different defect"
                                                              {:hegel/origin "instaparse/nil-input"}))
                                              (check-nil v broken?)))))))
(println "PASS pilot pin, replay identity and fixture controls")
