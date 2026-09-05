# Canonical operation events

The unreleased `hegel.event-contract` API implements the explicit
`hegel.operation-events` revision `1` profile. Supported-host semantics and
cross-repository evidence are recorded in
[ADR0007](adr/0007-versioned-operation-events.md).
It is portable pure Clojure data checking: no native engine or aspect-enabled
compiler is required. Native generation can produce inputs whose resulting
events are checked, but the checker does not generate or record events itself.

```clojure
(require '[hegel.event-contract :as events])

(events/check!
 [{:seq 1 :operation-id :request :phase :invoke :operation :read
   :parent-operation-id nil :context-id nil :causal-links []}
  {:seq 2 :operation-id :request :phase :return :value :ok}])
```

`check!` returns the original vector, including its metadata and application
fields. Its optional second argument is a closed map:

| Option | Contract |
| --- | --- |
| `:max-events` | Positive integer; default 256. Bound the complete input before rule evaluation. |
| `:sequence-start` | Integer; default 1. Supply the independent expected start, not the first event of a potentially truncated journal. |

Every event must be a map with an integer `:seq`, a scalar `:operation-id`
(integer, string, keyword or symbol), and phase `:invoke`, `:return` or
`:throw`. Sequence numbers are contiguous from the selected start. Every ID
has exactly one invocation followed by exactly one terminal event. Empty
complete vectors are valid; incomplete operations are not automatically
dropped, completed or repaired.

Invocations contain `:operation`, `:parent-operation-id`, `:context-id`, and
`:causal-links`. The operation key must be present; domain models decide its
allowed values (the profile alone does not reject an explicit nil). Root
parent IDs are nil. A non-nil parent ID names exactly one earlier invocation,
and the child has the same context as that invocation. Context values are
not narrowed by this profile. A parent can finish before its child begins:
causal parentage does not establish synchronous lifetime nesting.

Links are unique vectors of earlier invocation IDs, sorted by the existing
tagged scalar spelling order: integers, strings, keywords, then symbols, each
in string order of its canonical key. Integer links `[10 2]` are sorted in
this order; `[2 10]` are not. Empty links are `[]`, not nil or an omitted key.
The checker verifies rather than silently sorting or deduplicating them.
Terminals need not repeat parent, context, or causal links.

## Envelope identity and transport

`check-envelope!` accepts exactly these keys and returns the same envelope:

```clojure
{:contract-id "hegel.operation-events"
 :contract-revision "1"
 :events [...]}
```

It compares identity to the implemented profile, then checks semantics with
the same options as `check!`. Unsupported identity is rejected, not inferred
from event shape. In a replay bundle, this can be the existing optional trace
envelope; the bundle's transport schema does not need a new version. Bundle
decoding only checks its transport constraints. Call `check-envelope!`
explicitly if the application requires this semantic profile.

This profile is not an authenticated assertion, a digest check, or a portable
EDN transport validator. Arbitrary extra fields, input/value payloads and
context values are preserved, not redacted. Application models still validate
operation names, payload shapes and observed results. Transport still bounds
bytes, depth and scalar encoding. Neither replaces the other.

## Errors and bounded evidence

Invalid checker options and malformed/unsupported envelope identity throw
`:hegel/usage-error? true` with `:hegel.event-contract/invalid-options` or
`:hegel.event-contract/invalid-envelope`. They are setup errors, not shrinkable
property counterexamples. Invalid-envelope data does not echo its raw payload.

Event input and semantic failures retain the `hegel.trace` taxonomy:

- Nonvectors: `:hegel.trace/invalid-trace`, with value type, not raw input.
- Oversized vectors: `:hegel.trace/event-bound`, with counts and no event
  vector. A bound failure is not silent truncation.
- Failed shape/sequence/lifecycle/parent/link/context rules:
  `:hegel.trace/rule-failed`, a stable origin under
  `hegel.trace/hegel.operation-events/`, and the bounded complete event vector.

The event-count bound does not bound payload bytes or redact sensitive values
from failure evidence. Shape payloads before collection when necessary.
Take snapshots only after the required operations/workers have completed;
atomic sequence publication is necessary but does not itself prove quiescence.
Run assertions outside fail-open aspect advice.

## Existing APIs and migration

No existing caller is automatically migrated. Generic trace rules remain
composable: legacy `:enter`, non-operation events, filtered/increasing
sequences and custom event models remain useful. `hegel.history` still accepts
its complete contiguous histories, infers their initial sequence unless given
one, and does not validate extra parent/context/link metadata. Its generic ID
domain is not narrowed to the canonical profile's scalar IDs.

Use explicit profile validation before a domain model or bounded history
search when the producer claims revision 1. A valid event profile does not
establish linearizability; search exhaustion remains inconclusive, never an
illegal-history witness. Synchronous models may additionally require nested
lifetimes. Changes to required fields, ID ordering or acceptance semantics
require a new profile revision with explicit producer/consumer migration.
Compiler manifest IDs and domain-model revisions remain separately versioned.
