# 0007: Versioned operation events without collapsing checker domains

Status: proposed; implementation and cross-repository acceptance pending (#22).

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
model obligations. The ordinary test/conformance aliases pin Hegel
`3bc46499e0cfb5cf8fe67c30e997fbcae135ab47`; db corpus aliases separately pin
`88cc32cc3c39cb445fa16f725ff5f9c1db115858`. Updating only one pin is not a
cross-repository migration.

## Proposed decision

Define one opt-in canonical operation-event profile and version it separately
from native replay blobs, corpus transport, compiler join points and individual
domain models. Keep the existing generic trace and bounded history entry points
compatible. A canonical-profile check must be explicit, not silently injected
into either legacy API.

Use the existing trace-envelope fields for transported identity:
`:contract-id "hegel.operation-events"`, `:contract-revision "1"`, and
`:events`. These strings are a proposal until the implementation and consumer
gates below pass. The envelope codec validates transport, not this semantic
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

## Remaining acceptance before stabilization

1. Land literal positive/negative differential characterization on supported
   BB/JVM/Jolt hosts, including missing metadata, sequence shifts/gaps,
   legacy phases, asynchronous lifetime and incomplete/empty histories.
2. Implement and test the explicit canonical-profile checker and its bounded
   evidence/usage-error contract. Include malformed and semantic negative
   controls; reuse existing rule policy without weakening generic APIs.
3. Exercise actual aspect-packs producer/model fixtures against it; inventory
   remaining maintained consumers and update all affected pins together.
   Preserve caller assertions outside fail-open instrumentation advice.
4. Pin profile identity in the consuming fixture/transport metadata where used;
   reject stale/unknown identities at that profile boundary. Keep #23 compiler
   matching/versioning work distinct from event semantics.
5. Complete root and independent Claude review plus applicable CI. Update
   README, changelog and skill/API guidance before removing experimental
   markers. This proposed ADR alone does not complete #22.
