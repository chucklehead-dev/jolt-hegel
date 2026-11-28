# Changelog

Notable changes to jolt-hegel are recorded here. Release links compare the
exact public tags in `chucklehead-dev/jolt-hegel`. This file begins at v0.5.0;
for v0.1.0 through v0.4.0, see the tag and release history in that repository.

## Unreleased

## [0.7.0] - 2026-11-28

- Export successful stateful command histories through seeded materialization,
  select ordered outcomes, and require valid cases with a completed final check.
  Census-based rule coverage can also require a minimum eligible selection ratio.
  [Stateful evidence](docs/STATEFUL_EVIDENCE.md)

- Missed stateful rule minima now show selections, guard and assumption
  rejections, and applications from valid cases. With a census, the report
  includes eligible steps and the number of steps sampled for that rule.

## [0.6.1] - 2026-11-28

- Check the final model and external store with `:final-check` in
  `hegel.stateful.journal/run!`. A failing check can shrink and return the
  confirmed command journal. Correlate journal IDs with coverage keys using
  `hegel.stateful/rule-id`.
- Set valid-case coverage minima for rule selections, guard rejections, and
  eligible steps. Eligibility requires `:census? true`.
- Honor proxy environment variables through curl in the JVM installer. The
  consumer setup example now uses a dedicated alias, and requiring
  `hegel.generator` no longer prints a warning about `let`.

## [0.6.0] - 2026-11-28

- Require aggregate sequential state-machine rule coverage and diagnose guard
  rejection, selection and failure. Export a bounded redacted command journal
  from a confirmed failure. [Examples and semantics](docs/STATEFUL_EVIDENCE.md).

- A replayed failure with status `:not-reproduced` now carries a `:reason`.
  When another failure replaces it, `:observed-origin` identifies that failure.

- Embedded `clojure.test` assertions use the reported source location in their
  failure origin. Separate lines containing the same assertion can now shrink
  independently. Hosts that omit assertion locations retain form-based origins.
  Recorded assertion origins from earlier versions can differ on replay.
- Time and datetime constructors reject a bound containing both fractional
  fields and identify the ambiguous bound.
- Qualify Jolt 0.8.31 and resolve explicit shared-library overrides against
  their own symbols when a packaged libhegel is already loaded. Statically
  linked Linux and macOS executables reject a nonblank override at startup.

- Collection generators treat `:size` as exact. For example, `{:size 3
  :min-size 1 :max-size 5}` generates three entries. If a supplied bound
  excludes that size, construction fails. Omit `:size` to generate a range.
- Trace and history predicates propagate rejected draws and runner errors.
  Event models reject non-map entries, including nil and false, before
  invoking the model.

- Upgrade to libhegel 0.53.2. Stateful sampled invariants now run with at
  least 1% probability even when the step budget is very large. Shrinking
  abandons a mutation after five consecutive continuations run out of data.
- Generate times and datetimes at a requested precision, including milliseconds
  and nanoseconds. Restrict a date to selected weekdays and months. Existing
  generators keep their microsecond default. Draw UTC instants as portable
  epoch-second/nanosecond maps with `g/timestamp`. See the [temporal examples](docs/UPSTREAM_ADOPTION.md#calendar-and-clock-properties).
- Extract confirmed inputs with `hegel.report/failure-fixtures`. Each entry
  records the failure origin and explicit input, including nil and false.

- Add `g/grammar`, a Clojure data DSL for native grammar string generation and
  shrinking, plus pure `hegel.grammar/compile` for inspection. Validate references,
  operators, bounds, Unicode ranges and nonproductive rules with data paths.
  See [grammar examples](docs/GRAMMARS.md); raw `grammar-str` remains available.

- Adopt lazy Jolt native dependencies and static Linux/macOS packaging through
  the optional `packaging/jolt` root, with verified artifacts and relocation
  tests. Check Jolt's stack-argument ABI capability when available.
- Reuse compiled native generators within each test case, combine component
  labels for shrinking, restore consumed pool values after speculative discard,
  and adopt native default sizes and filter retry counts.
- Add named profiles with concrete replay settings, `:unbounded-choices?`,
  `:print-blob?`, `grammar-str`, weighted stateful rules, source-aware Antithesis
  reporting and fault-paused invariants. Preserve observed failures from
  unrecorded Antithesis workloads without claiming they were replayed.
  See the [adoption report](docs/UPSTREAM_ADOPTION.md) for inherited engine fixes
  and remaining platform/API work.

- Migrate through libhegel 0.51.6 from the new `hegeldev/hegel` monorepo:
  audit all 159 C functions, regenerate jank/CLR bindings, use length-delimited
  UTF-8 views and aggregate returns, and adopt the state-machine builder API.
  Saved native blobs and databases are version-specific; regenerate old failures.
- Retain native `:output` and `:diagnostic` on reported failures. Quiet runs
  preserve the original exception and any explicit fixture input.
- Drive native capture/confirmation and blob replay runs. `final?`, `when-final`
  and `fprn` now follow native capture requests, which can occur repeatedly;
  final result diagnostics and clojure.test reports select the retained failure.
  Add `:stop-on-first-failure?`, `:nondeterminism-strictness` and native caveats.
  Regex generators accept embedded NUL alongside Unicode text.
- Raise the JVM minimum to JDK 25 and use released `org.babashka/ffi` 0.1.3.
  Qualify Babashka 1.13.230, update Malli to 0.20.3, Typed Clojure to 1.4.0
  with explicit namespace opt-in and structured error controls, ClojureCLR to
  1.12.6, and refresh the signed-index jank package pin. These changes are for
  the next release; published v0.5.0 is unchanged. See the
  [migration guide](docs/LIBHEGEL-051-MIGRATION.md).

- Qualify the development checkout with official checksum-pinned Jolt 0.8.30 assets on
  Linux x86_64, Windows x86_64, and macOS arm64 while retaining a full Jolt
  0.8.1 Linux compatibility-floor lane. The final release verification uses 0.8.31. The published v0.5.0/Jolt 0.7.23 contract is unchanged, and a
  current-release arena remeasurement continues to reject a fresh arena per
  temporary-allocation wrapper.

- Record labelled draws and `note!` in the native test-case document, with native speculative rollback for rejected filters, duplicate
  vector/set/map candidates, and recursive retries. Final failures now retain
  a bounded structured `:counterexample` snapshot. Redaction precedes custom
  rendering; raw drawn values are not retained, whole entries are bounded
  before native append, and diagnostic failures cannot change the property
  verdict. See [structured counterexample diagnostics](docs/COUNTEREXAMPLES.md).

- Documented the Jolt and babashka.ffi backend tradeoffs, retained the two
  explicit adapters and Jolt's explicit temporary-pointer ownership, and added
  a cross-runtime contract that checks every canonical ABI signature and
  intentional carrier-policy difference. See
  [the FFI backend evaluation](docs/FFI_BACKEND_EVALUATION.md).

- Add a dev-only, bounded Typed Clojure pilot (`clojure -M:typed-check`) that
  externally annotates the unchanged `hegel.internal.portable-data`. Only
  `text-size`'s body is actually checked; `validate!`'s iterative loop over
  unannotated private helpers cannot be checked without editing `src`, so its
  external annotation is `^:no-check` — its polymorphic
  Limits/InvalidFn/input-result contract is still enforced at a valid driver
  and four named intentionally invalid control namespaces whose exact
  structured errors are checked, but its production body is trusted, not
  checked; runtime validation and existing behavioral tests remain
  authoritative. The optional `:typed-check` alias and its checker dependency
  are invisible to normal consumers. See
  [the Typed Clojure pilot](docs/TYPED_CLOJURE.md) for the exact
  checked/trusted boundary, the two errors an actual run reported, and
  expand/constrain/reject criteria.

- Make compiler join-point acceptance checks independent of assertion settings,
  with assertion-elision and rejected-report/event-journal controls. Clarify
  that the existing manifest version is an opaque compatibility token, not
  declaration source provenance; its value is unchanged.

- Stabilize the optional compiler join-point declaration contract without
  changing its existing identifiers or opaque manifest compatibility token. Exact
  source/provider pins remain independent. Pure cross-host manifest tests and
  a pinned-source Linux compiler gate cover actual matching, runtime advice,
  exception preservation, stale-provider rejection and plain builds. See
  [join-point contracts](docs/JOINPOINTS.md); ordinary property use still needs
  no aspect-capable compiler or instrumentation provider.

- Add `g/big-integer` with explicit arbitrary-width inclusive integer bounds
  and caller-owned canonical ABI buffers, and `g/float32` with exact bound
  validation, native width-32 generation, subnormals and widened-double results.
  Existing int64 `g/integer` and width-64 `g/double` behavior is unchanged.
  New numeric contracts are tested on BB/JVM/Jolt; experimental-host numeric
  qualification remains separate.

- Add the explicit `hegel.operation-events` revision 1 profile through
  `hegel.event-contract/check!` and `check-envelope!`. Generic trace/history
  callers retain their existing event domains; profile validation is separate
  from transport, domain models and bounded linearizability search. See
  [event contracts](docs/EVENT_CONTRACT.md) and ADR0007 for the supported
  trace/history compatibility boundaries and cross-repository evidence.

- Add categorical `event!`, numeric `observe!`, native `:show-statistics?`,
  bounded frontend `:observations?` and explicit `:coverage` requirements.
  Coverage failures are run-level results, not fabricated counterexamples;
  final replay is excluded and unavailable native subphases are not inferred.
  See [observation contracts](docs/OBSERVATIONS.md) for scope and counting rules.

- Add seeded materialization and bounded corpus envelopes with exact UTF-8
  SHA-256 integrity relative to independent consumer pins. Generation requires
  one successful native valid-case run per position; consumption needs neither
  libhegel nor a model dependency. Cross-platform/offline and coordinated db
  pilot evidence landed in Hegel PR86 and aspect-packs PR93; this is not a
  release declaration.
- Add bounded, versioned EDN counterexample bundles and direct native replay
  with explicit provenance compatibility checks. Results capture present
  replay settings; exports omit host exceptions and opt out of traces by
  default. Replay is for trusted artifacts, not a sandbox for hostile blobs.
- Add `g/permutations`, `g/subsequences`, and `g/samples`. They realize finite
  inputs once and return vectors while retaining duplicate source positions;
  replacement samples keep an unbounded default maximum size for nonempty
  inputs. Full permutations intentionally use `O(n²)` positional removal to
  preserve native choice shrinking.
- Make Jolt resource lookup prefer classpath and standalone embedded resources,
  while retaining the source-root fallback. Standalone consumers must embed
  the staged resources directory (with names rooted at `hegel/abi.edn`) and
  deploy and verify the native library separately.
- Preserve the original generator exception when owned span, collection,
  recursion, string, or context cleanup also fails. A cleanup-only failure
  still surfaces; native-discarded recursive retry spans are not closed during
  host unwinding, and no secondary throwable is mutated or attached.
- Upgrade libhegel to 0.36.3 with its complete 103-function canonical ABI and
  regenerated experimental backends. Native time fields now use nanoseconds;
  public `time`/`datetime` retain microsecond bounds and six-digit precision
  through an explicit full-bucket, round-down adapter. Seed/choice streams
  and reproduction blobs are not promised compatible across engine versions.
- Remove the obsolete `:mode` run option with an actionable pre-native usage
  error. `:test-cases 1` is a valid-case budget, not the former no-shrink
  single-test-case mode. Every-step stateful invariant checking remains the
  default; new native sampling, printer and observation APIs are not silently
  enabled by the upgrade.
- Record native stateful-rule spans around complete rounds so the native
  shrinker can attempt whole-step deletion, while retaining existing invariant
  timing and preserving property errors during span cleanup.
- Add `hegel.history/analyze` with decisive linearizability results and an
  explicit global `:max-search-steps` bound (default 100000). Exhaustion is an
  inconclusive marked abort, never a shrinkable false linearizability verdict.
- Audit the canonical ABI against the byte-pinned C header, compiler-measured
  layouts and constants, source option/status/span values, and explicit
  temporal units. Add offline development commands and three-platform CI.
- Give native installers invocation-owned staging files and publish verified
  bytes without deleting the previous cache entry first. Concurrent installers
  accept only checksum-matching winners; unsupported atomic replacement fails
  without removing the existing target.
- Tighten public option validation: documented option maps now reject unknown
 keys, documented booleans must be booleans, and invalid public configuration
 is classified as a usage error before native execution where feasible.
- Add bounded direct self-recursion to the optional Malli adapter, including
  engine-owned depth, leaf-budget, retry, and subtree-hoisting shrink behavior.
- Publish experimental compiler-derived Jolt aspect join points on
  `hegel.core/run-test!` and `hegel.stateful/run!` for property and
  state-machine runs.

## [0.5.0] - 2026-08-31

- Replace the development Babashka fork and private JVM adapter with the
  upstream `babashka.ffi` implementation shared by Babashka and JVM Clojure.
- Preserve the canonical libhegel ABI descriptor and common property API
  across Jolt, Babashka, and JVM Clojure.
- Require FFI-capable Babashka 1.13.220 or later and JDK 22 or later for the
  supported JVM host.

[0.7.0]: https://github.com/chucklehead-dev/jolt-hegel/compare/v0.6.1...v0.7.0
[0.6.1]: https://github.com/chucklehead-dev/jolt-hegel/compare/v0.6.0...v0.6.1
[0.6.0]: https://github.com/chucklehead-dev/jolt-hegel/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/chucklehead-dev/jolt-hegel/compare/v0.4.0...v0.5.0
