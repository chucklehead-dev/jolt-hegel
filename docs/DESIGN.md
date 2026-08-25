# Design

This document records the behavioral contracts that are easy to break while
extending jolt-hegel. Public usage belongs in the README; native layering belongs
in `ARCHITECTURE.md`.

## Seeds are always known

`run-test!` resolves a seed before it creates the native run:

- an explicit `:seed` is preserved;
- `:derandomize? true` derives a stable seed from `:database-key`, `:name`, or a
  library default; and
- every other run receives a fresh non-negative 63-bit seed assembled from
  the host clocks and random source.

The resolved seed is supplied to libhegel and returned as a decimal string in
the result map. This makes every run replayable even though the C ABI does not
expose a seed chosen internally by the engine.

## Generators are tagged functions

A generator is a tagged `(fn [test-case] value)`. Primitive generators delegate
to libhegel draw operations. Host-level composition remains ordinary Clojure,
but every structural combinator opens the corresponding libhegel span so the
shrinker retains the shape of the generated value.

| Combinator | Engine structure |
| --- | --- |
| `fmap` | mapped span |
| `bind` | flat-map span |
| `filter` | filter span with at most three candidate draws |
| `one-of`, `optional` | choice spans |
| `tuple` | tuple span |
| vector, list, set, map | collection handle plus matching collection span |

Duplicate set elements and map keys reject the current collection element
through libhegel rather than silently changing the requested size. `g/let`
draws tagged generator expressions while leaving ordinary dependent expressions
alone. `composite-fn` is the escape hatch for a custom generator function.

Prefer direct generation of valid inputs. `filter` and `assume!` are intended for
infrequent exclusions because excessive rejection reduces useful test coverage
and can trigger libhegel health checks.

## Property outcomes

The property body communicates with the run loop through values and exceptions:

| Body outcome | libhegel case status |
| --- | --- |
| returns normally | valid |
| `assume!` or an engine assumption rejects | invalid |
| an engine draw stops the test | overrun |
| an application/property exception | interesting, with a stable origin |

Configuration, API usage, and native libhegel harness errors escape the property
loop immediately. They must not be treated as counterexamples and sent through
shrinking. Only the native STOP and ASSUME results are ordinary generated-case
control flow.

The public result distinguishes the aggregate run status, wrapper-observed case
counts, failures, final replay summaries, the seed, and whether any failure was
flaky. `:test-cases` is a maximum: an exhausted engine choice tree may finish
earlier.

libhegel has a separate run-level nondeterminism path. Its "Flaky test
detected" and "data generation is non-deterministic" errors return
`:passed? false`, `:status :error`, `:flaky? true`, and the explanation in
`:error`, without inventing a replayable failure blob. Health checks and other
engine errors remain `:hegel.core/run-error` exceptions because they are not
property verdicts.

The wrapper retains bounded `:observed-failures` grouped by stable origin while
it drives exploration. Each entry includes the first and last structured
throwable summaries plus an observation count. This preserves diagnostic
`ex-data` for a failure that does not reproduce and for run-level
nondeterminism, where libhegel cannot supply a failure blob.

## Failure identity and origins

libhegel uses an origin to decide which failing examples represent the same bug.
An origin must identify a property, rule, invariant, or assertion site and must
never contain a generated value. Value-dependent origins partition the shrink
budget and prevent convergence.

For direct properties, a thrown exception may provide `:hegel/origin` in its
`ex-data`; otherwise the exception class supplies a stable fallback. The
`clojure.test` macro captures its source location at macro expansion and combines
it with the first failing assertion's stable identity. Stateful rules and
invariants derive origins from their declared names.

## Shrinking and final replay

Failure blobs are copied before their native result objects are freed. Each blob
is replayed through a final test case after shrinking completes. Only the replay
runs with `final?` true. Reproduction requires both another interesting outcome
and the original stable origin. A missing failure or a different replay origin
is retained with `:replay-origin` and marks the aggregate result `:flaky? true`.

Diagnostics should use `when-final`, `fprn`, or `note!` so they describe the
minimal counterexample instead of every generated attempt. The database is
disabled in deterministic tests to prevent stale examples from changing their
starting point.

## `clojure.test` semantics

`hegel.clojure-test/with` turns generator bindings and a property body into one
ordinary test assertion:

- a passing property reports one pass, independent of case count;
- intermediate failing candidates are captured but not published;
- final replay assertion reports are published for the minimal failure;
- every nonpassing event includes the resolved Hegel seed;
- blank exception messages fall back to the throwable-map cause or exception
  type and preserve original `ex-data`; and
- report capture is isolated for the active host.

The direct `run-test!` API returns data and does not throw merely because a
property failed or libhegel detected nondeterminism. A non-`clojure.test`
runner must explicitly turn `:passed? false` into its own test failure and wrap
the complete call if it must continue after setup, health-check, or unexpected
engine errors. `hegel.report/counting-runner` provides that policy without
owning process exit.

## Execution is sequential

One `run-test!` invocation drives generation and adaptive shrinking
sequentially; the public options expose no workers. Separate
`hegel.clojure-test/with` evaluations have isolated report bindings, but
concurrent engine use is not a supported contract until it has a
dedicated integration test.

## Stateful testing

`hegel.stateful/run!` exposes immutable model transitions while libhegel owns
rule selection, sequence shrinking, the configurable round cap (50 by default),
termination, and a random nonempty swarm subset for each case. At concurrency
one, a rejected rule is reported to libhegel and does not consume that budget.

Rules have stable, unique names and receive the current state. A rule
precondition is evaluated before its step. A false precondition or `assume!`
inside the rule reports a rejected rule to libhegel, skips that attempt, and
does not run invariants. Invariants
run once on the initial state and after every successfully applied rule. Failures
include `:hegel.stateful/trace` and stable rule or invariant origins.

Rule names and order must remain unchanged between generation and replay.
Mutable systems under test must be constructed inside the property body so each
generated case and final replay begins from fresh external state.

libhegel 0.33 uses one round protocol for sequential and concurrent machines.
jolt-hegel fixes concurrency to one, advances the all-zero rule group at each
join point, and exposes `:stateful-step-count` as the round budget. Concurrent
machines remain outside the public contract because they require explicit
semantics for shared state and nondeterministic failure capture; deterministic
shrinking and final replay must not be implied for them.

An expensive external service may wrap the complete property run when a fresh
connection or session is the isolation unit and every body invocation restores
equivalent observable state. The service remains alive through generation,
shrinking, and final replay; per-case resources close in `finally`. Protocol
completion must be deterministic—for a request stream, half-close the write
side and drain time- and byte-bounded reads through actual EOF instead of
sleeping. A thrown case closes its fresh connection as the abort signal. Replay
against a live service is valid only while either path leaves the next case in
equivalent state and earlier candidates cannot affect later cases.

Value pools let the engine shrink dependencies between rules. libhegel stores
integer variable identities; the shared layer maps those identities to arbitrary
host values. Reusable draws retain an entry, consumed draws remove it, and an
empty draw becomes an assumption rejection. Pools belong to exactly one test
case and cannot be retained or reused.

## Verification contracts

Tests should verify the quality of the minimal result, not only that a run
fails. The suite therefore includes exact counterexamples for primitives,
collections, dependent generators, temporal bounds, and state-machine traces.
It also round-trips reproduction blobs, checks stable failure grouping, exercises
cleanup followed by another run, validates generated and deterministic seeds,
and runs through an independent consumer project.

Native changes require the complete Linux, Windows, and macOS matrix. Temporal
tests must continue to include a fixed leap day, nonzero microseconds, and the
nested datetime layout because the Windows x64 aggregate ABI differs from Linux
x86_64.
