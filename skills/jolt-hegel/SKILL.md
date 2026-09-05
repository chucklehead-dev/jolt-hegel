---
name: jolt-hegel
description: Add, configure, and use jolt-hegel on Jolt, Babashka, or JVM Clojure; write shrinking property, stateful, swarm, and bounded semantic trace tests.
---

# jolt-hegel

Use jolt-hegel to write property-based and model-based tests on Jolt,
Babashka, or JVM Clojure. The public API and behavioral contract are shared;
runtime choice affects installation and native diagnostics, not property code.

The guidance below was verified on 2026-09-04 against source revision
`42ad76538e8824bd774a3c71bfa41723ce9e07c2`. Current `main` requires Jolt
0.8.1; the published `v0.5.0` release retains its historical Jolt 0.7.23+
contract. The supported FFI-capable Babashka floor is 1.13.220 with libffi.

jank and ClojureCLR implementations are merged but experimental. Do not select
one for a consumer merely because its source branch exists: first read
`docs/JANK.md` or `docs/CLR.md` and confirm the project accepts the focused
suite, packaging, and platform limitations. The supported release contract is
Jolt, an FFI-capable Babashka 1.13.220 or later build, and JVM Clojure.

Read [references/api.md](references/api.md) before writing tests. It lists the
exact supported generators, options, result keys, Malli subset, and stateful
contracts. Do not borrow names from another Hegel binding.

## Workflow

1. Inspect the code under test, its stated contract, existing examples, and the
   project's actual test runner.
2. Identify a general invariant: round-trip, idempotence, bounds, agreement
   with a model, invariant preservation, or no-crash behavior over valid input.
3. Confirm the selected host and dependency alias. Install libhegel with that
   host before running the suite.
4. Add the property beside examples for the same behavior. Prefer
   `hegel.clojure-test/with` in a `clojure.test` suite; use `run-test!` for a
   custom runner.
5. Run the narrow test, inspect the minimized counterexample, and replay the
   reported seed before changing code or narrowing a generator.
6. Run the project's established suite on every host it claims to support.

## Host setup

Pin jolt-hegel by full Git commit SHA. Git dependencies do not contribute their
aliases to the consuming project, so activate the alias that contains the
dependency when running the installer.

```bash
# Current `main` / next release candidate: Jolt 0.8.1+
jolt -A:test -m hegel.install

# Babashka 1.13.220+ with libffi (on Linux, do not use the -static asset)
bb -m hegel.install

# JVM Clojure, JDK 22+
clojure -J--enable-native-access=ALL-UNNAMED -M:test -m hegel.install
```

The direct Babashka command assumes the pin is in top-level `:deps` in
`bb.edn`, or otherwise on Babashka's classpath. Activate the consuming
project's alias when it is alias-scoped. Babashka 1.13.220 embeds the upstream
FFI library. The standalone JVM dependency is pinned to the same embedded
`babashka/ffi` commit, `aacb153618bc39ca1e4c397b8f30fb81c76d0c4c`.
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

## Write reliable properties

- Generate the broadest valid domain. Boundary cases are part of the test.
- Construct related values with `g/bind` or `g/let`; use `h/assume!` only when
  rejection is uncommon.
- Use `clojure.test/is` inside `hegel.clojure-test/with`. In a direct
  `run-test!` property, throw on failure; returning `false` passes the case.
- Give exceptions a stable `:hegel/origin` based on the property or assertion
  site. Never include generated values in an origin.
- Check `:passed?` when calling `run-test!`. `hegel.report/counting-runner` and
  `hegel.report/run!` provide a continue-and-count policy for custom suites.
- Keep expensive diagnostics behind `h/final?`, `h/when-final`, `h/fprn`, or
  `h/note!`.
- Preserve the result's string `:seed`; replay it as a number with
  `(parse-long (:seed result))`.
- Treat every `:flaky? true` result as a failed, untrusted counterexample until
  shared state, timing, or generation nondeterminism is fixed.

## Choose generators that shrink structurally

Use `g/string`, `g/regex-str`, and formatted generators for text. Build
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

Use `g/recursive` for trees, ASTs, nested workflows, and recursive documents.
Pass a leaf generator plus a branch function that consumes the supplied
subtree generator; let libhegel own depth, leaf-budget, retry, and subtree
hoisting rather than generating a competing depth counter.

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
