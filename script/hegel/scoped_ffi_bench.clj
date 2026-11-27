;; Run in a fresh Jolt process for each mode (docs/UPSTREAM_ADOPTION.md).
(require '[jolt.ffi :as ffi])

(let [[library] *command-line-args*]
  (assert (and library (nil? (ffi/find-symbol "hegel_version"))))
  (when-let [override (System/getenv "HEGEL_LIBHEGEL_LIBRARY")]
    (assert (= library override)))
  (ffi/load-library library))

(def binding-start (System/nanoTime))
(require '[hegel.ffi :as hffi])
(hffi/ensure-compatible-version!)
(println "binding-ms" (/ (- (System/nanoTime) binding-start) 1e6))
(flush)

(require '[hegel.core :as h] '[hegel.generator :as g])
(let [numbers (g/integer -1000 1000)
      run (fn []
            (let [result (h/run-test!
                          {:seed 42 :database "" :test-cases 200 :verbosity :quiet :phases [:generate]}
                          (fn [_]
                            (let [a (h/draw! numbers) b (h/draw! numbers)]
                              (assert (= a (- (+ a b) b))))))]
              (assert (and (:passed? result) (= 200 (:valid-test-cases result))))))]
  (dotimes [_ 5] (run))
  (let [start (System/nanoTime)]
    (dotimes [_ 20] (run))
    (println "property-ms" (/ (- (System/nanoTime) start) 20e6))))
