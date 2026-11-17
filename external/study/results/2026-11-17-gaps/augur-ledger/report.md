# Augur ledger external test-gap study

Two target defects reproduced on unchanged source. `timeout 120s ./verify.sh` exits 0, asserting the known defects explicitly, checking native replay and mismatch rejection, running the independent state model, and running existing ledger tests. Verification performed successfully in this study. No target or Hegel source was modified; no manufactured mutation is presented as a target defect.

## Deployment and provenance

- Target: `/workspace/pilot-targets/augur`, SHA `58fef3f2077515c216cd7a295e4599e8579ad5bb`.
- Hegel: `/workspace/jolt-hegel`, SHA `6321d5ab485db656a48823030453bc4b2d0af1cc`.
- Runtime: `/workspace/tooling/jolt/jolt`, `jolt v0.8.27`; native libhegel `0.51.6`, explicit `/workspace/jolt-hegel/.hegel-lib/libhegel_c.so`.
- Runtime manifest: `{:host :jolt :version "0.8.27" :os "Linux" :arch "amd64"}`. `runtime.txt` preserves platform output. Dependencies use local/root; caches are scratch-local.
- Provenance is independently declared in `study/provenance` and the captured SHA file; verification checks it against current git HEAD. It is never copied from a supplied bundle. Generator revision `ledger-study-v1`, model revision `ledger-contract-v1`.

## 1. Expiry bypasses the key cap

Contract: `src/augur/ledger.clj` namespace docstring says everything that grows is capped, and keys beyond `:max-keys` are folded least-recently-used first into `::other`. `expire` promises expiration counts per key. Existing `state-is-bounded` tests exercise resolutions across keys, but open-limit expiry only under one key. `expired-forecasts-are-never-scored` also uses one key.

Minimized valid input: `expiry-input.edn`, `{:n 2 :cap 1 :overflow? false}`. Starting with max-keys 1, register and explicitly expire ID/key 0, then register and expire ID/key 1. Expected: at most one ordinary key record plus `::other`, conserving two expirations. Actual: two ordinary key records, no `::other`, and `:dropped-keys 0`. There are no open forecasts to complicate interpretation of the bound.

Source evidence: `expire-ids` at lines 80–89 only removes open forecasts and increments per-key expiry counts. `expire` at lines 137–143 returns this directly; the automatic expiry path in `register` also bypasses `evict-keys`. The latter runs only after successful resolution. Explicit witnesses show automatic overflow expiry also exceeds the cap (3 registers, max-open 1, max-keys 1), and 100 explicit expirations retain 100 key records. This is unbounded retained state for expiry-only workloads, not a transient excess between two internal steps.

Native generated property fails with stable origin `expiry-key-bound`, no flakiness. `expiry-bundle.edn` reproduces in a fresh process. Target-only `repro.clj` reproduces without loading Hegel.

## 2. Shared benchmark aggregation crashes on repeated key eviction

Contract: the namespace promises benchmark scores alongside forecasts and folding evicted keys into `::other` sums; `register` expressly accepts `:bench {name p}`. Existing scoring tests exercise benchmarks without eviction, while eviction tests omit benchmarks.

Minimized valid input: `benchmark-input.edn`, `{:n 3 :cap 1 :p 0.0}`. Register then resolve three unique IDs/keys 0, 1, 2, each with probability 0.0, true outcome, increasing integer times, and `:bench {:baseline 0.5}`. The first eviction succeeds; the second throws `IllegalArgumentException: Don't know how to create ISeq from: java.lang.Double`. All probabilities and capacities are valid. Expected: complete resolution, aggregate two evicted forecasts/benchmarks under `::other`, retain the most recent key, total resolution count 3.

Source evidence: `fold-sums` at lines 59–60 invokes `(merge-with fold-sums x y)` when x is a map. Combining two maps under a common benchmark name consequently invokes `fold-sums` on scalar score values. That function expects maps; its recursive level is wrong for the nested benchmark sums passed at line 75. The two-key boundary witness confirms a single benchmark-bearing eviction succeeds.

Native generated property fails at stable origin `benchmark-eviction-total`, no flakiness. `benchmark-bundle.edn` reproduces in a fresh process. The target-only reproduction asserts the specific exception class and message fragment; harness failures cannot substitute for this known outcome.

## Independent model and domain

`study.clj` uses Hegel stateful rules for registration, resolution, expiry by key, and EDN roundtrip. Each invocation creates a fresh immutable ledger and independently maintained open map, complete resolution history, expiry counts, and unknown-resolution count. Hegel controls sequence selection and shrinking; rule order and names are stable. Invariants compare actual target state and public rows/records after each step, including oldest-open expiry, row trimming, preserved score count, Brier mean, expiry counts, and unknown resolution behavior. This is not an event-log-only oracle.

Domain: 4 integer keys; unique increasing registration IDs/times; probabilities 0, .25, .5, .75, 1; boolean outcomes; max-open 1–4; max-rows 1–3; max-keys 10; forget 1.0; valid default calibration edges. Model cap is deliberately above its key domain to isolate ordinary transitions from separately tested eviction defects. Resolution IDs range through prior IDs and unknown IDs. Roundtrips use plain EDN with map-valued features. A deterministic witness includes both known and unknown resolutions, open eviction, row truncation, explicit expiry, and full ledger roundtrip equality. A pack-local semantic broken control drops a live forecast from the actual state while keeping the independent model; the oracle detects precisely `:open` disagreement. It is only an oracle check, not a claimed target mutation.

Limitations: no filesystem persistence API exists in this target; persistence here means EDN serialization/deserialization. No concurrent schedule, floating-time ties, discounting model, full calibration model, arbitrary EDN identifiers, or log-score oracle was attempted. No probabilistic coverage thresholds or operation-frequency claims are made. Explicit witnesses establish transition presence. Trace APIs were unnecessary because the target returns immutable state rather than an event journal.

## Outcomes and counts

All runs use seed 414, database disabled. Stateful budget is 35 steps, requested 200 valid cases. Native counts below are reported categories, not independent generated samples; shrinking/confirmation/capture are not conflated with successful generation. No phase-level coverage counter was collected.

| Property | passed? | Native test-cases | Valid | Invalid | Overrun | Interesting | Failures | Flaky |
|---|---|---:|---:|---:|---:|---:|---:|---|
| expiry | false | 37 | 8 | 0 | 16 | 13 | 1 | false |
| benchmark | false | 70 | 21 | 0 | 32 | 17 | 1 | false |
| model | true | 200 | 200 | 0 | 0 | 0 | 0 | false |

Both saved native bundles reproduced in a separate process and then again through `verify.sh`. Changing expected generator revision returns `:incompatible` and executes zero callbacks. The existing ledger suite passes: 7 tests, 29 assertions, zero errors/failures. These passing examples therefore do not cover either discovered defect.

## Artifacts and commands

`study.clj`: properties, model, bundle creation and replay. `target_cases.clj`: plain target scenarios. `target-only/deps.edn`, `repro.clj`, `tests.clj`: target-only repro and existing test runner. `witnesses.clj`: boundary trace and semantic control. `*-input.edn`: minimized ordinary inputs. `*-details.edn`: retained exception data. `*-bundle.edn`: native replay artifacts. `*-summary.edn`: structured original counts. `logs/*original.log` and `logs/generation-fixed.log`: preserved transcripts. `commands.md` records commands and process outcomes; `verify.sh` creates fresh logged verification transcripts and never overwrites original bundles.

## DX evidence ledger (agent self-report)

- Question: can an external author find test gaps from source? Existing tests separately covered benchmarks and eviction, but not their combination; paired valid features exposed the crash. Follow-up: add the three-key shared-benchmark regression to target tests.
- Question: does a bounds invariant cover all state-creating paths? Expiry creates key records without calling eviction. Follow-up: add explicit and automatic expiry across many keys and a conservation assertion for folded expiry counts.
- Question: can Hegel retain and replay an ordinary minimum? Documentation gave correct `:final` exception extraction and bundle APIs; both worked, including mismatch callback suppression. No Hegel defect identified.
- Friction: initial harness had one missing closing parenthesis, preserved in generation-original.log (exit 1), fixed before results. One tool launch used a not-yet-created scratch cwd and failed before execution. These are author/setup errors, not target or Hegel bugs. The routine generator namespace `let` shadowing warning appears in logs.
- Proposed improvement: a small documented independent-state-model example with a bounds invariant spanning all mutating operations would be useful. This evidence does not justify a new core API. Provisioned runtime/dependencies mean this is not a cold-install evaluation; a same-family agent study is not a human usability study.

Recommended target follow-up: fix nested benchmark folding, then apply key eviction consistently after expiry and registration overflow. Add regressions for already-folded benchmark maps, expired-only records, and expiry-to-resolution mixed histories. This study deliberately leaves target source unchanged.
