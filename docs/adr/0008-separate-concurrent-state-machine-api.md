# ADR 0008: A separate concurrent state-machine API (design only)

- Status: Proposed (design only; no production API in this slice)
- Date: 2026-09-06

## Context

`hegel.stateful/run!` drives libhegel's state-machine protocol at concurrency
fixed to one ([ADR 0004](0004-delegate-stateful-testing-to-libhegel.md)).
`docs/DESIGN.md` already states the reason: libhegel 0.36.3 uses one round
protocol for sequential and concurrent machines, but concurrent machines
"remain outside the public contract because they require explicit semantics
for shared state and nondeterministic failure capture; deterministic
shrinking and final replay must not be implied for them."

`hegel.h` (`test/fixtures/hegel-0.36.3/hegel.h`) makes the concurrent contract
explicit at the C ABI level:

- `hegel_new_state_machine` accepts `rule_groups` (one concurrency group per
  distinct value), `min_concurrency`, and `max_concurrency`, and writes the
  engine-chosen worker count into `*out_concurrency`. Rules in the same group
  may run concurrently; rules in different groups never overlap.
- Creating a machine with `max_concurrency > 1` **declares the run
  nondeterministic**. The first such creation on a run not already known to
  be nondeterministic is rejected with `HEGEL_E_ASSUME` (report the case
  `HEGEL_STATUS_INVALID`, exactly like a failed assumption); the engine flips
  the run to nondeterministic at that case's end. Every later test case on
  that run is nondeterministic from the start.
- Once a run is flipped, it skips data-tree recording, novel-prefix
  generation, the nondeterminism-mismatch check, span mutation, the
  verify/shrink pass (and with it the flakiness check — generation stops at
  the first bug), targeting, and database persistence/reuse. **Failures from
  such a run carry no reproduce blob.** `hegel_run_result_status` reports
  `HEGEL_RUN_STATUS_FAILED_NONDETERMINISTIC` for these; the caller must
  capture and report from the discovering execution because there is no
  later replay to capture it from instead.
- `hegel_state_machine_next_rule` must be called by each worker `0 <=
  worker_index < concurrency` from its own `hegel_test_case_clone`, cloned
  once before the first round and held for the whole test case. The root
  handle stays with whoever drives `hegel_state_machine_next_group`.
- A state machine, pool, and collection each hold an internal lock: pools and
  state machines serialize concurrent use across clones instead of erroring;
  a collection instead returns `HEGEL_E_CONCURRENT_USE` under contention, so
  a collection must not be shared between two streams driven concurrently.
- `docs/ABI.md` documents four `:collect-safe? true` functions
  (`:state-machine-next-rule`, `:state-machine-rule-rejected`, `:pool-add`,
  `:pool-generate`) that the Jolt backend can additionally bind as
  `jolt.ffi` `:blocking` calls, "reserved for the concurrent state-machine
  executor, where a worker may park on a libhegel lock and must not prevent
  another host thread or GC from progressing." `state-machine-next-group!`
  is coordinator-only and ordinary; invariant checks and all frees remain
  coordinator-owned.
- The bounded characterization in `hegel.collect-safe-characterization`
  (`script/hegel/collect_safe_characterization.clj`) is prerequisite evidence
  only: it runs two Jolt futures with distinct contexts and test-case clones
  through the four collect-safe operations, reaches a real host-body failure
  after both workers enter, preserves the original throwable, publishes
  cooperative peer cancellation, forbids a second native pull, and joins
  before cleanup. It does not stand up a public executor, and its stable
  public runner still rejects concurrent use with
  `:hegel.core/unsupported-concurrent-state-machine`.
- [ADR 0006](0006-portable-library-and-canonical-abi.md) commits to one
  portable library and one canonical ABI across all backends; this proposal
  must not fork the ABI or the collect-safe route selection per host.

This ADR defines the first public-contract target for a distinct, opt-in
concurrent API before implementation lands. It is a design slice and commits
to no production code, but it fixes the v1 choices that implementation and
tests must enforce.

## Decision

Define a minimal separate namespace/entrypoint proposal and its contract
below. `hegel.stateful/run!` is unchanged and remains sequential-only
(concurrency fixed at 1,1). No part of this proposal is implemented in this
slice.

### 1. Public namespace and entrypoint

Add `hegel.stateful.concurrent` as a separate namespace, not a new arity or
option on `hegel.stateful/run!`. Concurrent semantics differ enough in result
shape, error taxonomy, and unsupported-feature set that overloading one
entrypoint would force every caller of the existing sequential API to read a
branching contract. The initial entrypoint is:

```clojure
(hegel.stateful.concurrent/run-test!
 {:workers 2 :test-cases 100 :seed 42}
 machine-fn)
```

It owns the outer libhegel run lifecycle because the existing
`hegel.core/run-test!` deliberately rejects
`HEGEL_RUN_STATUS_FAILED_NONDETERMINISTIC` before it can return a public
result. `opts` is the run configuration described in §3. `machine-fn` runs on
the coordinator once per test case, may make ordinary root-handle draws before
workers start, and returns:

```clojure
{:shared shared-system-or-model
 :close! (fn [shared] ...)
 :rules [(hegel.stateful.concurrent/rule
          :put :writes
          (fn [{:keys [shared worker-index round group cancelled?]}]
            :applied))]
 :invariants [(hegel.stateful.concurrent/invariant
               :consistent
               (fn [{:keys [shared round group]}] true))]}
```

`:close!` is optional. It runs once on the coordinator after all workers join
and join-point invariants finish, but before native outcome marking and
per-case release, so a teardown exception can still become the case outcome.
Once `machine-fn` returns a map containing a callable `:close!`, the runner
invokes it in a `finally` path even when later validation fails or the native
concurrency-flip case is rejected. An exception thrown by `machine-fn` itself
is a setup error, not a property counterexample: no concurrent machine has yet
declared the run nondeterministic, so the runner cleans up what it owns and
propagates that error rather than fabricating concurrent failure semantics.
The factory remains responsible for resources it acquires before it can return
a valid `:close!` contract.
`rule` and `invariant` are
new declarations because their callback and result contracts differ from the
immutable transition functions in `hegel.stateful`; reusing the old values
would make a single declaration mean two incompatible things.

### 2. Shared-state / operation / observation / join-point model

- **Rule groups.** A rule belongs to exactly one concurrency group. Groups
  execute disjoint rounds: rules in the same group may run concurrently;
  groups never overlap in time. This is a direct pass-through of the ABI's
  `rule_groups` model, not a new abstraction.
- **Shared state.** Unlike sequential `run!`, where model state threads
  immutably through `run-group!`, the value returned as `:shared` is passed
  unchanged to every worker and coordinator invariant. jolt-hegel neither
  wraps it in an atom nor merges worker return values. The caller owns all
  synchronization and the consistency of any model/SUT pair stored there;
  jolt-hegel controls only when workers may pull and invoke rules.
- **Operation.** One rule invocation by one worker in one round, mirroring
  the sequential trace unit but now attributed to a `worker-index` (matching
  the ABI's `worker_index`) as well as a rule name.
- **Observation.** What the join-point coordinator (and, for a failed run,
  the diagnostic capture path) can see: which rule each worker ran this
  round, in what order the workers reported completion, and whether each
  invocation was applied, rejected, or threw. Because there is no
  data-tree/replay path once a run is flipped nondeterministic (see §6), an
  "observation" here is a live diagnostic captured at discovery time, not a
  replayable trace entry.
- **Join point.** After every worker's rule stream for the current round is
  exhausted (each having received `HEGEL_STATE_MACHINE_DONE`), the
  coordinator joins them all, runs each intermediate invariant selected by
  `hegel_state_machine_should_check_invariant`, and only then calls
  `hegel_state_machine_next_group!`. Initial and final invariants are
  unconditional, as required by the ABI. This generalizes the existing
  `run-group!`/`state-machine-next-group!` split to N workers without running
  an invariant against an in-flight state.

### 3. Input validation

Validate `opts` before allocating a run and the value returned by `machine-fn`
before allocating a state machine or launching a worker. `:workers` is a
required run-wide fixed integer `>= 2`; `machine-fn` must return a map;
`:rules` is non-empty; rule and invariant
names are stable, nonblank, NUL-free, and unique within their kind; every rule
has one group; `:close!`, when present, is callable. The initial API passes
`min_concurrency == max_concurrency == :workers`, avoiding a drawn worker
count and the false suggestion that a seed controls scheduling.

Group identifiers are strings, keywords, or symbols. The implementation maps
their normalized, sorted distinct names to nonnegative `int64` ids; callers do
not supply native ids and therefore cannot collide with
`HEGEL_STATE_MACHINE_DONE`. Rule declaration order remains stable and is the
parallel order used for names and mapped groups.

The initial `opts` surface accepts `:workers`, `:test-cases`,
`:stateful-step-count`, `:verbosity`, `:seed`, `:derandomize?`,
`:suppress-health-checks`, and the
frontend-only positive integer `:max-diagnostic-events` (default 1024). It
rejects database/name keys, targeting phases, report-multiple-failures,
coverage, observations, and counterexample rendering instead of accepting
options whose sequential semantics libhegel disables. Unknown options and
unsupported hosts fail before a native run is allocated. A worker callback
must return exactly `:applied` or `:rejected`; a rejected operation calls the
worker-indexed native rejection route. Sequential `:precondition` is omitted
because a check-then-act split over shared state would invite a race.
The runner configures an empty native database internally.

### 4. Result and error schema

The sequential result and error shape assumes a reproduce blob and a shrunk
final replay for a property failure. Concurrent runs have neither, so this
entrypoint returns a separately versioned closed result shape:

```clojure
{:contract "hegel.concurrent-run"
 :contract-revision 1
 :passed? false
 :status :failed-nondeterministic       ; or :passed
 :seed "42"                            ; provenance, not a replay token
 :test-cases 2
 :valid-test-cases 0
 :invalid-test-cases 1
 :interesting-test-cases 1
 :worker-count 2
 :replay {:supported? false :reason :concurrent-scheduling}
 :shrinking {:supported? false}
 :persistence {:supported? false}
 :targeting {:supported? false}
 :flakiness-check {:supported? false}
 :failure {:phase :worker              ; or :join-invariant / :teardown
           :worker-index 0
           :round 3
           :group :writes
           :origin "hegel.stateful.concurrent/rule:put"
           :exception throwable
           :secondary []}
 :worker-results [{:worker-index 0 :status :error}
                  {:worker-index 1 :status :cancelled}]
 :diagnostics {:events []               ; bounded discovery prefix
               :total-events 7
               :dropped-events 0}}
```

Worker entries are sorted by `:worker-index`; failure-case diagnostic events
are retained in observation order up to `:max-diagnostic-events`, with total
and dropped counts making truncation explicit. This bound is independent of
`:stateful-step-count`: rejected rules do not advance a worker's per-round
continue decision and can produce more attempts than rounds. `:failure`,
`:worker-results`, and `:diagnostics` are absent on a passing result. The
primary worker failure is the first one published by the
first-writer cancellation record—an observed scheduling fact, not a
deterministic winner. Its original throwable object is preserved in
`:exception`; later same-round failures are retained in bounded `:secondary`
records rather than dropped. Teardown failure is primary only if no earlier
property failure exists; otherwise it is secondary so cleanup cannot erase the
discovering failure.

Failure origins mirror the sequential rule: preserve a truthy explicit
`:hegel/origin` from the user throwable; otherwise derive the stable fallback
`hegel.stateful.concurrent/rule:<name>` or
`hegel.stateful.concurrent/invariant:<name>`.

There is never a `:reproduction-blob`, `:replay-options`, `:final`,
`:reproduced?`, or sequential `:flaky?` key. Capability, usage, native setup,
and health-check failures throw after owned resources are released, matching
the existing distinction between a property verdict and inability to run.

- A concurrent failure uses `:status :failed-nondeterministic`, never
  `:status :failed` with an absent replay silently standing in for a present
  one. This mirrors `HEGEL_RUN_STATUS_FAILED_NONDETERMINISTIC`.
- A run that fails its first `max_concurrency > 1` creation attempt
  (`HEGEL_E_ASSUME`, case reported `HEGEL_STATUS_INVALID`) is not a property
  failure; it is the engine noting the run's nondeterminism flip and must
  not surface as `:status :error` or `:passed? false` — the case is simply
  invalid, and the *next* case on the same run is the one that actually
  starts running nondeterministically. That first flip case is included in
  the aggregate counts exactly like an ordinary assumption-rejected case; it
  does not consume the requested valid-case budget.

### 5. Capability reporting

Expose `hegel.stateful.concurrent/capability`, a structured preflight that
composes the existing ABI backend report with runtime, executor, route, and
native-version qualification without allocating a run. Its closed result is
either:

```clojure
{:status :supported
 :runtime :jolt
 :contract-revision 1
 :checks {:executor true
          :collect-safe-routes true
          :libhegel-compatible true}}
{:status :unsupported
 :runtime :jvm
 :reason :executor-unqualified
 :checks {:executor false}}
```

The initial supported host is Jolt only, because only Jolt futures plus the
collect-safe routes have the required progress, GC, cancellation, and cleanup
characterization. Babashka, JVM Clojure, jank, and ClojureCLR fail closed as
`:executor-unqualified`; there is no degraded concurrency-one fallback.
Qualifying another backend adds an executor adapter and evidence behind this
same portable namespace and canonical ABI—it does not fork the library or
signatures. A Jolt runtime is not sufficient by itself: missing collect-safe
routes, an unavailable/incompatible libhegel, or a failed executor preflight
produces `:unsupported` with the failing check and reason. `run-test!` requires
the supported result before allocating a native run.

### 6. Deterministic controls, and explicit non-replay semantics

This is the central guarantee this ADR must state, because it is the one
most tempting to silently assume:

**Concurrent mode does not provide deterministic replay, shrinking, the
example database, or flakiness detection, and this proposal must not imply
otherwise.** This follows directly from the ABI: once a run is flipped
nondeterministic, libhegel itself disables data-tree recording, novel-prefix
generation, the nondeterminism-mismatch check, span mutation, the
verify/shrink pass (and its flakiness check), targeting, and database
persistence/reuse, and failures carry no reproduce blob. A wrapper cannot
manufacture what the engine explicitly does not produce. Concretely:

- No `:seed` field promises replay for a concurrent failure the way it
  does for a sequential one (per `docs/DESIGN.md` "Seeds are always known").
  The seed may still be recorded for provenance, but recording it must not
  be described as making the run replayable, since scheduling — not the
  seed — determined what happened.
- No shrinking runs on a concurrent failure. The reported counterexample is
  exactly the discovering execution's captured trace, unminimized.
  Diagnostics must therefore capture full context eagerly at the failure
  site (mirroring "diagnostics should use `when-final`... to describe the
  minimal counterexample" in `docs/DESIGN.md`, which does not apply here —
  there is no minimal counterexample to describe).
- `:flaky?` as defined for sequential runs (mismatch between a discovered
  failure and its final replay) has no meaning in concurrent mode, because
  there is no final replay to mismatch against. This proposal must not reuse
  that key for a different meaning; if concurrent mode needs a "did this
  reproduce under a second attempt" signal, it needs its own key with its
  own explicitly different semantics, not an overload of `:flaky?`.
- No example database entry is written or read for a concurrent failure.
  Rerunning the same test therefore is not expected to reproduce the same
  interleaving; only re-running the same rule/group/state logic under a new
  race is expected.
- The one thing that *is* deterministic and controllable: `:workers` fixes
  the worker count precisely by passing equal native bounds without consuming
  entropy, and rule group partitioning is fixed by the caller, not drawn.
  Swarm subset selection per worker is still engine-owned and still drawn
  (per-worker, from the ABI), so which rules a given worker's subset
  includes is not caller-deterministic even when the worker count is fixed.

### 7. Host / resource ownership

Following the existing division of responsibility in `hegel.h` and
`docs/ABI.md`:

- The **coordinator** (the thread that calls `hegel_new_state_machine`,
  and later `hegel_state_machine_next_group!`) owns invariant checks and all
  free operations, exactly as ABI.md states: "Invariant checks and all free
  operations remain coordinator-owned because the concurrent protocol has no
  reason to issue them while workers may still hold shared native handles."
  This proposal keeps that split rather than distributing frees to whichever
  worker happens to finish last.
- Each **worker** owns exactly one `hegel_test_case_clone`, created once
  before the first round and held for the run's lifetime, and drives its own
  `next-rule!`/`rule-rejected!` calls on that clone only. A worker must never
  reuse another worker's clone, and a clone must not be handed to more than
  one live OS thread at a time (this is the ABI's `HEGEL_E_CONCURRENT_USE`
  boundary for test-case handles).
- State-machine handles are the only shared native handles exposed by v1, and
  only the executor touches them. Existing `hegel.stateful` pools and generic
  Hegel collections are not accepted in worker callbacks: their public APIs
  bind the root test case and do not provide the clone-scoped ownership this
  contract needs. The already characterized collect-safe pool routes remain
  internal prerequisites for a later clone-scoped pool API.
- Host-thread execution primitive: the collect-safe route
  (`docs/ABI.md`) exists specifically so a parked worker does not block
  another host thread or GC. This proposal requires that whatever host
  primitive drives workers (Jolt futures in the existing
  `hegel.collect-safe-characterization` script, or an equivalent per host)
  route v1's two worker operations (`state-machine-next-rule` and
  `state-machine-rule-rejected`) through their collect-safe `:blocking`
  bindings, and route `state-machine-next-group!` and all frees through the
  ordinary coordinator-only binding—never the reverse. The pool routes remain
  characterized prerequisites but are not used by this API version.
  Jolt futures are the initial executor. Other hosts remain fail-closed until
  an adapter proves the same protocol; §5 defines how they report that state.

### 8. Worker failure, cancellation, and cleanup

Per the characterization already run (ABI.md, `hegel.collect-safe-characterization`):
a real host-body failure after both workers have entered must (a) preserve
the original throwable, (b) publish cooperative peer cancellation so other
live workers stop pulling new rules rather than continuing to race against a
result that is already decided, (c) forbid a second native pull from a
worker that has already been told to stop, and (d) join every worker before
the coordinator proceeds to cleanup. This proposal adopts those four
properties as the required contract for a public executor, not as optional
behavior:

- **Failure propagation.** The first worker exception observed becomes the
  run's discovered failure (per §4, captured without shrinking). Later
  exceptions from other workers racing against the same round are not
  silently dropped; they must be attached as suppressed/secondary context on
  the same failure record, consistent with `docs/DESIGN.md`'s "observed
  failures grouped by stable origin" posture, generalized to same-round
  concurrent secondary failures rather than distinct rounds.
- **Cancellation is cooperative, not preemptive.** A worker mid-`step` is not
  forcibly interrupted; jolt-hegel signals "stop pulling further rules" and
  the worker's own `step` function is responsible for returning promptly.
  This proposal does not introduce thread interruption, kill, or timeout
  machinery — those are host/caller concerns, not this API's.
- **Cleanup and verdict order.** The coordinator joins every worker, completes
  applicable join-point invariants, calls `:close!`, frees clones and the
  state machine, marks the root test-case outcome, then releases the root
  case. A `:close!` exception is a `:teardown` property failure if there was
  no earlier failure and secondary otherwise. Native cleanup failures remain
  run errors, but cleanup is best-effort and cannot replace an earlier error.
  No mark or free may occur while a worker is live.
- **Cancellation record.** The executor owns one first-writer-wins promise per
  case. Every worker checks it before every native pull. The first failure
  delivers its structured identity; peers already inside a rule finish that
  rule and then return `:cancelled`. Each callback context includes a
  zero-argument `:cancelled?` predicate so long-running user code may stop
  cooperatively; it does not expose the mutable cancellation record. The API
  does not expose `future-cancel` or a user-supplied executor in v1.
- **No in-process timeout.** If a worker never returns from user or native
  code, `run-test!` does not return and therefore never frees shared native
  state underneath it. Test harnesses and applications that require a hard
  deadline must put the whole run in a separately killable process. This is a
  liveness limitation, but it preserves memory safety.
- **Rejected-rule progress.** A rejected rule retries its worker slot and does
  not consume the stateful round decision. The executor adds no unsound attempt
  timeout; it runs until native `HEGEL_STATE_MACHINE_DONE` or
  `HEGEL_E_STOP_TEST` (reported as an overrun). A worker stuck outside native
  choice exhaustion remains subject to the external-process rule above.

### 9. Supported vs. unsupported features (explicit exclusions)

Concurrent mode explicitly does **not** support, and must not silently
imply:

- Deterministic replay, sequence shrinking, minimal counterexamples, or the
  example database (§6).
- Flakiness detection in the sequential sense (§6); "did this fail again" is
  at most a caller-driven re-run, not an engine-verified flakiness check.
- Targeting/hill-climbing (`HEGEL_PHASE_TARGET`) — disabled by the engine
  once flipped nondeterministic, same as shrinking.
- Generic `h/draw!`, `h/note!`, `h/event!`, `h/observe!`, `h/target!`, and
  `hegel.stateful` pool operations inside worker callbacks. Binding the root
  dynamic test case on multiple threads would violate context/handle and
  diagnostic-render ownership. The coordinator `machine-fn` may make ordinary
  draws before workers launch, with the same explicit no-replay limitation.
- Swarm-subset introspection/control beyond what the ABI exposes: each
  worker's swarm subset is drawn once per worker per test case and is not
  independently configurable by the caller.
- Cross-run comparison or database-backed regression protection: nothing
  concurrent persists to `.hegel/`.

Concurrent mode **does** reuse, unchanged:

- The one portable library / canonical ABI commitment (ADR 0006) — no new
  ABI, no per-backend fork.
- The existing collect-safe route metadata and its fail-closed selection
  (ABI.md) for the four already-declared operations.
- Stable naming and origin conventions from `hegel.stateful`; the concurrent
  namespace has distinct rule/invariant values because worker callbacks and
  grouped execution are intentionally different contracts.
- The existing origin/error-identity model (`docs/DESIGN.md` "Failure
  identity and origins"): rule/invariant stable names remain the origin
  source; the origin model itself does not need concurrency-specific
  changes, only the replay guarantee built on top of it is removed.

### 10. Staged acceptance tests

Proposed staging, each stage a prerequisite for the next, mirroring how
`hegel.collect-safe-characterization` was already staged as "prerequisite
evidence, not a public concurrent executor":

1. **Pure validation and mocked protocol.** Reject an unsupported host and
   unknown/incompatible run options before run allocation. After a root case
   exists and `machine-fn` has made any coordinator draws, reject
   missing/duplicate names, invalid groups, callback return
   values other than `:applied`/`:rejected`, and malformed factory results
   before machine/clone/worker allocation. Mocked lifecycle tests require one
   context/clone per worker, coordinator-only join/invariant/free calls, sorted
   worker records, bounded diagnostic retention/counters, and exact cleanup.
2. **Nondeterminism flip observation.** Confirm the first
   `max_concurrency > 1` creation on a fresh run is reported
   `HEGEL_STATUS_INVALID` / case-invalid as specified, and that the
   subsequent case is correctly marked nondeterministic from its start
   (`hegel_test_case_is_nondeterministic`).
3. **Multi-worker happy path.** Two or more workers and two or more declared
   groups complete without failure. A pinned-seed bounded campaign reports
   nonzero observed rounds for every declared group rather than assuming the
   engine necessarily selects each one; it also verifies join-point ordering,
   sampled intermediate invariants, unconditional initial/final invariants,
   and per-worker swarm subsets.
4. **Failure capture without replay.** A rule deliberately throws; confirm
   the result carries `:status :failed-nondeterministic`, the
   discovering execution's diagnostic, no reproduction blob, and that
   generation stopped at first bug.
5. **Worker failure propagation, cancellation, cleanup** (extending the
   existing collect-safe characterization rather than duplicating it): a
   real host-body failure after multiple workers have entered must preserve
   the original throwable, publish cooperative cancellation, forbid a
   second pull from a cancelled worker, and join before free — run under an
   external process watchdog exactly as the existing characterization
   requires.
6. **Collect-safe route usage under load.** Confirm workers parked on
   next-rule/rejected do not block unrelated host threads or GC, using the
   same two-Jolt-futures shape as the existing characterization, generalized
   to the namespace's own entrypoint. Retain the existing pool-add/generate
   controls as prerequisite ABI coverage, not as claimed v1 surface coverage.

Each stage stays a *characterization* until the prior stage's evidence is
in; this ADR does not claim any stage is complete.

## Rejected alternatives

- **Overload `hegel.stateful/run!` with a `:concurrency` option.** Rejected:
  it would force every sequential caller to read a contract with a
  conditional non-replay carve-out, and it contradicts ADR 0004's decision
  to fix concurrency at one until a separate public contract exists.
- **Build a host-level STM/lock abstraction over the model state.**
  Rejected: libhegel does not provide or require one — its
  contract is purely about *when* a worker may pull a rule, not about
  synchronizing whatever the rule touches. Inventing one here would add a
  guarantee this ADR has no evidence libhegel needs or that callers agree
  on the right shape for.
  The v1 contract passes the caller's `:shared` value unchanged and leaves
  synchronization to caller code.
- **Promise best-effort replay via the recorded seed alone.** Rejected:
  the ABI is explicit that a flipped-nondeterministic run's failures carry
  no reproduce blob; a seed does not fix thread scheduling. Presenting a
  seed as sufficient for replay would misstate the actual guarantee and has
  already burned adjacent designs (see ADR 0007's insistence on not
  fabricating metadata an underlying contract doesn't supply).
- **Treat concurrent failures as flaky results reusing sequential
  `:flaky?`/`:observed-failures` semantics unchanged.** Rejected: those keys
  currently mean "engine detected a mismatch between discovery and replay."
  There is no replay in concurrent mode, so reusing the key would silently
  change its meaning for readers who already trust the sequential
  definition. A distinct status/key set (§4) is required instead.
- **Fork the ABI or add per-backend concurrent bindings outside the
  canonical descriptor.** Rejected outright: ADR 0006 commits to one
  portable library and one canonical ABI; this proposal reuses the existing
  `:collect-safe?` metadata and route-selection mechanism rather than adding
  a parallel one.
- **Ship a built-in cancellation timeout/watchdog inside the library.**
  Rejected for v1: the
  existing characterization explicitly relies on an *external* process
  watchdog because an in-process timeout cannot safely return into cleanup
  while a native worker may still be live. A hard deadline belongs outside
  the process until a later design proves a safe executor-level alternative.

## Consequences

- No production code changes in this slice; `hegel.stateful/run!` is
  unchanged.
- The initial contract is intentionally narrow: Jolt only, fixed worker count
  of at least two, caller-owned shared-state synchronization, coordinator-only
  invariants, cooperative cancellation, and discovery-time diagnostics.
- Any implementation must extend, not replace, the existing collect-safe
  characterization evidence (ABI.md, `hegel.collect-safe-characterization`)
  rather than starting a parallel evidence trail.
- The staged acceptance-test plan (§10) is a required prerequisite sequence,
  not a proposed test list to write all at once.
