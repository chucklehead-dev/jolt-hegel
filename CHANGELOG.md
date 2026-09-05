# Changelog

Notable changes to jolt-hegel are recorded here. Release links compare the
exact public tags in `chucklehead-dev/jolt-hegel`. This file begins at v0.5.0;
for v0.1.0 through v0.4.0, see the tag and release history in that repository.

## Unreleased

- Add the explicit `hegel.operation-events` revision 1 profile through
  `hegel.event-contract/check!` and `check-envelope!`. Generic trace/history
  callers retain their existing event domains; profile validation is separate
  from transport, domain models and bounded linearizability search. See
  [event contracts](docs/EVENT_CONTRACT.md) and proposed ADR0007 for the
  cross-repository stabilization gates.

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

[0.5.0]: https://github.com/chucklehead-dev/jolt-hegel/compare/v0.4.0...v0.5.0
