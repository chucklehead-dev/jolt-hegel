(ns hegel.materialize
  "Generate bounded, sealed corpus payloads through sequential Hegel runs.

  Materialization deliberately owns neither files nor native installation.  A
  returned envelope contains only completed, independently bounded values."
  (:require [hegel.core :as core]
            [hegel.corpus :as corpus]
            [hegel.host :as host]
            [hegel.validation :as validation]
            [hegel.version :as version]))

(def ^:private max-uint64 18446744073709551615N)
(def ^:private option-keys #{:seed :count :provenance})

(defn- invalid-options! [message data]
  (validation/usage-error! ::invalid-options message data))

(defn- require-option! [opts option]
  (when-not (contains? opts option)
    (invalid-options! (str "materialize! requires " (name option))
                      {:option option})))

(defn- require-fn! [option value]
  ;; `core/draw!` itself requires a function (rather than any IFn).  Rejecting
  ;; map/set callables here keeps malformed materialization inputs out of the
  ;; native run entirely.
  (when-not (fn? value)
    (invalid-options! (str "materialize! " (name option) " must be a function")
                      {option value}))
  value)

(defn- validate-producer! [provenance]
  (when-not (= (host/runtime) (get-in provenance [:runtime :host]))
    (invalid-options! "materialize! provenance runtime host does not match"
                      {:expected (host/runtime)
                       :actual (get-in provenance [:runtime :host])}))
  (when-not (= version/libhegel-version (:libhegel-version provenance))
    (invalid-options! "materialize! provenance libhegel version does not match"
                      {:expected version/libhegel-version
                       :actual (:libhegel-version provenance)})))

(defn- validate-options! [opts generator check!]
  (validation/reject-unknown-keys! ::invalid-options "materialize! options"
                                   option-keys opts)
  (doseq [option option-keys]
    (require-option! opts option))
  (require-fn! :generator generator)
  (when (some? check!)
    (require-fn! :check! check!))
  (validation/require-integer-range! ::invalid-options :count (:count opts)
                                     1 256)
  ;; Validate the full corpus schema before native work.  This bounded
  ;; placeholder establishes the seed/provenance/count policy without asking
  ;; a generator to provide an unbounded value merely to validate options.
  (let [payload {:provenance (:provenance opts)
                 :seed (:seed opts)
                 :count (:count opts)
                 :valid-case-policy :exact-valid-count
                 :values (vec (repeat (:count opts) nil))}]
    (corpus/validate-payload! payload))
  (validate-producer! (:provenance opts))
  opts)

(defn- position-options [seed index]
  {:seed (mod (+ seed index) (inc max-uint64))
   :test-cases 1
   :backend :default
   :phases [:generate]
   :database ""
   :verbosity :quiet
   :report-multiple-failures? false})

(defn- completed-run? [run callback-count]
  (and (true? (:passed? run))
       (= :passed (:status run))
       (false? (:flaky? run))
       (= 0 (:n-failures run))
       (= 1 (:valid-test-cases run))
       (= 1 callback-count)))

(defn- materialization-failed! [position run callback-count]
  ;; Do not attach a run result: failures can contain property exceptions or
  ;; observed generated values.  This is an ordinary verdict contradiction,
  ;; not a wrapper around setup, usage, inconclusive, or native exceptions.
  (throw (ex-info "materialized corpus position did not complete exactly once"
                  {:type ::materialization-failed
                   :position position
                   :passed? (:passed? run)
                   :status (:status run)
                   :flaky? (:flaky? run)
                   :n-failures (:n-failures run)
                   :valid-test-cases (:valid-test-cases run)
                   :callback-count callback-count})))

(defn- payload [opts values]
  {:provenance (:provenance opts)
   :seed (:seed opts)
   :count (count values)
   :valid-case-policy :exact-valid-count
   :values values})

(defn materialize!
  "Generate exactly `:count` valid values and return one sealed corpus envelope.

  Each position is a fresh sequential `run-test!` invocation.  Native
  assumptions may cause attempts within that run, but only a passing,
  nonflaky one-valid-case verdict is accepted.  The optional `check!` runs
  after drawing each candidate and must throw to reject/fail it.

  Usage, setup, native, and inconclusive exceptions are deliberately not
  caught.  An ordinary unsuccessful run becomes a sanitized materialization
  failure, never a partial successful envelope."
  ([opts generator]
   (materialize! opts generator nil))
  ([opts generator check!]
   (let [opts (validate-options! opts generator check!)
         seed (bigint (:seed opts))]
     (loop [index 0 values [] sealed nil]
       (if (= index (:count opts))
         sealed
         (let [callback-count (atom 0)
               captured (atom nil)
               run (core/run-test!
                    (position-options seed index)
                    (fn [_]
                      (let [value (core/draw! generator)]
                        (when check! (check! value))
                        ;; Do not append here: an assumption may still reject
                        ;; this attempt.  Keep only one temporary candidate
                        ;; until the completed native verdict is known.
                        (reset! captured value)
                        (swap! callback-count inc))))]
           (when-not (completed-run? run @callback-count)
             (materialization-failed! index run @callback-count))
           ;; Seal every proposed prefix before retaining it.  This bounds
           ;; portable data and encoded payload text before the next position
           ;; can accumulate another generated value.
           (let [next-values (conj values @captured)
                 next-sealed (corpus/seal (payload opts next-values))]
             (recur (inc index) next-values next-sealed))))))))
