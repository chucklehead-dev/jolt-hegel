# libhegel 0.44 migration

Current source binds libhegel **0.44.1**, from
[`libhegel-v0.44.1`](https://github.com/hegeldev/hegel-rust/releases/tag/libhegel-v0.44.1)
at `ebfd9d53a3de91522e0c4e5941cf08242aba713b`. Native C release versions
are separate from Hegel's Rust crate versions. The installer uses the native
release tag and independently pinned shared-library checksums.

Jolt's primary CI release is **0.8.17**. The property API is shared with
FFI-capable Babashka and JVM Clojure; it needs no aspect compiler fork.
Older release tags and historical evidence keep their original contracts.

## What you get

- Shared engine profiles, `hegel.toml`, environment defaults, and inspectable
  resolved settings, with explicit Clojure options taking precedence.
- Weighted rules and a step budget per machine. Sequential invariants still
  run before the first rule and after every successful rule.
- Engine-confirmed failures, fresh per-origin captures, caveats, and blob-run
  replay instead of an extra frontend replay after every failed run.
- Component-derived opaque generator labels that help the engine recognize
  compatible spans when shrinking and mutating cases.
- Native fixes to string/alphabet ownership, caching, state-machine shrinking,
  float generation, and pool identifier allocation (now amortized constant
  time). These are engine improvements, not a promise that every workload
  gets a particular speedup or a globally minimal counterexample.

## Profiles and environment

```clojure
(require '[hegel.core :as h] '[hegel.generator :as g])

(h/register-profile! "integration"
  {:profile "base" :test-cases 200 :database ""
   :verbosity :quiet :nondeterminism-strictness :error})

(h/resolved-options {:profile "integration" :test-cases 500})
(h/run-test! {:profile "integration" :test-cases 500}
  (fn [_] (assert (<= 0 (h/draw! (g/integer 0 100)) 100))))
```

`resolved-options` loads the verified engine but starts no property run. It
returns effective native settings, including the uint64 seed if one is set.
`register-profile!` snapshots native settings process-wide; existing handles
do not change. Per-test identity (`:database-key`, `:name`, `:test-location`)
and frontend settings such as coverage, rendering, and `:stateful-step-count`
are not registered native profile fields.

Use `h/set-default-profile!` to select a process default; nil clears that
override. Without it, the engine chooses from `HEGEL_DEFAULT_PROFILE`,
`hegel.toml`, detected CI/Antithesis, and its development default. A named
profile can extend another profile in `hegel.toml`; `base` is the immutable
engine base, not a way to disable environment overrides.

```toml
[profiles.integration]
extends = "base"
test_cases = 200
database = ""
nondeterminism_strictness = "error"
```

`HEGEL_CONFIG` selects a TOML file directly. Configuration is cached by the
native engine once per process: restart to pick up file changes. Native
environment overrides include `HEGEL_TEST_CASES`, `HEGEL_DATABASE`,
`HEGEL_STATISTICS`, `HEGEL_SEED`, `HEGEL_DERANDOMIZE`, `HEGEL_PRINT_BLOB`, and
`HEGEL_NONDETERMINISM_STRICTNESS`. They override profiles; explicit options
override them. Malformed environment values or configuration are startup
errors, not failing property cases. Seeds above `Long/MAX_VALUE` work on
Jolt, Babashka, and JVM Clojure and remain decimal strings in results.

`:unbounded-choices? true` removes the engine's per-case choice limit. The
default grew from 8,192 to 1,048,576; suppressing `:test-cases-too-large` also
removes that limit. This is not a run time or memory bound. Use finite
generators and an external process watchdog for potentially blocking tests.

The native `print_blob` preference is exposed through `:print-blob?` and
resolved settings. This frontend deliberately does **not** automatically log
raw blobs; use the result or trusted replay-bundle API. Blobs contain actual
choices and can disclose data. Diagnostic redaction never changes them.

## Rules and per-machine budgets

```clojure
(require '[hegel.stateful :as hs])

(h/run-test! {:test-cases 100 :stateful-step-count 50 :database ""}
  (fn [_]
    (hs/run!
      {:initial-state 0
       :step-count 12
       :rules [(hs/rule :inc {:weight 3.0} inc)
               (hs/rule :dec {:weight 1.0 :precondition pos?} dec)]
       :invariants [(hs/invariant :nonnegative #(<= 0 %))]})))
```

`:step-count` overrides the run's `:stateful-step-count` for this machine;
both default to 50 when omitted. Weights must be finite and positive. They
are relative hints among enabled rules, not promised distributions. A swarm
may select only a subset, and a machine may stop before its budget.

The old low-level `settings-set-stateful-step-count!` was removed upstream.
Low-level constructors now accept `:step-count`, parallel `:rule-weights`,
and parallel `:invariant-always-check`. The sequential frontend passes true
for every invariant, so this upgrade does not introduce sampled checks.

## Capture, caveats, and replay

`final?` now means the engine stamped this attempt for capture. First-check
replays, confirmation batches, database replays, blob runs, and final replays
can be stamped; it does not mean “this is the last/minimal invocation.”
Keep side effects behind it idempotent. `fprn` records bounded diagnostic
notes; normal output and `clojure.test` reports are published from the
freshest failing capture for each reported origin, after the engine finishes.
There is no additional frontend blob replay.

`:nondeterminism-strictness` has three values:

| Setting | Behavior |
| --- | --- |
| `:quiet` (native default) | Engine confirmation and guarded shrinking; failures carry caveats when needed |
| `:warn` | Same handling, with a native notice when nondeterminism is detected |
| `:error` | Abort on detected nondeterminism, preserving the old determinism-lint policy |

A nondeterministic failing run reports `:failed`, not a separate native
nondeterministic status. Each failure may contain `:caveat`. The binding
conservatively marks **any** caveat, missing capture, or nondeterminism error
as `:flaky? true`, and cannot export it as a stable replay bundle. An
unconfirmed failure remains a failure; it is never silently converted into
a pass. `clojure.test` includes caveats in failure messages.

`replay-bundle!` uses `hegel_run_start_blob`, so the engine owns deterministic
continuation/retry behavior and nondeterministic graph replay. The supplied
property can execute more than once. A missing/different origin, passing
attempt, or caveated result is not trusted reproduction. Invalid blobs are
rejected at startup before property execution, with native resource cleanup.
Use only trusted artifacts; the compressed-payload decode cap is not a sandbox.

Do not relabel an old bundle or corpus with a new engine version. Record
generator/model revisions and requalify fixtures deliberately. One historical
51-step deterministic fixture is retained and explicitly retested here;
that is not a general compatibility promise for old blobs or seed streams.

Coverage excludes engine-stamped confirmations, including passing ones. The
historical `:observations :final-replay` key contains selected captures, not
an extra replay. Tests check this separation so confirmation work cannot
manufacture exploration coverage.

## Other native functionality and boundaries

`hegel.label/from-name` and `combine` implement the engine's UTF-8 FNV-1a and
ordered little-endian uint64 hashing without loading native code. Built-in
combinators derive labels from their kind and components. Custom generators
can use `(g/composite-fn "my-app.packet" f)`; function identity, bounds,
mapping-function semantics, and generated values are not inferred. Give
semantically distinct custom generators distinct stable names. Labels are
structural shrink hints, not cryptographic identities or proof of compatibility.

The low-level boundary also exposes owned `test-case-block!` handles and
`test-case-set-worker!` for native output indentation/worker attribution.
Caller-owned blocks/clones must be freed. Their native output does not
automatically join the bounded/redacted frontend document API.
Unlike a clone, a block shares its parent's choice stream: draw through the
two handles serially, never concurrently. The upgrade tests check indentation,
worker attribution, rejected worker IDs, and lexical handle cleanup.
After every writer has finished, resolve the root printer before reading a
document containing block/deferred regions; freeing a block is not resolution.

`:test-location` supplies `:file`, `:line`, `:class-name`, and `:function`.
`hegel.clojure-test/with` supplies a location automatically unless overridden.
With an existing `ANTITHESIS_OUTPUT_DIR`, the engine writes property verdicts
to `sdk.jsonl`; an absent directory is a startup error. Native Antithesis
verdicts are not proof that frontend coverage requirements passed.

Native concurrency above one no longer discards an initial flip case or
disables replay/persistence. It uses engine confirmation, graph blobs, and
caveats. The separate Clojure concurrent executor is still **pending**;
declarations, lifecycle/mock seams, and a watched low-level worker probe do
not constitute a production executor. The older concurrency ADR records its
0.36 assumptions and needs a reviewed implementation delta for this regime.

The canonical ABI covers all 128 functions. jank/CLR artifacts are generated
from it, but those experimental hosts are not promoted by descriptor coverage
or Linux Jolt/BB/JVM results. Their focused CI remains required.

## Checks

Run `bb header-audit` for independent header/signature/layout/constant controls,
`bb jank-codegen-check`, and `bb clr-codegen-check` for generated artifacts.
The supported semantic suites include the migration regressions. The focused
slice is also available as `jolt -M:upgrade-test`,
`bb --classpath src:resources:test -m hegel.latest-native-test`, or
`clojure -J--enable-native-access=ALL-UNNAMED -M:jvm:upgrade-test`.

Fresh-process environment/TOML/Antithesis controls run with
`bb upgrade-env-test` in Babashka CI.
They use synthetic data and never print environment variables or credentials.
