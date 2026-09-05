# jolt-hegel

Property-based and stateful testing for Jolt, Babashka, and JVM Clojure, backed
by the [Hegel](https://hegel.dev/) generation and shrinking engine.

Examples are good at confirming cases you already thought of. A Hegel property
describes a larger truth: values are generated across the input domain, a
failure is reduced to a small counterexample, and the final case is replayed
before the result is reported. The seed in every result makes the run
repeatable.

jolt-hegel exposes one Clojure API on all three hosts. It calls the same
libhegel 0.36.3 C ABI directly through `jolt.ffi` or the upstream
`babashka.ffi` library (which uses the final JDK Foreign Function & Memory API
on the JVM). There is no service to start and no subprocess protocol.

Experimental ports reuse that same implementation and boundary on jank and
ClojureCLR. They have focused cross-platform CI but are not yet part of the
supported release contract.

## Current main status

This table describes the support floor exercised by the current `main` source
and its next-release work. It is not a release announcement; the published
`v0.5.0` contract remains the one recorded by that tag, including Jolt
0.7.23+.

| Host | Contract | Continuously tested targets |
| --- | --- | --- |
| Jolt 0.8.1+ | Supported on current `main` | Linux x86_64, Windows x86_64, macOS arm64 |
| FFI-capable Babashka 1.13.220+ | Supported | Linux x86_64, Windows x86_64, macOS arm64 native images |
| JVM Clojure, JDK 22+ | Supported; JDK 25 is primary | Linux x86_64, Windows x86_64, macOS arm64, plus a Linux JDK 22 minimum gate |
| jank | Experimental focused suite | Linux x86_64 and macOS arm64 |
| ClojureCLR 1.12.2 on .NET 8 | Experimental focused suite | Linux x86_64, Windows x86_64, macOS arm64 |

The current release is `v0.5.0`. It replaces the development Babashka fork and
the private JVM FFM adapter with the upstream `babashka.ffi` implementation,
while preserving one canonical ABI and the same property API across Jolt,
FFI-capable Babashka, and JVM Clojure. Consumer Git dependencies should pin the
tag's full peeled commit SHA, not the tag-object SHA or a moving branch
reference.

Releases follow Semantic Versioning. While the project is pre-1.0, SemVer
itself places no compatibility requirement on `0.y.z` releases; jolt-hegel
additionally commits to confining incompatible changes to the supported API to
minor releases. Namespaces and hosts marked experimental are outside the
supported compatibility contract: they may change incompatibly in a minor
release, with the change called out in the [changelog](CHANGELOG.md). Promotion
into the supported contract will be explicit in release notes and the status
table above.

## Acknowledgments

The original jolt-hegel design and implementation were based in part on Kyle
Kingsbury (Aphyr)'s [hegel-clj](https://github.com/aphyr/hegel-clj), including its
imperative generator style, `run-test!` and `clojure.test` integration, and
final-replay diagnostics. The project also builds on upstream Hegel and its
libhegel engine. jolt-hegel remains an independent Clojure-family library with
one public behavior across its supported hosts.

## What should be a property?

Property tests are most useful when many inputs should obey one durable rule:

- encoding and decoding should round-trip;
- normalization should be idempotent;
- a result should stay inside documented bounds;
- optimized code should agree with a simpler model;
- a command sequence should leave a real system and its model in the same
  state; or
- arbitrary valid input should never crash a parser or protocol handler.

Keep exact examples for named edge cases and user-visible output. Add a
property where the input space or interaction sequence is the thing you cannot
usefully enumerate.

## A first property

Suppose an application serializes user records. This test explores record
sizes, Unicode names, optional values, role combinations, and UUIDs while
asserting the contract that matters:

```clojure
(ns example.codec-test
  (:require [clojure.test :refer [deftest is]]
            [example.codec :as codec]
            [hegel.clojure-test :refer [with]]
            [hegel.core :as h]
            [hegel.generator :as g]))

(def user-record
  (g/hmap
   {:id (g/uuid)
    :display-name (g/string {:max-size 80})
    :nickname (g/optional (g/string {:max-size 40}))
    :roles (g/set {:max-size 6}
                  (g/sampled-from [:reader :author :admin]))}))

(deftest user-codec-round-trips
  (with {:test-cases 500 :database "" :verbosity :quiet}
    [user user-record]
    ;; Printed only for the final, minimized replay.
    (h/fprn :minimal-user user)
    (is (= user (codec/decode (codec/encode user))))))
```

`hegel.clojure-test/with` behaves like an ordinary `deftest`: a successful
property reports one pass. If an assertion fails, exploration output is held
back while Hegel shrinks the input, then only the final minimal failure is
reported through `clojure.test`.

Dependent draws can be expressed inside the property with `g/let`:

```clojure
(g/let [size (g/integer 1 256)
        payload (g/vector {:size size} (g/octet))
        chunks (g/chunkings payload)]
  {:payload payload :chunks chunks})
```

Here `chunks` always concatenate to `payload`. Hegel can shrink both the data
and its delivery boundaries, which makes this useful for parsers, sockets, and
streaming APIs.

## Shrinking and replay

When a property throws, jolt-hegel gives the failure a stable identity, lets
libhegel minimize the choices that produced it, and executes the minimized case
one final time. Put expensive diagnostics behind `h/final?`, `h/when-final`, or
`h/fprn` so hundreds of exploratory cases stay quiet.

Every result includes its selected seed as a decimal string. Rerun it with
the same engine, property contract and settings:

```clojure
(defn packet-property [_]
  (let [packet (h/draw! packet-generator)]
    (when-not (= packet (decode (encode packet)))
      (throw (ex-info "packet round-trip failed"
                      {:hegel/origin "packet-codec/round-trip"})))))

(def result
  (h/run-test! {:test-cases 500 :database "" :verbosity :quiet}
               packet-property))

(def replay
  (h/run-test! {:test-cases 500
                :seed (bigint (:seed result))
                :database ""
                :verbosity :quiet}
               packet-property))
```

Use a fixed, assertion-site origin such as `packet-codec/round-trip`; generated
values do not belong in the origin. `:passed? false` is a property verdict, not
an exception from `run-test!`, so a custom runner must check it. For a suite
that should count failures and continue, use `hegel.report/counting-runner`
with `hegel.report/run!`.

The result map also records case counts, minimal failures, final replay data,
observed failure summaries, and flakiness. A result with `:flaky? true` means
the same generated choices did not reproduce the same outcome; fix shared
state, timing, or other nondeterminism before trusting its counterexample.

### Portable counterexample bundles

Use `hegel.replay-bundle/from-result` and `hegel.replay-bundle.codec` to export
a bounded EDN artifact from a stable failure. `hegel.core/replay-bundle!`
compares an independent deployment/property manifest and directly replays the
native blobs; it does not substitute a seed rerun. See
[replay bundles](docs/REPLAY_BUNDLES.md) for provenance, redaction, result
variants and the trusted-artifact boundary. Matching provenance and bounded
EDN do not make untrusted native blobs safe to execute.

### Seeded corpora (unreleased)

`hegel.materialize/materialize!` generates an exact bounded count of successful
plain-data examples. `hegel.corpus/consume!` verifies their exact payload digest
and provenance against an independent pin before releasing values; consumption
does not load libhegel. See [materialized corpora](docs/MATERIALIZED_CORPORA.md)
for the seed policy, platform crypto requirements, bounds, and verified
cross-platform/db-pilot evidence. Corpus integrity is not proof that
the values satisfy a model or are safe to publish.

### Observations and coverage (unreleased)

Use `h/event!` for categorical labels and `h/observe!` for numeric measurements.
`:observations? true` returns bounded structured counts; `:coverage` adds explicit
run-level requirements and makes missed coverage a failing result. Native
`:show-statistics?` printing remains a separate diagnostic facility. See
[observations and coverage](docs/OBSERVATIONS.md) for phase scopes, valid-case
denominators, zero-hit controls and statistical limitations.

## Stateful and swarm testing

A function can be correct in isolation while a sequence of operations is not.
`hegel.stateful/run!` compares a real system with a simpler model across
generated command sequences. libhegel selects and shrinks the rules, sequence
length, and a random nonempty swarm subset for each case.

```clojure
(ns example.stack-test
  (:require [clojure.test :refer [deftest]]
            [hegel.clojure-test :refer [with]]
            [hegel.core :as h]
            [hegel.generator :as g]
            [hegel.stateful :as hs]))

(deftest stack-agrees-with-a-vector-model
  (with {:test-cases 200
         :stateful-step-count 50
         :database ""
         :verbosity :quiet}
    []
    (let [stack (atom [])]
      (hs/run!
       {:initial-state {:model [] :sut stack}
        :rules
        [(hs/rule
          :push
          (fn [{:keys [sut] :as state}]
            (let [value (h/draw! (g/integer))]
              (swap! sut conj value)
              (update state :model conj value))))
         (hs/rule
          :pop
          {:precondition #(seq (:model %))}
          (fn [{:keys [sut] :as state}]
            (swap! sut pop)
            (update state :model pop)))]
        :invariants
        [(hs/invariant :model-matches-sut
                       #(= (:model %) @(:sut %)))]}))))
```

A false precondition or `h/assume!` inside a rule skips that attempted rule.
Invariants run on the initial state and after each successful rule. Keep rule
names and order stable for replay, and create mutable state inside the property
body so generation, shrinking, and final replay each begin fresh.

Swarm selection is especially useful for systems with many features: one case
might exercise only create/read operations, another create/update/delete, and
another the full rule set. This explores feature interactions without requiring
you to hand-author each subset or a second rule-choice loop.

libhegel also defines nondeterministic concurrent state machines. They are not
exposed by `hs/run!`: declaring concurrency above one disables shrinking,
replay, targeting, persistence, and flakiness checks upstream, while the
current Clojure API deliberately models each rule as a deterministic
`state -> state` transition. A future concurrent API will use a distinct
shared-state and join-point contract rather than silently weakening this one.

### Reusing generated resources with pools

State machines often create identifiers, handles, or files that later rules
must reuse. A pool lets libhegel track and shrink that dependency:

```clojure
(let [handles (hs/pool)]
  ;; In a create rule:
  (hs/add! handles newly-created-handle)

  ;; In later rules:
  (h/draw! (hs/values-reusable handles)) ; select and keep
  (h/draw! (hs/values-consumed handles))) ; select and remove
```

An empty-pool draw skips a stateful rule. Pools belong to one generated case;
never retain one between cases.

## Generators

The built-in surface covers:

- signed int64 and arbitrary-width integers, unsigned octets, booleans,
  binary32/binary64 floating-point values, and byte arrays;
- Unicode text, characters, regex strings, email addresses, domains, and URLs;
- UUIDs, IPv4 and IPv6 addresses, dates, times, and datetimes;
- constants, sampled values, mapping, dependent generation, filtering,
  alternatives, and optional values;
- permutations, order-preserving subsequences, and samples of finite inputs;
- tuples, vectors, chunkings, lists, sets, sorted sets, maps, sorted maps, and
  fixed-key heterogeneous maps; and
- recursive trees and documents with engine-owned depth, leaf-budget, retry,
  and subtree-hoisting shrink semantics.

Generators are ordinary values drawn with `h/draw!`. Prefer a generator that
constructs valid data directly. Use `h/assume!` only for uncommon exclusions;
rejecting most cases wastes the run and weakens shrinking.

Public option maps are closed: unsupported keys and invalid documented option
domains fail as usage errors when the generator or test configuration is
constructed. In particular, documented boolean options require `true` or
`false`; they do not use generic Clojure truthiness. This keeps a configuration
mistake made inside a property out of shrinking and replay.

`g/big-integer` requires two inclusive integer bounds (or `{:min x :max y}`)
and returns a portable arbitrary-precision integer value; do not depend on its
host class. There is no unbounded default. `g/integer` remains int64-only.

`g/float32` uses native width-32 generation and returns exactly widened host
doubles, including subnormals. It accepts the same option keys as `g/double`;
finite bounds must be exactly binary32-representable, without rounding the
original integer, ratio, or decimal input. For example, `0.5` is valid but
`0.1` is not. NaN is allowed only without bounds; infinities require the
corresponding unbounded endpoint and `:infinity? true`. With no bounds both
are enabled by default. Explicit infinite endpoints are accepted.
`g/double` retains its existing width-64 behavior.

The new numeric domains are tested on BB/JVM/Jolt. Their full arbitrary-width
and binary32 contracts are not yet qualified on experimental jank/ClojureCLR;
see the host-specific documentation before selecting those hosts.

`g/permutations`, `g/subsequences`, and `g/samples` realize their finite input
once and return vectors. They retain duplicate values by source position:
permutations retain every position, subsequences preserve input order, and
`{:replacement? false}` samples select each position at most once. Samples use
replacement by default, so their default native maximum size remains unbounded
for nonempty inputs. Full permutations intentionally remove one position at a
time from the remaining vector (`O(n²)`) to keep positional duplicate handling
and native choice shrinking intact.

Recursive generators keep the recursion policy in libhegel, so generated
trees use the same depth and leaf budgets on every host and can shrink by
replacing a tree with one of its own subtrees:

```clojure
(def tree
  (g/recursive
   {:max-depth 8 :max-leaves 64}
   (g/fmap (fn [value] {:leaf value}) (g/integer))
   (fn [subtree]
     (g/fmap (fn [children] {:children children})
             (g/vector {:max-size 4} subtree)))))
```

The branch function receives a reusable subtree generator and returns a
generator for one branch level. `:max-depth` defaults to 32 and `:max-leaves`
to 100. A depth of zero forces leaves. libhegel owns branch decisions, retry
policy, size steering, and subtree-hoisting shrink structure; application code
does not write its own recursive depth draw.

Generator-owned spans and native handles preserve exception precedence: if a
generator body fails while its cleanup also fails, the exact body throwable is
rethrown. If only cleanup fails, that cleanup failure remains visible. Native
recursive retry controls already discard their open spans, so host unwinding
does not close them; no secondary throwable is mutated or attached as evidence.

The exact constructors and options are documented in the bundled
[API reference](skills/jolt-hegel/references/api.md).

## Optional Malli adapter

Malli is not a runtime dependency. If the consuming project already uses
Malli, `hegel.malli/generator` can derive a native Hegel generator for the
adapter's bounded schema subset, including a single self-recursive registry:

```clojure
(require '[hegel.malli :as hm])

(def request-generator
  (hm/generator
   [:map {:closed true}
    [:id [:int {:min 1 :max 1000000}]]
    [:query [:string {:min 1 :max 120}]]
    [:limit {:optional true} [:int {:min 1 :max 100}]]]))
```

A recursive definition must be one registry entry whose root is `:or`, with
at least one nonrecursive base branch and one self-referencing branch:

```clojure
(def tree-generator
  (hm/generator
   [:schema
    {:registry
     {::tree
      [:or
       [:= :leaf]
       [:tuple [:= :node] [:ref ::tree] [:ref ::tree]]]}}
    [:ref ::tree]]
   {:max-depth 8 :max-leaves 64}))
```

This delegates depth, leaf-budget, retry, and subtree-hoisting shrink behavior
to `g/recursive`. Mutual recursion, multiple registry entries, and references
outside the active recursive definition remain unsupported.

Add Malli to the test alias yourself. Unsupported schemas fail when the
generator is built rather than silently falling back to a different generator.
The supported schema contract is listed in the
[API reference](skills/jolt-hegel/references/api.md#optional-malli-adapter).

## Installation

For the native mode removal, temporal precision adapter, and replay-version
boundary, see [libhegel 0.36 migration](docs/LIBHEGEL-036-MIGRATION.md).

jolt-hegel uses libhegel 0.36.3. Prebuilt upstream libraries are available for
Linux x86_64/arm64, Windows x86_64/arm64, and macOS arm64; the supported CI
matrix remains Linux x86_64, Windows x86_64, and macOS arm64. The installer chooses the asset,
verifies its pinned SHA-256, and caches it. The same SHA-pinned Git dependency
can be used by each host:

```clojure
{:aliases
 {:test
  {:extra-deps
   {io.github.chucklehead-dev/jolt-hegel
    {:git/url "https://github.com/chucklehead-dev/jolt-hegel.git"
     :git/sha "<jolt-hegel-commit-sha>"}
    ;; JVM Clojure only; omit for Jolt and Babashka.
    io.github.babashka/ffi
    {:git/url "https://github.com/babashka/ffi.git"
     :git/sha "aacb153618bc39ca1e4c397b8f30fb81c76d0c4c"}
    ;; Only needed when requiring hegel.malli:
    metosin/malli {:mvn/version "0.20.1"}}}}}
```

For Babashka's direct `bb -m hegel.install` UX, put the same pin in the
top-level `:deps` of `bb.edn` (or otherwise place it on Babashka's classpath):

```clojure
{:deps
 {io.github.chucklehead-dev/jolt-hegel
  {:git/url "https://github.com/chucklehead-dev/jolt-hegel.git"
   :git/sha "<jolt-hegel-commit-sha>"}}}
```

Replace the placeholder with the full peeled commit behind the intended
release tag. Do not leave the placeholder, a tag name, or a moving branch in
consumer configuration.

Run the installer with the host that will run the tests and the alias that
contains jolt-hegel:

```bash
# Current `main` / next release candidate: Jolt 0.8.1+
jolt -A:test -m hegel.install

# Babashka 1.13.220+
bb -m hegel.install

# JVM Clojure on JDK 22+ (JDK 25 is the primary target)
clojure -J--enable-native-access=ALL-UNNAMED -M:test -m hegel.install
```

Git dependencies do not contribute their aliases to a consuming project. If
jolt-hegel is in top-level `:deps`, no dependency alias is needed; otherwise
make sure the consuming project's alias is active so the installer namespace is
on the classpath.

Babashka 1.13.220 is the minimum supported release, but the runtime build must
include libffi and dynamic-library loading. On Linux, install the ordinary
`babashka-<version>-linux-<arch>.tar.gz` asset, not the `-static` asset. Confirm
that `bb describe` reports a non-nil `:libffi/version`; jolt-hegel performs the
same capability check before looking for libhegel and reports an unsupported
runtime build separately from a missing native library.

The 1.13.220 release embeds
`babashka.ffi` commit `aacb153618bc39ca1e4c397b8f30fb81c76d0c4c`.
JVM Clojure pins the same source commit through this repository's `:jvm`
development alias and requires JDK 22 or later with
`--enable-native-access=ALL-UNNAMED`. Because dependency aliases do not
propagate, JVM consumers must add that `io.github.babashka/ffi` Git dependency
beside jolt-hegel; Babashka consumers must not add it because the namespace is
built into the runtime.

Environment overrides:

| Variable | Purpose |
| --- | --- |
| `HEGEL_CACHE_DIR` | writable native-library cache |
| `HEGEL_LIBHEGEL_LIBRARY` | explicit path to a compatible libhegel library |
| `HEGEL_NATIVE_ARCH` | target override: `amd64` or `arm64` |
| `HEGEL_LIBHEGEL_RELEASE_BASE` | mirror for the pinned upstream release |
| `JOLT_CACHE_DIR` | writable Jolt AOT cache, preferably keyed by dependency SHA |

Checksum verification is never skipped. A user-supplied library path controls
where libhegel is loaded from; its ABI version is still checked at run startup.

Concurrent installs use unique staging files beside the destination. The
installer verifies its own download before publication and never deletes an
existing cache entry to make room. Publication is an atomic same-filesystem
rename, not a copy/delete fallback. When a host refuses to replace an existing
destination, a checksum-matching concurrent winner is accepted; otherwise the
install fails and preserves the destination. In particular, Jolt on Windows
does not replace an existing mismatching entry automatically. Resolve such a
cache conflict explicitly, or select another cache directory. This contract
covers cooperating installers for the pinned release, not unrelated processes
that modify cache contents, and does not promise crash durability via fsync.
Each invocation cleans up only its own staging files. Explicit library-path
overrides do not download or publish cache files.

## Backend diagnostics

The native ABI is data, so it can be inspected without loading libhegel:

```clojure
(require '[hegel.abi :as abi])

(abi/functions) ; canonical function map from resources/hegel/abi.edn
(abi/validate!) ; validates types, references, structs, and signatures
```

After the selected backend initializes, `(abi/backend-report)` returns
structured per-function coverage and routing. Routes identify Jolt direct FFI,
Babashka's compiled trampoline or libffi fallback, upstream-library JVM FFM, generated jank
interop, or generated CLR P/Invoke. Ordinary property code does not need to
select a backend.

### Standalone Jolt resources

Standalone Jolt packaging must embed the resource directory explicitly. Resource
names remain classpath-relative and are rooted at `hegel/abi.edn`:

```clojure
{:jolt/build {:embed ["path/to/hegel/resources"]}}
```

Stage that directory from the checked-out dependency or release contents; do
not hardcode a Git cache path. Embedding the descriptor does not package
`libhegel`: deploy the native library separately and verify its ABI/version and
checksum through the normal installer or explicit-library-path contract.

Embedded resources take precedence. For filesystem resources on Windows, the
host seam retains direct source-root paths to avoid Jolt 0.8.1's file-URL
drive-path rebasing bug; read errors are not swallowed to try a different copy.

The Linux Jolt CI gate runs `bash script/check-standalone-resources.sh`: it builds
from a disposable copy, moves those inputs out of their compiled paths, then
runs a native property from a separate consumer directory. Source-root listings
alone do not package resources. The standalone gate is Linux-specific; ordinary
resource lookup is also tested in the supported-host harness matrix.

## Trace and history contracts

The trace and history APIs below are portable library code and run on supported
Jolt, Babashka, and JVM Clojure releases. Neither API requires a forked Jolt
compiler or runtime. Only the optional producer which captures events from
Jolt compiler aspects currently requires an aspect-enabled Jolt fork;
protocol harnesses, explicit journals, application logs, and other producers
can supply the same event vectors on an unmodified runtime.

An explicit canonical operation-event profile is available through
`hegel.event-contract/check!` and `check-envelope!`; see
[the profile contract](docs/EVENT_CONTRACT.md). It does not silently change
generic trace/history acceptance or replace domain-model checks.

The trace, bounded-history and explicit canonical-profile contracts are
supported on Jolt, Babashka and JVM Clojure. Their intentional acceptance
differences and versioning rules are recorded in
[ADR0007](docs/adr/0007-versioned-operation-events.md), with shared differential
tests and real aspect-packs producer/consumer evidence. Profile validity does
not establish domain-model correctness or linearizability.

The optional compiler-aspect manifest, join-point identifiers, and advice-role
metadata follow the [join-point compatibility contract](docs/JOINPOINTS.md).
Pin the Hegel source and independently match the manifest revision in the
provider. The compiler execution gate is Linux-specific and requires the
explicitly selected aspect-capable fork; ordinary properties need neither.

### Trace rules for aspect and event journals

`hegel.trace` checks a complete, bounded semantic event trace after a generated
action or state-machine checkpoint. The event producer is not coupled to
Hegel. When a rule fails, its stable origin and bounded events become part of
Hegel's failure, so the input or command sequence which produced the trace is
shrunk normally.

```clojure
(require '[hegel.trace :as ht])

(ht/check!
 (journal/snapshot observations)
 [(ht/ordered-sequence :partition-cursors-increase
                       {:value :cursor :scope :partition
                        :order :strictly-increasing})
  (ht/contiguous-sequence :journal-not-truncated)
  (ht/closed-lifecycles :aspect-lifecycles-close)
  (ht/causal-parentage :aspect-parent-invoked-first)
  (ht/causal-links :aspect-causal-fan-in)
  (ht/context-coherence :aspect-context-coherent)
  (ht/every-eventually
   :model-call-terminates
   #(and (= :agent/model (:role %)) (= :enter (:phase %)))
   #(contains? #{:return :throw} (:phase %)))])
```

Run these checks outside instrumentation advice. Jolt aspects deliberately fail
open when advice throws, so an assertion inside advice can be swallowed while
the application correctly proceeds. Also reject wrapped ring-journal snapshots:
`contiguous-sequence` detects a missing prefix, while `:max-events` on `check!`
keeps failure evidence bounded.

Sequence order is explicit: `:nondecreasing` permits duplicates and gaps,
`:strictly-increasing` permits gaps but not duplicates, and `:contiguous`
requires increments of exactly one. `:scope` checks independent cursors or
partitions without imposing a global order on their interleaving.

For canonical async histories emitted by jolt-aspect-packs, compose
`contiguous-sequence`, `closed-lifecycles`, `causal-parentage`, `causal-links`,
and `context-coherence`. Causal parentage requires the parent's invocation to
precede the child's invocation, but permits the parent to terminate before the
child. `causal-links` validates additional fan-in dependencies as a sorted,
unique vector of operation ids which each name exactly one earlier invocation;
use an empty vector when there is no fan-in. Canonical linked operation ids are
portable scalars—integers, strings, keywords, or symbols—not composite EDN or
opaque host values. Every invocation id in a history checked by this rule uses
that same portable domain, including invocations with no links. Context
coherence compares invocation carrier metadata; terminal events do not repeat
`:context-id` or `:causal-links`. Use `synchronous-parentage` only when child
lifecycles must be wholly nested in the parent's dynamic extent. For filtered
views whose omitted events legitimately create sequence gaps, use
`ordered-sequence` with `:order :strictly-increasing` instead of continuity.

For richer protocols, `event-model` folds each event through a tiny pure state
machine and checks an invariant after every transition plus a final predicate.
Its optional `:scope` makes the same model independently check file
descriptors, buffer loans, requests, spans, DB handles, or queue partitions.

### Bounded linearizability

`hegel.history` checks complete concurrent operation histories against a pure
sequential model. Unlike `hegel.trace/event-model`, it preserves real-time
precedence but explores alternate orders for overlapping operations. This is
useful for atoms, promises, channels, connection lifecycles, pollers, and other
APIs whose legal observation order is not necessarily invocation order.

```clojure
(require '[hegel.history :as history])

(defn register-step [state operation]
  (case (:operation operation)
    :write (when (= :ok (:value operation))
             {:state (:input operation)})
    :read  (when (= state (:value operation))
             {:state state})
    nil))

(history/check!
 0
 register-step
 [{:seq 0 :operation-id :write :phase :invoke
   :operation :write :input 1}
  {:seq 1 :operation-id :read :phase :invoke :operation :read}
  {:seq 2 :operation-id :write :phase :return :value :ok}
  {:seq 3 :operation-id :read :phase :return :value 0}])
;; => {:order [:read :write], :final-state 1, ...}
```

Each operation has exactly one `:invoke` and one `:return` or `:throw` terminal.
Sequence numbers must be integer, unique, contiguous, and in vector order.
`check!` returns a witness or throws with a stable `:hegel/origin` and bounded
history evidence. `linearization` returns the witness or nil, while
`linearizable?` returns a boolean. The sequential step returns nil for an
illegal observation or `{:state next-state}` for a legal one. For callers that
need to distinguish a negative result from a bounded search, `analyze` returns
one of `:linearizable`, `:not-linearizable`, or `:inconclusive`.

The checker defaults to at most ten total operations because its exhaustive
search is exponential. `:max-search-steps` defaults to 100000 and is a global
cap across partitions: each candidate operation considered consumes one step,
including a candidate blocked by real-time precedence. It does not bound
history preprocessing or wall time spent in a model callback. Empty valid
histories are decisive without consuming a step. If the cap is exhausted,
legacy `linearization`, `linearizable?`, and `check!` throw an exception marked
`:hegel/inconclusive? true`; they never convert exhaustion into a false verdict
that can be shrunk. `:partition-by` can split independent keys into their own
model instances (so cross-key precedence is intentionally outside each local
model), and `:sequence-start` can require a particular first sequence number.
The indexed search uses persistent sets and vectors rather than fixed-width
masks. It deliberately does not memoize model states: safe memoization requires
an equality-congruence promise for arbitrary user state and step functions that
this API does not make. `hegel.history/rule` creates a rule accepted by
`hegel.trace/check!`, so linearizability can be checked beside lifecycle and
parentage rules. Incomplete histories are rejected; callers must snapshot only
after all generated operations have terminated.

## Development and design

The repository gates the same semantic suite under all supported hosts. Start
with the host-specific installer, then run its test alias.

The default test command runs the reviewed ordered aggregate. For focused
maintenance, list stable suite names with `bb test --list-suites`, then select
one with `bb test --suite generators`; the equivalent host commands are
`clojure -M:jvm:test --suite generators` and `jolt -M:test --suite generators`.
Unknown or malformed runner arguments fail without falling back to the full
aggregate.

Maintainer details live in:

- [architecture and ownership](docs/ARCHITECTURE.md);
- [ABI descriptor and backend development](docs/ABI.md);
- [FFI backend evaluation](docs/FFI_BACKEND_EVALUATION.md);
- [behavioral contracts](docs/DESIGN.md);
- [experimental jank host status](docs/JANK.md);
- [experimental ClojureCLR host status](docs/CLR.md);
- [architecture decisions](docs/adr/README.md); and
- [release process](docs/RELEASING.md).

The repository also ships an [Agent Skill](skills/jolt-hegel/SKILL.md) for
adding evidence-backed property and stateful tests to another project.

## License

jolt-hegel is licensed under the [Eclipse Public License 2.0](LICENSE).
libhegel remains under its upstream MIT license; see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
