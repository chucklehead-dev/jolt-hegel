# 0007: Versioned operation events without collapsing checker domains

Status: accepted for the next release; supported-host semantics (#22).
Compiler-aspect metadata remains separately experimental under #23.

## Context and inspected baseline

Hegel `2d122fb86d7bbe7e287a4e99f09e4a671e62859f` exposes two deliberately
different abstractions. `hegel.trace/check!` evaluates caller-selected rules;
`hegel.history/operations` requires a complete contiguous operation history
before a bounded linearizability search can begin. Making either call apply
the other's validation would be an incompatible change, not deduplication.

The inspected aspect-packs source is
`6be0648170921255cee437643d8efb175fc0f1d7` (the reviewed PR93 head).
Its `src/jolt/aspect_packs/history.clj` publishes integer operation IDs from
zero and contiguous sequence numbers from one. Invocations carry operation,
parent, context, causal links, site/build identity and shaped input; terminals
carry sequence, operation ID, phase and shaped value. Publication and sequence
assignment occur in one atom update/CAS. This source is a producer contract,
not Hegel's history validator.

Actual consumers in `models/jolt/aspect_packs/` include db, glitter, glimmer,
HTTP client/server and mycelium trace models, and the core_async bounded
history model. The db model uses synchronous parentage; async capture also
supports a carrier whose parent has already terminated. These are distinct
model obligations. At that baseline the ordinary test/conformance aliases pin Hegel
`3bc46499e0cfb5cf8fe67c30e997fbcae135ab47`; db corpus aliases separately pin
`88cc32cc3c39cb445fa16f725ff5f9c1db115858`. Updating only one pin is not a
cross-repository migration.

## Decision

The maintained top-level source scan also found
`jolt-otel-clickhouse/test/otel/exporter/chdb_property_test.clj` at
`8971ce58241ed236653fe7c84209579ae70027ac`. It uses legacy `:enter` and
`:connection/:close` events with `event-model`, not complete operation events;
its Hegel test pin is `d68ae004e45d9271d4d6378bdeb44eec6997c953`.
It must retain the generic trace API, not be migrated to the canonical profile
by renaming phases or inventing operation IDs for connection-close records.

Define one opt-in canonical operation-event profile and version it separately
from native replay blobs, corpus transport, compiler join points and individual
domain models. Keep the existing generic trace and bounded history entry points
compatible. A canonical-profile check must be explicit, not silently injected
into either legacy API.

Use the existing trace-envelope fields for transported identity:
`:contract-id "hegel.operation-events"`, `:contract-revision "1"`, and
`:events`. The envelope codec validates transport, not this semantic
profile; matching strings alone must never be described as semantic validation.

The profile defines:

- A complete bounded vector of map events, in observation order, with
  contiguous integer `:seq`. The expected initial sequence is explicit (one
  for an entire aspect-packs journal); filtered views cannot claim completeness
  merely because their remaining sequence numbers are increasing.
- Non-nil portable scalar operation IDs: integer, string, keyword or symbol.
  Each ID has exactly one `:invoke` followed by one `:return` or `:throw`.
  `:operation` is present on invocations. Domain models decide which operation
  values and input/outcome payloads are meaningful.
- Invocation `:parent-operation-id` and `:context-id` keys are present, with
  nil parent for a root. Each non-nil parent names one earlier invocation and
  the child retains its parent's context. Parentage means causality, not
  synchronous lifetime containment. Models may add nesting constraints.
- Invocation `:causal-links` is a unique vector of earlier operation IDs,
  sorted by the existing tagged scalar key (integer/string/keyword/symbol
  tags, then printed scalar spelling). This is not numerical integer sorting:
  the producer's multi-digit regression fixtures must remain in the gate.
  Terminals need not repeat parent, context or links.
- Extra metadata is preserved. Compiler site/build identity, payload privacy,
  scalar-text/transport limits and domain-specific closed shapes remain the
  producer/transport/model's explicit obligations. A semantic event bound is
  not a byte bound, redaction guarantee or authentication mechanism.

## Compatibility boundaries to preserve

| Existing API/rule | Intentional acceptance boundary |
| --- | --- |
| Generic `trace/check!` | Does not impose a universal event-map schema when no rule asks for one |
| `closed-lifecycles` | Accepts legacy `:enter`; does not independently require sequence or operation metadata |
| Sequence rules | Increasing allows gaps; contiguous requires adjacent values; explicit start detects a lost prefix |
| `history/operations` | Accepts `:invoke` only; requires sequence and operation keys; infers initial sequence unless supplied |
| History metadata | Preserves extra fields but does not validate causal/context semantics |
| Causal versus synchronous parentage | Async child may start or finish after its parent terminates |
| Generic operation IDs | Existing history/lifecycle callers are not narrowed to the canonical scalar subset |

Do not rewrite legacy `:enter` to `:invoke`, renumber filtered histories, fill
missing terminal events, drop incomplete operations or fabricate missing
metadata automatically. An explicit adapter, if needed, must name its input
contract and preserve evidence of the transformation.

History model results remain legal, illegal or inconclusive. Candidate-budget
exhaustion is not a negative linearizability witness. Types can check event and
result variants but cannot establish ordering, completeness, independence of
partitions or truth of a model transition.

## Acceptance evidence and integration

- Hegel implementation `c74762b89679674076834b2728252ec97894ffe1` passed
  [supported CI33987371982](https://github.com/chucklehead-dev/jolt-hegel/actions/runs/33987371982),
  all 18 jobs. Root inspected all ten supported runtime rows: both new scenario
  families actually ran at synthetic merge `d638d6fff24847d2637457555fa95743e06338bc`.
  Literal characterization and profile tests covered 14 tests/103 assertions;
  the final documentation delta adds an explicit nil-operation positive control.
- [Hegel PR89](https://github.com/chucklehead-dev/jolt-hegel/pull/89) records
  root review and two usable independent local Claude source/test/evidence
  reviews. The timed-out earlier characterization attempt is not approval.
  The scalar ordering helper was extracted without changing its branches or
  spelling; the existing mixed-ID/fan-in and independent history oracle gates
  remain in the aggregate suite.
- Aspect-packs candidate `0e29833b58f71598851801912aea6cdaa5a881d7` adds an
  atomic closed-journal `event-envelope`, independent literal identity pins,
  real return/throw/async-carrier/multi-digit fan-in fixtures and mutated-event
  and stale-identity controls. Its pure runner executes these alongside the
  existing history normalization/ownership suite: 19 tests/93 assertions on
  BB/JVM/Jolt, using the published Hegel SHA and unavailable libhegel.
- [Consumer CI33987863168](https://github.com/chucklehead-dev/jolt-aspect-packs/actions/runs/33987863168)
  passed all nine runtime/OS rows; the existing formal evidence gate also
  passed. [Packs PR96](https://github.com/chucklehead-dev/jolt-aspect-packs/pull/96)
  records independent Claude snapshot/test/CI review, ordinary test/conformance
  and core.async scenario pin updates. Existing db corpus provenance, dependency
  and setup-action pins remain intentionally separate and unchanged.
- Broader aspect-runtime comparison at source-matched Jolt
  `09f4218828f93320f3b43eb4e8ae5df76fc65210` has the same four pre-existing
  pending-put/close failures on baseline and candidate; candidate adds exactly
  six passing tests/28 assertions. See the evidence on
  [packs #14](https://github.com/chucklehead-dev/jolt-aspect-packs/issues/14).
  This is not a claim that the aspect runtime's entire core.async suite passes,
  nor does it weaken those ownership assertions. The generic OTel mixed-event
  consumer stays on its existing API; no forced migration is needed.

Final-head CI/review and merge provenance live on the linked PRs. Experimental
jank/CLR smoke success is compatibility evidence, not full host promotion.
Profile semantics, transport limits and model truth remain distinct contracts.
