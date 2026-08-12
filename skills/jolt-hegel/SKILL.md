---
name: jolt-hegel
description: Add, configure, and use jolt-hegel in Jolt projects and write shrinking property-based or stateful model tests with its current API. Use when a Jolt codebase needs generative tests, property tests, replayable randomized coverage, minimal counterexamples, model-based operation sequences, Hegel integration, or migration from hand-written randomized tests.
---

# Jolt Hegel

Use jolt-hegel to run property-based tests directly in Jolt through Hegel's
native engine. Treat properties as ordinary test code that draws inputs and
throws on failure.

Read [references/api.md](references/api.md) before editing dependencies or
writing code. It is the exact supported API; do not invent generators or
combinators from other Hegel bindings.

## Workflow

1. Inspect the code under test, its docs, existing tests, and call sites.
2. Check the project's Jolt version and test-runner conventions.
3. Resolve the current release tag to its full commit SHA using the command in
   the API reference, add the pinned dependency, and run the installer with the
   alias that contains it, normally `jolt -A:test -m hegel.install`. Use the
   bare command only for a top-level dependency. Never leave the example SHA
   placeholder in project files. Key `JOLT_CACHE_DIR` by that SHA when the
   project has reused a cache across dependency upgrades; the installer rejects
   a loaded release that does not match the resolved source checkout.
4. Identify evidence-backed properties rather than merely replacing constants
   with random values.
5. Add each property beside the existing tests for the same behavior. Prefer
   `hegel.clojure-test/with` in a `clojure.test` suite; use `run-test!`
   directly for custom runners.
6. Run the relevant test command and inspect any minimal counterexample.
7. Replay failures with the returned seed before changing the property or code.

## Choose useful properties

Prefer properties supported by the implementation's contract:

- parse/format or encode/decode round trips;
- invariant preservation after an operation;
- agreement with a simpler independent implementation;
- idempotence of normalization;
- commutativity or monotonicity where documented;
- output bounds and structural validity;
- agreement between a stateful system and a simpler model across operation
  sequences;
- no-crash behavior across the valid input domain;
- boundary behavior at zero, extrema, empty data, and calendar transitions.

Do not force property testing onto exact-output assertions, elaborate fixtures,
or behavior with no meaningful general invariant.

## Write reliable properties

- Start with the broadest generator allowed by the contract. Boundary cases are
  part of the test.
- Construct valid related inputs directly when possible. Use
  `h/assume!` only when rejection is uncommon.
- Use `clojure.test/is` inside `hegel.clojure-test/with`. Inside a property
  passed to a custom runner, throw an exception when the property fails;
  returning `false` does not mark a test case as failing.
- Include a stable `:hegel/origin` in exception data. Base it on the
  property or assertion site, never on generated values.
- Check `:passed?` when calling `run-test!` directly. Use
  `hegel.report/counting-runner` plus `hegel.report/run!` for a framework-less
  suite that must count failed results, continue past thrown run errors, and
  exit nonzero after all properties.
- Treat `:status :error` with `:flaky? true` as a failed property result.
  libhegel uses that shape when the same generated choices produce different
  outcomes or generation. The explanation is in `:error`; there may be no
  counterexample in `:failures`. Inspect `:observed-failures` for structured
  exception summaries captured during exploration.
  `hegel.clojure-test/with` reports all failed results automatically.
- Preserve the result's `:seed` in failure output. Replay it by
  parsing the string and passing it as the next run's numeric
  `:seed`.
- Put detailed diagnostics behind `h/final?`,
  `h/when-final`, `h/fprn`, or `h/note!` so
  ordinary generation stays quiet.
- For operation-sequence tests, use `hegel.stateful/run!`. Return the next
  state from every rule, check preconditions before mutation, and create fresh
  per-case mutable state inside the property body.
- Keep stateful rule names and order stable. libhegel performs swarm selection
  automatically; do not hand-roll a competing rule-choice loop.

## Handle failures

When a property fails:

1. Read the final replay exception and minimized value.
2. Re-run with the reported seed and confirm reproduction.
3. Decide whether the code violates its contract, the property assumes too
   much, or the generator includes values outside the documented domain.
4. Fix implementation bugs when authorized. Otherwise report the counterexample
   clearly.
5. Narrow a generator only with evidence that the excluded values are invalid.

Do not replace a failing property with hard-coded examples or arbitrary bounds
merely to make the suite green.

## Build related data directly

Use `g/string`, `g/regex-str`, and the format generators for text. Build
structured values with `g/vector`, `g/set`, `g/map`, `g/tuple`, or `g/hmap`.
Use `g/bind` to construct a reusable dependent generator. Use `g/let` inside
an active property when later draws depend on earlier ones; it draws immediately
and is not itself a reusable generator. Prefer these forms over broad draws
followed by frequent assumptions; their native spans preserve useful shrinking
structure.

Use `g/octet` for an unsigned protocol byte and call `unchecked-byte` only where
an API requires Jolt's signed byte representation. Use `(g/integer -128 127)`
when the property is defined over signed bytes, and `g/bytes` for byte arrays.
For stream delivery, draw `(g/chunkings payload)`; its chunks stay nonempty,
concatenate to the payload, and shrink toward simpler write boundaries.

Use `hegel.stateful/pool` when one rule creates values that later rules must
reuse or consume. Pools are scoped to one generated test case. If a test needs
an unsupported generator or control, state that limitation instead of
borrowing an API from another Hegel binding.

## Share expensive external services safely

Starting a TCP server inside every generated case is usually too expensive. It
is acceptable to start an external service once around the complete
`run-test!` call when each property invocation opens a fresh connection or
session and begins from equivalent observable server state. Wait for readiness,
then keep that service alive until `run-test!` returns: generation, shrinking,
and automatic final replay all happen inside the call.

Close the per-case connection in `finally`, including on rejected and failing
cases. Reset or namespace any shared server state before each case; if that is
not possible, sharing the server makes shrinking order-dependent and is not a
valid fixture. A manual seed replay may start a new equivalent server, but it
must again wrap the whole `run-test!` call. Let startup, reset, and connection
failures surface rather than turning them into rejected generated inputs. End
each case with protocol signals, not sleeps: for a request stream, half-close
the write side and drain a time- and byte-bounded response through actual EOF;
reaching either bound is a failure. If the property throws first, closing the
fresh connection in `finally` is the abort signal, and the server must still
isolate the next case. An in-protocol reset must drain its acknowledgement
before generated traffic. Shrinking against the live service remains sound
only when every property invocation re-establishes equivalent state. If the
result is `:flaky? true`, fix resource isolation or timing before trusting the
minimized counterexample.

## Keep property execution sequential

There is no worker option for one `run-test!` call; generation and adaptive
shrinking execute sequentially. Jolt 0.7.5 gives each
`hegel.clojure-test/with` evaluation a dynamically scoped report sink.
Concurrent native runs are still unsupported because shim and engine safety
for that pattern is unverified. Do not rely on concurrent property runs without
a dedicated jolt-hegel test.
