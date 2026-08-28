# jolt-hegel

Property-based and stateful testing for Jolt, Babashka, and JVM Clojure, backed
by the [Hegel](https://hegel.dev/) generation and shrinking engine.

Examples are good at confirming cases you already thought of. A Hegel property
describes a larger truth: values are generated across the input domain, a
failure is reduced to a small counterexample, and the final case is replayed
before the result is reported. The seed in every result makes the run
repeatable.

jolt-hegel exposes one Clojure API on all three hosts. It calls the same
libhegel 0.33 C ABI directly through `jolt.ffi`, `babashka.ffi`, or the final
JDK Foreign Function & Memory API. There is no service to start and no
subprocess protocol.

Experimental ports reuse that same implementation and boundary on jank and
ClojureCLR. They have focused cross-platform CI but are not yet part of the
supported release contract.

## Current status

| Host | Contract | Continuously tested targets |
| --- | --- | --- |
| Jolt 0.7.23+ | Supported | Linux x86_64, Windows x86_64, macOS arm64 |
| Babashka fork `26367edf` | Supported while the required FFI work is unreleased upstream | Linux x86_64, Windows x86_64, macOS arm64 native images |
| JVM Clojure, JDK 22+ | Supported; JDK 25 is primary | Linux x86_64, Windows x86_64, macOS arm64, plus a Linux JDK 22 minimum gate |
| jank | Experimental focused suite | Linux x86_64 and macOS arm64 |
| ClojureCLR 1.12.2 on .NET 8 | Experimental focused suite | Linux x86_64, Windows x86_64, macOS arm64 |

The current release is `v0.3.0`, the first release of the portable
implementation. Consumer Git dependencies should pin the tag's full peeled
commit SHA, not the tag-object SHA or a moving branch reference.

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

Every result includes its selected seed as a decimal string. A direct runner
can replay it exactly:

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
                :seed (parse-long (:seed result))
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

## Trace rules for aspect and event journals

`hegel.trace` checks a complete, bounded semantic event trace after a generated
action or state-machine checkpoint. The event producer is not coupled to
Hegel: a compiler-aspect journal, protocol harness, or ordinary application log
can supply the vector. When a rule fails, its stable origin and bounded events
become part of Hegel's failure, so the input or command sequence which produced
the trace is shrunk normally.

```clojure
(require '[hegel.trace :as ht])

(ht/check!
 (journal/snapshot observations)
 [(ht/ordered-sequence :partition-cursors-increase
                       {:value :cursor :scope :partition
                        :order :strictly-increasing})
  (ht/contiguous-sequence :journal-not-truncated)
  (ht/closed-lifecycles :aspect-lifecycles-close)
  (ht/synchronous-parentage :aspect-parentage)
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

For richer protocols, `event-model` folds each event through a tiny pure state
machine and checks an invariant after every transition plus a final predicate.
Its optional `:scope` makes the same model independently check file
descriptors, buffer loans, requests, spans, DB handles, or queue partitions.
This covers linear resource lifecycles without coupling the journal producer to
Hegel.

## Generators

The built-in surface covers:

- signed integers, unsigned octets, booleans, doubles, and byte arrays;
- Unicode text, characters, regex strings, email addresses, domains, and URLs;
- UUIDs, IPv4 and IPv6 addresses, dates, times, and datetimes;
- constants, sampled values, mapping, dependent generation, filtering,
  alternatives, and optional values; and
- tuples, vectors, chunkings, lists, sets, sorted sets, maps, sorted maps, and
  fixed-key heterogeneous maps.

Generators are ordinary values drawn with `h/draw!`. Prefer a generator that
constructs valid data directly. Use `h/assume!` only for uncommon exclusions;
rejecting most cases wastes the run and weakens shrinking.

The exact constructors and options are documented in the bundled
[API reference](skills/jolt-hegel/references/api.md).

## Optional Malli adapter

Malli is not a runtime dependency. If the consuming project already uses
Malli, `hegel.malli/generator` can derive a native Hegel generator for the
adapter's bounded, nonrecursive schema subset:

```clojure
(require '[hegel.malli :as hm])

(def request-generator
  (hm/generator
   [:map {:closed true}
    [:id [:int {:min 1 :max 1000000}]]
    [:query [:string {:min 1 :max 120}]]
    [:limit {:optional true} [:int {:min 1 :max 100}]]]))
```

Add Malli to the test alias yourself. Unsupported schemas fail when the
generator is built rather than silently falling back to a different generator.
The supported schema contract is listed in the
[API reference](skills/jolt-hegel/references/api.md#optional-malli-adapter).

## Installation

jolt-hegel uses libhegel 0.33.0. Prebuilt upstream libraries are available for
Linux x86_64, Windows x86_64, and macOS arm64. The installer chooses the asset,
verifies its pinned SHA-256, and caches it. The same SHA-pinned Git dependency
can be used by each host:

```clojure
{:aliases
 {:test
  {:extra-deps
   {io.github.chucklehead-dev/jolt-hegel
    {:git/url "https://github.com/chucklehead-dev/jolt-hegel.git"
     :git/sha "<jolt-hegel-commit-sha>"}
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
# Jolt 0.7.23+
jolt -A:test -m hegel.install

# Babashka built from casselc/babashka commit 26367edf...
bb -m hegel.install

# JVM Clojure on JDK 22+ (JDK 25 is the primary target)
clojure -J--enable-native-access=ALL-UNNAMED -M:test -m hegel.install
```

Git dependencies do not contribute their aliases to a consuming project. If
jolt-hegel is in top-level `:deps`, no dependency alias is needed; otherwise
make sure the consuming project's alias is active so the installer namespace is
on the classpath.

While the required Babashka FFI work is unreleased, use a binary built from
`casselc/babashka` commit
`26367edf91905eb85c68e6fd77f3e108a60dc651`, the exact revision built and
tested by this repository's CI. JVM Clojure uses `java.lang.foreign` directly
and therefore requires JDK 22 or later.

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

## Backend diagnostics

The native ABI is data, so it can be inspected without loading libhegel:

```clojure
(require '[hegel.abi :as abi])

(abi/functions) ; canonical function map from resources/hegel/abi.edn
(abi/validate!) ; validates types, references, structs, and signatures
```

After the selected backend initializes, `(abi/backend-report)` returns
structured per-function coverage and routing. Routes identify Jolt direct FFI,
Babashka's compiled trampoline or libffi fallback, JVM FFM, generated jank
interop, or generated CLR P/Invoke. Ordinary property code does not need to
select a backend.

## Development and design

The repository gates the same semantic suite under all supported hosts. Start
with the host-specific installer, then run its test alias. Maintainer details
live in:

- [architecture and ownership](docs/ARCHITECTURE.md);
- [ABI descriptor and backend development](docs/ABI.md);
- [behavioral contracts](docs/DESIGN.md);
- [experimental jank host status](docs/JANK.md);
- [experimental ClojureCLR host status](docs/CLR.md);
- [architecture decisions](docs/adr/README.md); and
- [release process](docs/RELEASING.md).

The repository also ships an [Agent Skill](skills/jolt-hegel/SKILL.md) for
adding evidence-backed property and stateful tests to another project.

## Acknowledgments

The original jolt-hegel design and implementation were based in part on Kyle
Kingsbury (Aphyr)'s [hegel-clj](https://github.com/aphyr/hegel-clj), including its
imperative generator style, `run-test!` and `clojure.test` integration, and
final-replay diagnostics. The project also builds on upstream Hegel and its
libhegel engine. jolt-hegel remains an independent Clojure-family library with
one public behavior across its supported hosts.

## License

jolt-hegel is licensed under the [Eclipse Public License 2.0](LICENSE).
libhegel remains under its upstream MIT license; see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
