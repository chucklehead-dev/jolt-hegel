# ADR 0004: Delegate stateful testing to libhegel

- Status: Accepted
- Date: 2026-07-22

## Context

libhegel exposes state-machine and value-pool primitives. It already owns
rule choice, sequence shrinking, a bounded attempt loop, termination, and random
nonempty swarm selection. Reimplementing those policies in host code would
duplicate engine behavior and weaken shrinking.

## Decision

Use libhegel's state-machine and pool APIs directly. Present rules as named
immutable state transitions and invariants as named predicates. Keep arbitrary
host values in a Clojure map keyed by libhegel pool variable identities. Treat false
preconditions and rule assumptions as skipped attempts; run invariants initially
and after successful rules. Drive libhegel 0.33's round protocol at fixed
concurrency one until a separate public contract covers concurrent state.

Do not add a separate public swarm combinator.

## Consequences

- Rule names and order are part of the replay contract and must remain stable.
- Failure data can expose minimal rule traces and stable rule/invariant origins.
- Pools are scoped to one test case and cannot be reused across cases.
- Stateful behavior follows the pinned libhegel version and must be revalidated
  when that pin changes.
