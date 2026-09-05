# Observations and explicit coverage

These APIs are an unreleased implementation of #54, not part of historical
v0.5.0. Changes require supported-host CI and independent review. They do not
enable concurrent runs.

```clojure
(require '[hegel.core :as h] '[hegel.generator :as g])

(h/run-test!
 {:test-cases 100 :seed 42 :database ""
  :coverage {:scope :exploration
             :requirements {"nonempty" {:min-count 1}}}}
 (fn [_]
   (let [xs (h/draw! (g/vector {:max-size 20} (g/integer)))]
     (h/observe! (count xs) "length")
     (when (seq xs) (h/event! "nonempty"))
     ;; Property assertions still throw on failure.
     (assert (= xs (vec (reverse (reverse xs))))))))
```

Always inspect the returned `:passed?` (or use `hegel.clojure-test/with`).
A returned `false` from the property body still means a passing case.

## Two different observation facilities

- `h/event!` records a categorical label. Repetition counts once per case.
- `h/observe!` takes a finite numeric value, then a label. It does not guide
  targeting; `h/target!` remains the targeting API. Frontend summaries retain
  count/min/max, not all samples. Numeric observations never satisfy a
  categorical coverage requirement, even under the same label.
- `:show-statistics? true` enables libhegel's human-readable statistics on its
  native stderr stream. The default is off. This output is not captured by
  rebinding Clojure `*err*`, is not a stable structured format, and is never
  parsed to decide coverage. Native verbosity may affect emitted diagnostics.
- `:observations? true` enables frontend structured result data, independently
  of native printing. `:coverage` implicitly enables the same collection even
  if `:observations?` is false. Ordinary runs without either option retain
  their existing result shape.

Labels must be stable, nonblank strings without NUL or malformed Unicode, at
most 256 UTF-16 code units on every host. Do not derive labels from secrets,
generated values or exception messages. Numeric values are converted to a
finite double; nonnumeric values, NaN, infinity and conversion overflow are
usage errors. Calls require an active test case. Validation errors abort a run
rather than becoming counterexamples to shrink.

Structured collection allows at most 256 distinct labels across categorical
and numeric observations per case and per summary. One label used in both
kinds occupies one slot. Exceeding this limit is an error, not truncation.
The exploration and final-replay summaries each have this limit. Native
statistics are managed separately by libhegel; use low-cardinality labels.

## Counting and phase boundaries

`:observations` contains `:scope`, `:phases`, `:exploration` and `:final-replay`.
Each summary contains:

```clojure
{:cases {:valid 10 :invalid 2 :overrun 0 :interesting 0}
 :events {"nonempty" {:valid 8 :invalid 1}}
 :numeric {"length" {:valid {:count 10 :min 0.0 :max 20.0}}}}
```

Absent label/outcome entries mean zero observations. Numeric counts count
calls, while categorical counts count cases. Only completed cases are merged;
setup, usage, native and inconclusive aborts propagate with existing cleanup.
An event whose native call throws is not committed to frontend counters.
These are case-level observations: a generator retry or discarded draw within
an eventually valid case does not roll them back. Put final-value coverage
events after the generator has returned when that is the intended obligation.

libhegel's printed statistics cover **generation-phase** cases. Its C 0.36.3
ABI does not expose the native subphase of each returned test case. Therefore:

- An explicitly configured `:phases [:generate]` run has frontend scope
  `:generation-only` (automatic final failure replay is still separate).
- Other runs have frontend scope `:exploration`: native reuse, generation,
  targeting and shrinking are not individually identifiable. `:phases` records
  the caller's supplied collection, or `:all` for the native default.
- `:final-replay` counts the wrapper's explicit minimal-failure replay only.
  Non-final is **not** a synonym for generation. Direct `replay-bundle!` does
  not collect or assert run coverage.

No phase is silently disabled to obtain easier coverage accounting. For a
generation-only coverage experiment, explicitly request `:phases [:generate]`;
that choice disables native reuse/target/shrink work and is not equivalent to
a normal shrinking property run.

## Coverage verdicts

`:coverage` is a closed map requiring `:scope` and `:requirements`.
The scope must be `:exploration` or `:generation-only`. The latter is rejected
before native work unless the run explicitly enables only `:generate`.
Requirements are a map of 1..256 labels to closed maps containing:

- `:min-count`: a positive integer through uint64 maximum; defaults to 1.
- `:min-fraction`: a finite number from 0 to 1; defaults to 0. It is normalized
  to a double; comparison uses its rationalized decimal value against exact
  hit/case counts. The displayed `:fraction` is approximate and never decides
  the verdict by itself.

Both thresholds must be met. The denominator is **completed valid exploration
cases only**, not the requested case budget. Rejected, overrun, interesting
and final-replay cases cannot satisfy requirements. No valid cases fails every
requirement, and its displayed fraction is nil. Missing categorical labels
have zero hits. A zero fraction threshold still requires at least one hit.

`:coverage` in the result contains the scope, valid-case count, `:passed?`,
and label-sorted `:checks` with hits, denominator, fraction and thresholds.
If the native run otherwise passes but coverage fails, the result becomes
`:passed? false`, `:status :coverage-failed`. There is no fabricated failure
blob, counterexample, or extra shrink/replay invocation. Existing native
failure/error/flaky results remain primary; coverage does not mask them.
Both standard reporting integrations treat missed coverage as a failure.

Coverage and observation settings are deliberately omitted from schema-v1
counterexample replay options. They are diagnostics/run-level obligations,
not instructions for reproducing one native failure blob.

Random hit rates are observations, not confidence bounds or a proof of
reachability. Native generation is adaptive and observations may be correlated.
Use explicit mandatory witness families for boundary/deep-recursion/state
transitions; keep model assertions and known-bad non-vacuity controls. Repeated
random retries until a threshold passes would weaken this contract.
