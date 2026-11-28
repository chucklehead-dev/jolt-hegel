---
name: jolt-hegel
description: Add, configure, and use jolt-hegel on Jolt, Babashka, or JVM Clojure; write shrinking property, stateful, swarm, and bounded semantic trace tests.
---

# jolt-hegel

Use jolt-hegel to write property-based and model-based tests on Jolt,
Babashka, or JVM Clojure. The public API and behavioral contract are shared;
runtime choice affects installation and native diagnostics, not property code.

The current release is v0.7.0 with libhegel 0.53.2. Current source
supports Jolt 0.8.1+ (0.8.31 is primary), FFI-capable Babashka 1.13.220+
(1.13.230 is primary), and JVM Clojure on JDK 25+ with babashka.ffi 0.1.3.
The historical v0.5.0 Jolt floor is 0.7.23.

Check `hegel.version/libhegel-version` and the migration guide before changing
native dependencies. A saved replay bundle requires the engine and deployment in its manifest.
Replay an old failure there or generate a fresh bundle on 0.53.2.

jank and ClojureCLR implementations are merged but experimental. Do not select
one for a consumer merely because its source branch exists: first read
`docs/JANK.md` or `docs/CLR.md` and confirm the project accepts the focused
suite, packaging, and platform limitations. The supported release contract is
Jolt, an FFI-capable Babashka 1.13.220 or later build, and JVM Clojure.

Use the contents of [references/api.md](references/api.md) to read the relevant
API sections before writing tests. Check exact signatures, options and result
paths; do not borrow names or arities from another Hegel binding or test.check.
For external consumer packs and DX probes, also read
[references/external-pilots.md](references/external-pilots.md).

## Workflow

1. Inspect the code under test, its stated contract, existing examples, and the
   project's actual test runner.
2. Identify a general invariant: round-trip, idempotence, bounds, agreement
   with a model, invariant preservation, or no-crash behavior over valid input.
3. Confirm the selected host and dependency alias. Install libhegel with that
   host for native generation/replay; baked corpus consumption deliberately
   requires no native installation.
4. Add the property beside examples for the same behavior. Prefer
   `hegel.clojure-test/with` in a `clojure.test` suite; use `run-test!` for a
   custom runner.
5. Run the narrow test and inspect the retained failure in `:final`/`:failures`.
   Preserve its ordinary input and, for a stable reproduced failure, a native
   replay bundle. Reproduce in a fresh process with independent provenance
   before changing code or narrowing the generator. A seed rerun is useful
   evidence but is not replay of the saved minimized choices.
6. Run the project's established suite on every host it claims to support.

## Host setup

Pin jolt-hegel by full Git commit SHA. Git dependencies do not contribute their
aliases to the consuming project, so activate the alias that contains the
dependency when running the installer. The examples use a dedicated consumer
`:hegel` alias with dependencies, test paths and JVM options. Omit
`:main-opts` so the caller can select the installer or the property runner.

```bash
# Jolt 0.8.1+ (0.8.31 is the primary CI release)
jolt -A:hegel -m hegel.install

# Babashka 1.13.220+ with libffi (on Linux, do not use the -static asset)
bb -m hegel.install

# JVM Clojure, JDK 25+
clojure -M:hegel -m hegel.install
clojure -M:hegel -m project.property-runner

# Experimental jank 0.1-alpha on Linux/macOS; curl must be on PATH.
jank -I generated --module-path src:resources:script \
  run-main hegel.install -- setup
```

The direct Babashka command assumes the pin is in top-level `:deps` in
`bb.edn`, or otherwise on Babashka's classpath. Activate the consuming
project's alias when it is alias-scoped. Babashka embeds the upstream FFI library. The standalone JVM dependency is
`org.babashka/ffi` version `0.1.3` and requires JDK 25+.
It lives in this repository's `:jvm` alias so Babashka consumers do not fetch a
duplicate copy. Dependency aliases do not propagate, so JVM consumers must add
that FFI dependency beside jolt-hegel. Babashka consumers must use a build whose
`bb describe` output has a non-nil `:libffi/version`; the Linux `-static`
release asset cannot load libhegel or pass its by-value aggregate signatures.

Use the project's selected executable, not a convenient global binary. For
Jolt, keep `JOLT_CACHE_DIR` writable and key it by the dependency SHA when a
cache survives upgrades. Use `HEGEL_CACHE_DIR` for a writable native cache or
`HEGEL_LIBHEGEL_LIBRARY` for an explicit compatible library. Do not bypass
checksum or runtime ABI-version checks.

If native loading fails, inspect `(hegel.abi/backend-report)` after backend
initialization. It reports coverage and routes such as `:jolt/direct`,
`:bb/trampoline`, `:bb/libffi`, `:jvm/ffm`, `:jank/generated`, and
`:clr/generated-pinvoke`. Property code must not branch on those routes. An
unsupported Babashka build is diagnosed before the configured libhegel path is
checked, so do not treat that error as a missing native dependency.

When diagnosing Jolt scalar FFI writes, preserve the Jolt 0.8 value-before-offset
argument order. In a mock-only adapter test, invoke `write-value` with an
actually allocated pointer `p`, type `:int64`, offset `24`, and value `42`, then
assert that the mocked underlying write records `[p :int64 42 24]`. Keep the
nonzero offset and value distinct so a swap is obvious; do not use an integer
sentinel as a pointer. This is a Jolt-adapter diagnostic only; shared property
code must not branch on it.

Do not replace jolt-hegel's scoped temporary allocation with a fresh Jolt arena
per wrapper or per FFI call. The landed evaluation measured that strategy as
substantially slower for the adapter-scale workload. Jolt 0.8.27 includes the
confined-arena optimization and sibling-fiber publication fix, but an exact
release remeasurement found a fresh arena about 3.90x raw allocation and a
100-allocation batch about 2.95x raw (Linux amd64 allocation microbenchmark).
These are not end-to-end property-testing speedup measurements. Consider arenas
only for a real lexical batch
that amortizes one close across many allocations, and require a
`System/nanoTime` gate with an above-noise-floor workload plus a bounded,
regression-sensitive negative control before changing ownership.

## Write reliable properties

- Generate the broadest valid domain. Boundary cases are part of the test.
- Construct reusable dependent generators with `g/bind`. Use `g/let` only
  inside an active property: it draws immediately and returns a value. Use
  `h/assume!` only when rejection is uncommon.
- Check combinator signatures: `(g/bind f generator)` is function-first;
  `g/one-of` takes a collection of generators; `g/tuple` is variadic.
- Use `clojure.test/is` inside `hegel.clojure-test/with`. In a direct
  `run-test!` property, throw on failure; returning `false` passes the case.
- Give exceptions a stable `:hegel/origin` based on the property or assertion
  site. Never include generated values in an origin.
- Check `:passed?` when calling `run-test!`. `hegel.report/counting-runner` and
  `hegel.report/run!` provide a continue-and-count policy for custom suites.
- Keep expensive diagnostics behind `h/final?`, `h/when-final`, `h/fprn`, or
  `h/note!`.
- Preserve the result's string `:seed`; rerun it as a number with
  `(bigint (:seed result))`; uint64 seeds can exceed `parse-long`'s range.
- Treat every `:flaky? true` result as a failed, untrusted counterexample until
  shared state, timing, or generation nondeterminism is fixed.
- Prefer `g/let` or the labelled `h/draw!` arity when a copyable failure
  diagnostic matters. Labelled draws and `h/note!` use the bounded native
  test-case document; rejected filters, duplicate collection candidates, and
  recursive retries roll their speculative output back.
- Put secret-bearing drawn values behind `:counterexample {:redact-fn ...}`.
  Redaction runs before `:render-fn`, and only rendered strings enter the
  result snapshot. It does not sanitize text already passed to `h/note!`.
  These presentation hooks are deliberately absent from replay bundles.

## Retain and reproduce failures

There is no top-level `:counterexample` snapshot. Read a retained entry, e.g.
`(-> result :final first :counterexample)`. With `:verbosity :quiet`, that
snapshot is nil; the retained exception and native blob are still available.
For a plain fixture, explicitly attach the bounded domain input to assertion
exception data and read `(-> result :final first :exception ex-data :input)`.
Handle each origin separately when multiple failures are enabled.

Capture callbacks can run repeatedly. Do not assume an arbitrary or last
`final?` callback is the selected minimum. Do not serialize the entire result
as a portable artifact: printed exceptions can contain `#error` forms. Use the
bundle codec for native replay and plain data for ordinary regression fixtures.
The expected replay manifest comes from the deployment/property, independently
of the supplied bundle. See [the replay reference](references/api.md#replay-bundles).

## Choose generators that shrink structurally

Use `g/string`, `g/regex-str`, and formatted generators for ordinary text.
For language-shaped strings, use `g/grammar` with Clojure data, or `g/grammar-str`
for existing Lark source. `hegel.grammar/compile` inspects the data DSL without
loading native code. For a parser's semantic property, generating an AST and
printing/evaluating it independently may provide a stronger oracle than
acceptance alone. There is no automatic Instaparse/Malli grammar conversion or
test.check generator interchange. Build
structured values with `g/vector`, `g/set`, `g/map`, `g/tuple`, or `g/hmap`.
Use `g/bind` for a reusable dependent generator. Use `g/let` inside an active
property when later draws depend on earlier values; it draws immediately and is
not itself a generator constructor.

Use `g/octet` for an unsigned protocol byte and convert only at an API boundary
that requires a signed host byte. Use `(g/integer -128 127)` for a property
defined over signed bytes and `g/bytes` for byte arrays. For streaming input,
draw `(g/chunkings payload)` so both content and write boundaries shrink.

`g/integer` bounds are validated when the generator is constructed: both bounds
must satisfy Clojure's `integer?` predicate (so `1.0` is rejected), be in the
signed 64-bit range, and have a minimum no greater than the maximum. Invalid
bounds are usage errors before any native draw.

`h/sample` returns up to `n` values when the underlying property run passes; the
engine may exhaust its choices before reaching `n`. Ordinary property failures
and flaky verdicts throw `::h/sample-failed` with the complete run result and
original cause data instead of returning a partial sample. Usage, setup, and
native harness exceptions from `run-test!` propagate directly.

`g/time` and `g/datetime` draw on the requested `:precision` grid.
The default is `:microseconds`. Use `:milliseconds` for a clock that records
whole milliseconds, or `:nanoseconds` to exercise distinct values within a
microsecond. `g/date` accepts `:weekdays` and `:months` within its inclusive bounds. Use `g/timestamp` for comparable
UTC instant maps across hosts.

Use `g/recursive` for trees, ASTs, nested workflows, and recursive documents.
Pass a leaf generator plus a branch function that consumes the supplied
subtree generator; let libhegel own depth, leaf-budget, retry, and subtree
hoisting rather than generating a competing depth counter.

## Materialized corpora

Use `hegel.materialize/materialize!` for an exact count of successful plain-data
examples; use replay bundles for native failed-case reproduction instead.
Read the materialized-corpora section of [references/api.md](references/api.md)
for the source pin, generation policy and independent consumer-pin boundary.
`hegel.corpus/decode` alone is not verification. Never derive trusted expected
provenance or digest from the artifact being consumed, and never load the native
generator in a baked-only consumer. Run model checks outside fail-open advice.

## Typed Clojure pilot

The repository now has a bounded, JVM-only development checker at
`clojure -M:typed-check`, pinned to Typed Clojure 1.3.0. It checks the unchanged
`text-size` body, but `validate!` is externally annotated `^:no-check`: typed
call sites enforce its polymorphic limits/callback/result contract while the
production loop body remains trusted and covered by runtime tests. The checker
and `typed/` tree are absent from normal consumer paths. Annotation loading,
actual static checking, compiler hints and runtime validation remain different
capabilities; do not claim that Jolt or another host runs the JVM checker, and
do not weaken or remove the valid driver and mutation-specific failing controls
when extending the pilot.

## Observations and coverage

Current source adds `h/event!` and `h/observe!`; use stable string labels and
keep measurements separate from targeting. Read the observation section of
[references/api.md](references/api.md) before adding `:coverage` requirements.
Native printed statistics are not structured coverage data, and non-final
cases are not necessarily generation cases. The `:final-replay` observation
bucket contains native diagnostic-capture cases and may include repeated
capture/confirmation; it is not one minimum or a set of independent samples.
Keep explicit witness families;
do not retry random runs until a coverage threshold happens to pass.

## Stateful testing

Call `hegel.stateful/run!` inside a Hegel property. Each `hs/rule` receives the
current state and returns the next state. Check preconditions before mutation;
a false precondition or failed assumption skips the attempted rule and cannot
undo a side effect. Invariants run initially and after successful rules.

Create fresh mutable systems inside the property body. Keep rule names and
order stable. libhegel owns sequence length, shrinking, and per-case random
nonempty swarm selection; do not add a competing rule-choice loop.

Use `hs/pool` when one rule creates handles or identifiers consumed by later
rules. Pools are scoped to one generated case. Draw with
`hs/values-reusable` to retain an entry or `hs/values-consumed` to remove it.

## Semantic trace testing

Use `hegel.trace/check!` when generated inputs or stateful commands produce a
bounded event trace, including a compiler-aspect journal. Snapshot only after
the generated action or checkpoint has completed. Apply the built-in sequence,
lifecycle, parentage, and eventual-outcome rules, or add a stable named
predicate with `hegel.trace/rule`.

Choose sequence semantics explicitly: nondecreasing allows duplicates and
gaps, strictly increasing allows gaps, and contiguous requires `+1`. Use a
scope function for independently ordered partitions or cursors.

Use `hegel.trace/event-model` for a pure per-event state fold. It is the
preferred small abstraction for linear resources such as fds, callbacks,
buffer loans, spans, and result handles: define `:step`, check `:invariant`
after each event, and use `:final` for the completed-snapshot obligation.

Run trace assertions outside aspect advice. Jolt's aspect runtime fails open on
advice errors, so an assertion thrown inside advice is intentionally not a
reliable test verdict. Keep the snapshot within `:max-events` and use
`contiguous-sequence` when a bounded ring journal can discard its prefix.
Hegel will then shrink the inputs or stateful rule sequence that produced a
failed trace and retain the bounded events in final exception data.

For bounded histories, use `hegel.history/analyze` when the caller needs an
explicit legal/illegal/inconclusive result. Legacy witness/boolean/check APIs
throw a `:hegel/inconclusive? true` exception on search-budget exhaustion;
never turn that into a shrinkable property failure. The candidate-work budget
does not limit preprocessing or wall time in arbitrary model callbacks.

For producers explicitly claiming canonical operation events, use
`hegel.event-contract/check!` or `check-envelope!` before the domain model.
Read the canonical event profile section of [references/api.md](references/api.md).
Do not apply that profile implicitly to legacy or mixed event-model traces;
transport decoding and matching version strings alone do not check semantics.

## External systems

An expensive service may wrap the complete `run-test!` call only when every
property invocation opens a fresh session and restores equivalent observable
state. The service must remain alive through generation, shrinking, and final
replay. Close per-case resources in `finally` for passing, rejected, failing,
and replayed cases.

Prefer protocol completion signals over sleeps. Bound both elapsed time and
data volume. Startup, reset, dependency, and transport failures are harness
errors, not reasons to reject a generated case. If state cannot be isolated,
shrinking is order-dependent and the fixture is invalid.

## Execution model

One `run-test!` call is sequential because adaptive generation and shrinking
depend on prior cases. There is no worker option. Do not assume concurrent
native runs are supported without a project-specific conformance test.

When a property launches fresh host processes, pre-resolve dependencies and
treat worker bootstrap as harness setup. Preserve the first worker artifact and
transcript. Do not send identical infrastructure failures through generation
and shrinking.
