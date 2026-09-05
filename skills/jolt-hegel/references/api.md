# jolt-hegel API

## Contents

- [Install](#install)
- [Choose an integration](#choose-an-integration)
- [`clojure.test` property template](#clojuretest-property-template)
- [Custom-runner property template](#custom-runner-property-template)
- [Generators](#generators)
  - [Optional Malli adapter](#optional-malli-adapter)
- [Combinators and collections](#combinators-and-collections)
- [Core controls](#core-controls)
- [Observations and coverage](#observations-and-coverage)
- [Stateful testing](#stateful-testing)
- [Semantic trace rules](#semantic-trace-rules)
- [Canonical event profile](#canonical-event-profile)
- [Bounded linearizability](#bounded-linearizability)
- [Run options](#run-options)
- [Replay bundles](#replay-bundles)
- [Materialized corpora](#materialized-corpora)
- [Concurrency](#concurrency)
- [Verification commands](#verification-commands)

## Install

Use the runtime selected by the consuming project's toolchain contract: Jolt
0.8.1 or later on current `main` and next-release work (the published `v0.5.0`
tag retains its historical Jolt 0.7.23+ contract), an FFI-capable Babashka
1.13.220 or later build with the official `babashka.ffi`, or JVM Clojure on
JDK 22 or later. On Linux, use the
ordinary release asset rather than the `-static` asset and confirm `bb describe`
reports a non-nil `:libffi/version`. Do not substitute a
convenient global executable for a project-selected binary. Add the public
release by full commit SHA. `v0.5.0` is the current portable release; resolve its
annotated tag to the full peeled commit rather than using the tag-object SHA,
tag name, or a moving branch. A test-only dependency normally belongs in the
project's test alias:

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
     :git/sha "aacb153618bc39ca1e4c397b8f30fb81c76d0c4c"}}}}}
```

From the consuming project, install the verified libhegel release with the host
that will run the tests:

```bash
# Current `main` / next release candidate: Jolt 0.8.1+
JOLT_CACHE_DIR=.jolt-cache/jolt-hegel-<jolt-hegel-commit-sha> \
  jolt -A:test -m hegel.install

# Babashka
bb -m hegel.install

# JVM Clojure
clojure -J--enable-native-access=ALL-UNNAMED -M:test -m hegel.install
```

The direct Babashka command assumes the SHA-pinned dependency is in top-level
`:deps` in `bb.edn` or is otherwise on the classpath. Activate a consuming
project alias when it is alias-scoped.

Git dependencies do not contribute aliases to a consuming project. Activate
the alias that contains jolt-hegel where the host requires it. If the dependency
is in top-level `:deps`, no dependency alias is needed.

Babashka 1.13.220 embeds `babashka.ffi`; do not add the standalone FFI Git
dependency to `bb.edn`. JVM consumers add the exact FFI pin shown above because
jolt-hegel's development-only `:jvm` alias does not propagate transitively. The
Linux `-static` Babashka asset cannot load libhegel or provide the libffi-backed
aggregate calls its ABI requires. jolt-hegel detects that unsupported runtime
build before checking whether the configured libhegel file exists.

Replace `<jolt-hegel-commit-sha>` with the full peeled commit behind the
intended release tag; never copy the placeholder into a project. Without a
local checkout, list and resolve the remote tags with:

```bash
git ls-remote --tags --sort=-v:refname \
  https://github.com/chucklehead-dev/jolt-hegel.git 'v*'
```

The highest version tag is listed first; confirm that it is the stable release
you intend to pin. For an annotated tag, use the full SHA on its `^{}` line (the
peeled commit), not the tag-object SHA on the plain tag line. A lightweight tag
has no peeled line, so its plain line already names the commit.

Use `HEGEL_CACHE_DIR` when the dependency checkout is not writable and
`HEGEL_LIBHEGEL_LIBRARY` for an explicit compatible library. If Jolt cannot
write its default AOT cache, set `JOLT_CACHE_DIR` to a writable directory keyed
by the pinned release SHA. The installer retains a Jolt-only source/AOT identity
check and verifies SHA-256 on every downloaded asset. All hosts verify the
loaded libhegel ABI version before a run.

For standalone Jolt consumers, stage the dependency's `resources` directory
and explicitly include that directory in `:jolt/build {:embed [...]}`. Embed
the classpath root so the canonical descriptor retains the resource name
`hegel/abi.edn`; listing a source/resource path alone does not package it.
Use normal resource resolution, not manual filesystem lookup, so embedded data
remains available after deployment. The native library is still a separately
deployed, version-checked asset. Test a relocated binary with its build inputs
unavailable; a source-only test can conceal missing packaging.

### Host status

| Host | Status and current evidence |
| --- | --- |
| Jolt 0.8.1+ on current `main` | Supported on Linux x86_64, Windows x86_64, and macOS arm64 |
| FFI-capable Babashka 1.13.220+ | Supported on the same three-OS native-image matrix; Linux `-static` assets are excluded |
| JVM Clojure on JDK 22+ | Supported on the same matrix with JDK 25 primary and a Linux JDK 22 minimum gate |
| jank | Experimental focused Linux and macOS suites; see `docs/JANK.md` |
| ClojureCLR 1.12.2 on .NET 8 | Experimental focused three-OS suite; see `docs/CLR.md` |

The experimental hosts reuse shared property behavior, but their focused CI is
not equivalent to the complete supported-host semantic and consumer matrix.

## Choose an integration

| Suite | Entry point | Caller responsibility |
| --- | --- | --- |
| `clojure.test` | `hegel.clojure-test/with` inside `deftest` | Use ordinary `clojure.test` assertions and runner behavior |
| Any other runner or a program | `hegel.core/run-test!` | Throw inside the property and treat returned `:passed? false` as failure |

Use `hegel.report/counting-runner` with `hegel.report/run!` when a custom suite
must continue after failed results or thrown run errors. The reporting helper
counts outcomes but deliberately does not call `System/exit`.

## clojure.test property template

```clojure
(ns example.property-test
  (:require [clojure.test :refer [deftest is]]
            [hegel.clojure-test :refer [with]]
            [hegel.core :as h]
            [hegel.generator :as g]))

(deftest vector-reverse-roundtrip
  (with {:test-cases 200 :database "" :verbosity :quiet}
    [xs (g/vector {:max-size 100} (g/integer))]
    (h/fprn :minimal-xs xs)
    (is (= xs (-> xs reverse reverse vec)))))
```

`with` reports one pass for a successful property. For a failure it suppresses
exploration reports, shrinks the input, then sends only final replay assertions
to `clojure.test`. It returns the underlying `run-test!` result map.

## Custom-runner property template

```clojure
(ns example.property-test
  (:require [hegel.core :as h]
            [hegel.generator :as g]
            [hegel.report :as report]))

(defn check-integer-roundtrip! [runner]
  (report/run!
   runner
   "integer text round-trip"
   (fn []
     (h/run-test!
      {:test-cases 200
       :database ""
       :verbosity :quiet}
      (fn [_]
        (let [n (h/draw! (g/integer))]
          (when-not (= n (parse-long (str n)))
            (throw
             (ex-info "integer text round-trip failed"
                      {:hegel/origin "integer-roundtrip/text"})))))))))

(defn -main [& _]
  (let [runner (report/counting-runner)]
    (check-integer-roundtrip! runner)
    (println "Ran properties;" (report/failure-count runner) "failures")
    (flush)
    (System/exit (if (report/passed? runner) 0 1))))
```

The property body throws when an invariant fails. `run-test!` catches that
failure, shrinks it, replays the minimal counterexample, and returns normally
with `:passed? false`. libhegel-detected outcome flakiness and non-deterministic
generation also return a countable result with `:status :error`, `:flaky? true`,
and an `:error` message. Setup errors, health-check failures, and unexpected
engine errors still throw. `hegel.report/run!` counts both failed results and
thrown run errors, then continues the suite. `counting-runner` accepts a
`:reporter` function for structured `:pass`, `:fail`, and `:error` events; its
default reporter prints to stdout. It never calls `System/exit`.

## Generators

All generators return `(fn [test-case] value)` and are consumed with
`h/draw!`.

| Form | Output and options |
| --- | --- |
| `(g/integer)` | full signed 64-bit range |
| `(g/integer min max)` | inclusive integer bounds |
| `(g/integer {:min x :max y})` | either integer bound may be omitted |
| `(g/octet)` | unsigned wire octet as an integer from 0 through 255 |
| `(g/boolean)` | unbiased boolean |
| `(g/boolean p)` | boolean with probability `p` of true |
| `(g/double)` | double; NaN and infinities allowed |
| `(g/double min max)` | inclusive finite bounds |
| `(g/double opts)` | `:min`, `:max`, `:exclude-min?`, `:exclude-max?`, `:nan?`, `:infinity?` |
| `(g/bytes)` | byte array with length 0 through 100 |
| `(g/bytes min-size max-size)` | byte array with inclusive size bounds |
| `(g/bytes opts)` | `:min-size` and `:max-size` |
| `(g/uuid)` | canonical lowercase UUID |
| `(g/uuid version)` | RFC 4122 version 1 through 5 |
| `(g/ipv4)` | dotted-quad string |
| `(g/ipv6)` | canonical lowercase colon-hex string |
| `(g/date opts)` | ISO 8601 date string |
| `(g/time opts)` | ISO 8601 local-time string |
| `(g/datetime opts)` | ISO 8601 naive-datetime string |
| `(g/string)` | Unicode string with 0 through 100 code points |
| `(g/string opts)` | size, codec, code-point, category, character, or alphabet constraints |
| `(g/character opts)` | one-code-point string with the string character filters |
| `(g/regex-str pattern)` | full-match regex string |
| `(g/regex-str pattern {:full-match? false})` | string containing a regex match |
| `(g/email)` | RFC 5321/5322 email-address string |
| `(g/url-str)` | RFC 3986 HTTP/HTTPS URL string |
| `(g/domain opts)` | RFC 1035 domain, optionally bounded by `:max-length` |

Use `(g/integer -128 127)` when the property is defined over signed byte values.
For wire formats, prefer `g/octet` and call `(unchecked-byte octet)` only at an
API boundary that requires a host signed-byte representation. In the other
direction, `(bit-and signed-byte 0xff)` recovers the unsigned octet. Use
`g/bytes` when the property needs an array.

`g/integer` validates both bounds at construction. Each must satisfy Clojure's
`integer?` predicate (so `1.0` is rejected), be in the signed 64-bit range, and
have a minimum no greater than the maximum. Fractional, nonnumeric, nil, and
out-of-range bounds are usage errors before a native draw.

### Optional Malli adapter

Consumers that supply Malli may require `hegel.malli` and build a generator
with `(hegel.malli/generator schema)`. Malli is deliberately absent from
jolt-hegel's runtime dependencies; add `metosin/malli` explicitly to the same
consumer alias. The adapter test suite uses Malli 0.20.1.

```clojure
{:extra-deps
 {io.github.chucklehead-dev/jolt-hegel
  {:git/url "https://github.com/chucklehead-dev/jolt-hegel.git"
   :git/sha "<jolt-hegel-commit-sha>"}
  metosin/malli {:mvn/version "0.20.1"}}}
```

```clojure
(require '[hegel.malli :as hm])

(hm/generator
 [:map {:closed true}
  [:id [:int {:min 1 :max 1000}]]
  [:label {:optional true} [:maybe [:string {:max 40}]]]])
```

Construct the generator once when practical, then draw from it only inside an
active `run-test!` or `hegel.clojure-test/with` property.

The bounded subset supports `:nil`, `:boolean`, `:int`, `:double`, `:string`,
`:=`, `:enum`, `:maybe`, `:or`, `:tuple`, `:vector`, `:sequential`, `:set`,
`:map-of`, and closed maps with explicit keys. Scalar and collection
`:min`/`:max` properties become native Hegel bounds. An absent string or
collection `:max` uses 100 as its fallback, raised to an explicit larger `:min`
when necessary; pass `{:default-max-size n}` as the second argument to change
that fallback.

One directly self-recursive registry is also supported. Its root definition
must be `:or` with at least one branch containing no self-reference and at
least one branch containing `[:ref id]`:

```clojure
(hm/generator
 [:schema
  {:registry
   {::tree
    [:or
     [:= :leaf]
     [:tuple [:= :node] [:ref ::tree] [:ref ::tree]]]}}
  [:ref ::tree]]
 {:max-depth 8 :max-leaves 64})
```

The adapter maps that shape to `g/recursive`, preserving libhegel's native
depth, leaf-budget, retry, and subtree-hoisting shrink semantics. `:max-depth`
defaults to 32 and `:max-leaves` to 100; both are uint64 values and zero has the
same meaning as for `g/recursive`.

The adapter rejects unknown properties and config, other references, mutual
recursion, multiple registry entries, recursive roots other than `:or`, regex
schemas, intersections, predicates, functions, classes, transforms, custom
generator properties, open maps, and default map entries at construction time.
Exceptions carry stable `:type`, `:path`, and `:form` data. Every value is
checked by a precompiled Malli validator through `g/fmap`; an invalid value is
reported with `:hegel/origin "hegel.malli/generated-value"` and is an adapter
bug, not an input rejection.

Construction error types are `:hegel.malli/invalid-schema`,
`:hegel.malli/unsupported-schema`, `:hegel.malli/unsupported-property`,
`:hegel.malli/invalid-property`, and `:hegel.malli/invalid-config`. The final
guard uses `:hegel.malli/invalid-generated-value`.

Date bounds are inclusive maps:

```clojure
{:year 2024 :month 2 :day 29}
```

Time bounds are inclusive maps:

```clojure
{:hour 14 :minute 30 :second 15 :microsecond 123456}
```

Datetime bounds are `{:date date-map :time time-map}`. Pass temporal
bounds as `{:min minimum :max maximum}` or as two positional maps.

With libhegel 0.36, these public maps and six-digit fractional strings remain
microsecond-based. Raw `hegel.ffi` time maps use `:nanosecond` instead. The
generator converts inclusive microsecond bounds to full nanosecond buckets
and projects results with integer quotient by 1000; valid sub-microsecond
draws are not rejected. Seed streams and blobs are version-specific, not
guaranteed identical across a native upgrade.

String options are:

| Option | Meaning |
| --- | --- |
| `:min-size`, `:max-size` | inclusive code-point length bounds |
| `:codec` | `:ascii`, `:latin-1`, `:iso-8859-1`, or `:utf-8` |
| `:min-codepoint`, `:max-codepoint` | inclusive Unicode code-point bounds |
| `:categories` | Unicode general-category allowlist, e.g. `[:Lu :Nd]` |
| `:exclude-categories` | Unicode general-category denylist |
| `:include-characters` | String whose characters are added to the generated alphabet |
| `:exclude-characters` | String whose characters are removed from the generated alphabet |
| `:alphabet` | String containing the exact character alphabet; cannot be combined with other character filters |

Pass the alphabet itself as a string:

```clojure
(g/string {:min-size 1 :max-size 20 :alphabet "abc😀"})
```

`(vec "abc😀")`, a set, or another character collection is not accepted for
`:alphabet`; `:include-characters` and `:exclude-characters` likewise require
strings.

## Combinators and collections

| Form | Output |
| --- | --- |
| `(g/composite-fn f)` | tagged custom generator `(fn [test-case] value)` |
| `(g/just value)` | constant value |
| `(g/sampled-from values)` | one value from a non-empty collection |
| `(g/fmap f generator)` | transformed value |
| `(g/bind f generator)` | value from a dependent generator |
| `(g/filter pred generator)` | value satisfying pred; rejects after three failed draws |
| `(g/one-of generators)` | value from one selected generator |
| `(g/optional generator)` | `nil` or a generated value |
| `(g/tuple generators...)` | vector with one value per generator |
| `(g/vector opts elements)` | vector; supports `:unique?` |
| `(g/permutations values)` | vector containing every finite input position once |
| `(g/subsequences [opts] values)` | input-order vector selected without replacement |
| `(g/samples [opts] values)` | vector sampled with replacement by default |
| `(g/chunkings payload)` | vector chunks that concatenate to `payload` |
| `(g/recursive leaf branch-fn)` | recursively defined values with engine-owned sizing and shrinking |
| `(g/recursive opts leaf branch-fn)` | recursive values bounded by `:max-depth` and `:max-leaves` |
| `(g/list opts elements)` | Clojure list |
| `(g/set opts elements)` | set |
| `(g/sorted-set opts elements)` | sorted set |
| `(g/map opts keys values)` | map |
| `(g/sorted-map opts keys values)` | sorted map |
| `(g/hmap key-to-generator)` | fixed-key heterogeneous map |

Collection options are `:size`, `:min-size`, and `:max-size`. The forms without
an options map use engine-managed unbounded sizing. Duplicate set elements, map
keys, and unique-vector elements are rejected and redrawn.

`permutations`, `subsequences`, and `samples` realize a finite `values` input
once and always return vectors. They use native integer choices over source
positions, so equal values at distinct positions are retained rather than
deduplicated. `permutations` has exactly the input cardinality;
`subsequences` preserve source order; and `samples` accepts
`:replacement? true` or `false` in addition to the size options. For
subsequences and samples without replacement, the default maximum is the input
cardinality and an explicit maximum above it is a usage error. For nonempty
replacement samples, the default maximum remains engine-unbounded; an empty
input permits only size zero. Positional selection lets native shrinking move
toward earlier positions and smaller selected collections; full permutations
remove positions from a remaining vector and therefore intentionally cost
`O(n²)`.

For recursive data, `leaf` generates base values and `branch-fn` receives a
reusable generator for child values. The branch function must return a
generator for one compound level:

```clojure
(g/recursive
 {:max-depth 8 :max-leaves 64}
 (g/fmap (fn [n] {:leaf n}) (g/integer))
 (fn [subtree]
   (g/fmap (fn [children] {:children children})
           (g/vector {:max-size 4} subtree))))
```

`:max-depth` defaults to 32 and `:max-leaves` to 100. Both accept zero; depth
zero forces a leaf, while a zero leaf budget succeeds only for a branch shape
that can finish without a leaf. libhegel chooses leaf versus branch, retries
oversized or mispriced attempts, and labels every subtree so shrinking can
replace a tree with one of its descendants. Do not add a separate generated
depth counter around `g/recursive`.

`g/let` draws tagged generator right-hand sides and leaves ordinary expressions
alone. It must execute inside an active `run-test!` or
`hegel.clojure-test/with` property; it returns its body value and is not a
reusable generator constructor:

```clojure
(defn draw-sized-booleans! []
  (g/let [size (g/integer 1 10)
          xs (g/vector {:size size} (g/boolean))
          pair [size xs]]
    pair))
```

For stream tests, `g/chunkings` draws positive planned sizes and splits the
payload until it is consumed. Integer shrinking toward `1` keeps small writes
prominent, while vector shrinking removes planned splits:

```clojure
(defn draw-delivery! []
  (g/let [payload (g/vector {:max-size 4096} (g/octet))
          chunks (g/chunkings payload)]
    {:payload payload :chunks chunks}))
```

For a nonempty payload every chunk is nonempty, `(vec (mapcat identity chunks))`
equals `payload`, and shrinking may simplify delivery to one write or one-byte
chunks. Convert chunk vectors to the transport's byte-array representation only
at the I/O boundary. Call `draw-delivery!` from the property body, not at
namespace load time.

## Core controls

| Form | Behavior |
| --- | --- |
| `(h/draw! generator)` | draw from the current test case |
| `(h/draw! generator label)` | draw and log the labeled value at enabled verbosity |
| `(h/assume! condition)` | reject the current case when false |
| `(h/target! score)` | guide targeting toward a finite score |
| `(h/target! score label)` | target with a stable label |
| `(h/final?)` | true only during minimal failure replay |
| `(h/when-final ...)` | evaluate diagnostics only during final replay |
| `(h/fprn ...)` | print values to stderr only during final replay |
| `(h/note! ...)` | print according to configured verbosity |
| `(h/sample n generator)` | inspect up to `n` values |

`h/sample` returns up to `n` values when the underlying property run passes; the
engine may exhaust its choices before reaching `n`. Ordinary property failures
and flaky verdicts throw `::h/sample-failed` with the complete run result and
original cause data; usage, setup, and native harness exceptions from
`run-test!` propagate directly rather than being wrapped as sample failures.

## Observations and coverage

Current source (not v0.5.0) adds `(h/event! label)` for categorical hits and
`(h/observe! value label)` for finite numeric measurements. Labels are nonblank
Unicode strings without NUL, bounded to 256 UTF-16 code units. Numeric values
are converted to finite doubles; invalid calls are usage errors, not shrinkable
counterexamples. Calls require an active test case.

`:observations? true` collects bounded structured results. Categorical labels
count once per case; numeric labels retain count/min/max, not sample vectors.
`:show-statistics? true` separately enables libhegel's native stderr report;
never parse that report to decide coverage or confuse it with `*err*` capture.

```clojure
{:coverage {:scope :exploration
            :requirements {"boundary" {:min-count 1 :min-fraction 0.0}}}}
```

Coverage implicitly enables collection. Requirements use completed valid
exploration cases only; rejected, overrun, interesting and explicit final replay
cannot supply hits. Zero valid cases or missing labels fail, and numeric values
do not satisfy categorical requirements. Both thresholds apply; defaults are
one hit and zero fraction. A missed requirement changes an otherwise passing
result to `:passed? false`, `:status :coverage-failed`, without a native failure
blob or extra shrink run. Existing native failures/errors remain primary.

The C API does not expose each case's native subphase. Choose `:exploration`
explicitly for mixed phases. `:generation-only` coverage requires the caller's
explicit `:phases [:generate]`, which disables normal native reuse/target/shrink
work; never make that choice silently. Final replay is separately summarized.
Discarded draws within a valid case do not roll back frontend events: emit
final-value coverage after a successful draw when that is the intended check.

Read `docs/OBSERVATIONS.md` for result shapes, label cardinality, numeric
threshold rules and statistical limitations. These are observations, not
confidence bounds or proof of reachability. Retain independent model assertions
and mandatory witnesses. Coverage/observation settings are not exported in
schema-v1 counterexample bundles; a blob replay is not a coverage run.

## Stateful testing

Call `hs/run!` inside a Hegel property or `hegel.clojure-test/with` body:

```clojure
(require '[hegel.stateful :as hs])

(hs/run!
 {:initial-state {:model [] :sut (new-stack)}
  :rules
  [(hs/rule :push push-step)
   (hs/rule :pop
            {:precondition #(seq (:model %))}
            pop-step)]
  :invariants
  [(hs/invariant :model-matches-sut model-matches-sut?)]})
```

Each rule step is `(fn [state] next-state)`. A rule's optional
`:precondition` is evaluated before its step. False preconditions and failed
`h/assume!` calls reject that attempted rule without consuming the configured
`:stateful-step-count`; invariants do not run after a skipped rule. All other
ordinary rule exceptions fail the property; usage, native control flow and
marked inconclusive errors retain their abort/control classification. Invariants are
truth-valued predicates checked on the initial state and after every successful
rule.

libhegel owns sequence length, rule choices, shrinking, and per-case swarm
selection. Keep rule and invariant names and rule order stable across generated
cases and final replay. Construct fresh per-case mutable state inside the
property body. Check all preconditions before mutating a system because an
assumption cannot undo side effects.

An expensive external service such as a TCP server may be shared by the whole
property run, provided each property invocation creates a fresh connection and
starts from equivalent observable server state:

```clojure
(defn check-server! [opts]
  (let [server (start-ready-server!)]
    (try
      (h/run-test!
       opts
       (fn [_]
         (let [connection (connect! server)]
           (try
             ;; If reset is in-protocol, wait for its acknowledgement before
             ;; beginning generated traffic.
             (reset-case-state! connection)
             (hs/run!
              {:initial-state {:model (initial-model)
                               :connection connection}
               :rules protocol-rules
               :invariants protocol-invariants})
             ;; Terminate the protocol deterministically. For a stream request,
             ;; half-close writes and drain the bounded response to EOF.
             (half-close-write! connection)
             (read-response-to-eof! connection
                                    {:timeout-ms 1000
                                     :max-bytes 1048576})
             (finally
               (close-connection! connection))))))
      (finally
        (stop-server! server)))))
```

The server must stay alive around the entire `run-test!` call because shrinking
and the automatic final replay invoke the property body again. Put the
connection in the state machine's initial state so one case's operation
sequence shares it, but never reuse that connection across cases. Cleanup must
run for passing, rejected, failing, and final-replay cases. If the server cannot
reset or isolate state per case, do not share it: the shrink result would depend
on earlier candidates. For manual replay, start an equivalent server and call
the same wrapper with `(assoc opts :seed (bigint (:seed result)))`.
Use protocol completion signals rather than sleeps: for request streams, a
write-side half-close followed by bounded reads through response EOF gives each
case a deterministic end. Bound both elapsed time and response bytes, and fail
rather than accepting a timeout, size overrun, or truncated response as EOF. If
a rule or invariant throws before normal completion, `finally` closes the
connection as the abort signal; the server must still restore equivalent state
for the next fresh connection. Let connection, startup, timeout, and reset
failures surface; they are not reasons to call `h/assume!`. An in-protocol reset
must finish, including its acknowledgement, before generated traffic begins.
Shrink replay against the live service is sound only while every invocation
re-establishes equivalent case state. If the result has `:flaky? true`, treat
the minimized counterexample as untrusted and fix resource isolation or timing
first.

`hs/run!` returns the final state. On failure, the final exception data includes
`:hegel.stateful/trace`, and the stable origin names the failing rule or
invariant. Invalid state-machine declarations are setup errors: they abort the
run immediately instead of becoming counterexamples to shrink.

### Value pools

| Form | Behavior |
| --- | --- |
| `(hs/pool)` | create an empty pool for the current test case |
| `(hs/add! pool value)` | register a value and return the pool |
| `(hs/values-reusable pool)` | generator that draws without removing |
| `(hs/values-consumed pool)` | generator that draws and removes |
| `(hs/pool-size pool)` | active value count |
| `(hs/pool-empty? pool)` | true when no values remain |

Draw from the two value generators with `h/draw!`. An empty-pool draw is an
assumption failure: inside a stateful rule it skips the rule; at property scope
it rejects the generated case. Never retain a pool across test cases.

## Semantic trace rules

`hegel.trace` validates a complete, bounded vector of semantic events inside a
property. It does not own the event producer. Use it with compiler-aspect
journals, protocol harnesses, or application event logs, and call it only after
the generated action or state-machine checkpoint has completed.

The checker itself is portable and does not require a forked compiler or
runtime. Capturing events from Jolt compiler aspects currently requires an
aspect-enabled Jolt fork; explicit journals and other event producers work on
an unmodified supported runtime. `hegel.trace` and `hegel.history` remain
experimental while their contracts are exercised across more libraries.

```clojure
(require '[hegel.trace :as ht])

(ht/check!
 events
 [(ht/contiguous-sequence :journal-not-truncated)
  (ht/closed-lifecycles :operations-close)
  (ht/causal-parentage :parent-invoked-first)
  (ht/context-coherence :carrier-context-coherent)
  (ht/every-eventually
   :request-terminates
   #(and (= :request (:role %)) (= :enter (:phase %)))
   #(contains? #{:return :throw} (:phase %)))])
```

`check!` returns the original event vector when all rules pass. A false rule
throws with a stable `:hegel/origin`, `:hegel.trace/rule`, event count, and the
bounded events. Hegel therefore shrinks the generated values or stateful command
sequence which produced the invalid trace. Its third argument accepts
`{:max-events n}` and defaults to 256; exceeding the bound is itself a stable
property failure.

| Form | Contract |
| --- | --- |
| `(ht/rule name predicate)` | Create a stable named predicate over the complete event vector |
| `(ht/check! events rules)` | Check rules with the default 256-event evidence bound |
| `(ht/check! events rules {:max-events n})` | Check with an explicit positive bound |
| `(ht/ordered-sequence name opts)` | Check integer values as `:nondecreasing`, `:strictly-increasing`, or `:contiguous`, optionally per `:scope` and from `:start` |
| `(ht/event-model name opts)` | Fold events through `:initial` and required `:step`, checking optional `:invariant`, `:final`, and independent `:scope` models |
| `(ht/contiguous-sequence name start)` | Require contiguous integer `:seq` values, defaulting to start 1 |
| `(ht/closed-lifecycles name)` | Require each `:operation-id` to have `:invoke` (or legacy `:enter`) then exactly one `:return` or `:throw` |
| `(ht/synchronous-parentage name)` | Require each child lifecycle to be wholly nested in its declared parent |
| `(ht/causal-parentage name)` | Require each declared parent invocation to precede its child invocation, without constraining terminal order |
| `(ht/context-coherence name)` | Require invocation `:context-id` metadata to match along every declared parent edge |
| `(ht/every-eventually name trigger? outcome? correlate)` | Require a later correlated outcome for every trigger; correlation defaults to `:operation-id` |

Run checks outside compiler-aspect advice. Jolt's advice safety contract fails
open on advice exceptions, so an assertion inside advice can be swallowed while
the application operation correctly proceeds. A bounded ring journal must not
silently wrap during a test; `contiguous-sequence` detects a discarded prefix.
For `ordered-sequence`, `:value` defaults to `:seq`; `:scope` may select an
independent cursor or partition. Nondecreasing permits duplicates and gaps,
strictly increasing permits gaps only, and contiguous requires exact `+1`
steps.

Canonical async jolt-aspect-packs journals use `contiguous-sequence`,
`closed-lifecycles`, `causal-parentage`, and `context-coherence` together. A
parent may terminate before its asynchronous child, so
`synchronous-parentage` is appropriate only for dynamically nested work.
Terminal events omit carrier metadata; context coherence checks invocation
events. When a filtered view legitimately omits global journal events, use a
strictly increasing sequence rule instead of a contiguous one.

`event-model` calls `:step` as `[state event]`, then calls `:invariant` as
`[next-state event]`. After the complete trace or scoped subtrace, it calls
`:final` with the last state. Both checks default to true. `:scope` groups
interleaved events while preserving their order within each group. Keep the
model pure so generation, shrinking, and final replay begin from the same
`:initial` value.

## Canonical event profile

Current unreleased source adds `hegel.event-contract/check!` for explicit
canonical operation events and `check-envelope!` for the closed envelope
`{:contract-id "hegel.operation-events" :contract-revision "1" :events [...]}`.
Both return their original input unchanged. These pure APIs need no native
library. They do not replace or silently narrow generic trace/history rules.

The optional settings are `:max-events` (positive, default 256) and
`:sequence-start` (integer, default 1). Use an independent expected start to
detect truncated prefixes. Events have contiguous integer sequence numbers,
scalar operation IDs (integer/string/keyword/symbol), and complete
`:invoke`/`:return` or `:invoke`/`:throw` lifecycles. Invocations require
operation, parent, context and causal-link fields; nil root parents and empty
link vectors are explicit. Parents and links refer to earlier invocations;
child context matches its parent. Async children may outlive parents.

Link vectors are unique and sorted by tagged scalar spelling, not numerical
integer order (`[10 2]`, not `[2 10]`). Terminals need not repeat metadata.
Extra fields and payloads are preserved; models still check operation/payload
semantics. Generic legacy `:enter`, mixed event models and history ID domains
remain valid through their existing APIs, not through this canonical profile.

Bad options or unsupported envelope identity are usage errors. Semantic
failures use stable trace-rule origins and bounded event evidence. Event count
does not bound payload bytes or redact data. Transport/provenance validation,
profile semantics and linearizability are separate checks. See
`docs/EVENT_CONTRACT.md` and ADR0007 for exact boundaries and the pending
cross-repository stabilization gates.

## Bounded linearizability

`hegel.history` validates complete operation histories and searches for a
legal sequential-model witness while preserving real-time precedence. Each
event is a map with contiguous integer `:seq`, non-nil `:operation-id`, and a
`:phase` of `:invoke`, `:return`, or `:throw`. Invocations also require
`:operation`. Each operation must have exactly one invocation followed by one
terminal event.

The model step is `(fn [state operation] transition)`. A legal transition is
`{:state next-state}`; nil means the completed operation and observed outcome
are illegal in that state. The normalized operation contains
`:operation-id`, `:operation`, `:input`, `:outcome`, `:value`, `:invoke-seq`,
`:terminal-seq`, and the original `:invoke` and `:terminal` events.

| Form | Contract |
| --- | --- |
| `(history/operations events opts?)` | Validate and normalize the complete history |
| `(history/analyze initial step events opts?)` | Return `:linearizable`, `:not-linearizable`, or `:inconclusive` in `:status`, plus `:search` metadata and a `:witness` on success |
| `(history/linearization initial step events opts?)` | Return a witness or nil |
| `(history/linearizable? initial step events opts?)` | Return a boolean |
| `(history/check! initial step events opts?)` | Return a witness or throw with stable bounded Hegel evidence |
| `(history/rule name opts)` | Create a rule accepted by `hegel.trace/check!`; opts require `:step` and may contain `:initial` plus checker options |

Checker options are `:max-operations` (default 10 total operations),
`:max-search-steps` (default 100000 candidate considerations), optional
callable `:partition-by`, optional integer `:sequence-start`, and `:name` for a
stable `hegel.history` failure origin. The witness contains the selected
`:order`, normalized `:operations`, and `:final-state`. A partitioned witness
instead contains ordered `:partitions`; each partition starts from the same
supplied initial state and has its own order and final state.

Search is exhaustive only within its budget and can grow factorially, so keep
the operation bound small. Each candidate consumes one search step, even if
blocked by precedence, and the budget is global across partitions and
backtracking. Empty valid histories are decisive at budget zero. Exhaustion
returns `{:status :inconclusive :reason :search-budget :search ...}` from
`analyze`; the legacy witness/boolean/check APIs instead throw with
`:hegel/inconclusive? true`, stable origin and bounded evidence. Core, trace
and stateful wrappers preserve that abort rather than shrinking a false
counterexample. Preprocessing and arbitrary model callback wall time are not
bounded by candidate counts. Partitions must be independent models; the
checker intentionally ignores cross-partition predecessors within each model.

Evidence is
limited to twice the operation bound even when an oversized malformed history
is supplied. The checker rejects incomplete histories rather than completing
or dropping pending operations. Snapshot the journal only after every worker
has terminated. Record sequence assignment atomically with publication; on a
weakly ordered host, timestamps or non-atomic counters are not a sound
substitute for observation order.

## Run options

Common `run-test!` options:

| Option | Meaning |
| --- | --- |
| `:test-cases` | positive maximum valid generated cases; assumptions do not consume the budget |
| `:stateful-step-count` | positive state-machine round budget; libhegel defaults to 50 and rejected sequential rules do not consume it |
| `:seed` | numeric seed for replay with the same engine and property contract |
| `:derandomize?` | derive repeatable behavior when no seed is supplied |
| `:name` or `:database-key` | stable identity for derived seeds and persistence |
| `:database` | database path; `""` disables persistence |
| `:verbosity` | `:quiet`, `:normal`, `:verbose`, or `:debug` |
| `:report-multiple-failures?` | retain multiple stable failure origins |

libhegel 0.36 removes `:mode`, including `:single-test-case`. Supplying it is
an actionable pre-native usage error. Use `:test-cases 1` only when a one-valid-
case budget is intended: shrinking/replay may still invoke the property again,
and this is not the old no-shrink mode. Such runs skip the native simplest-
example probe and its large-initial-case health check.

The result includes `:passed?`, `:status`, `:seed`, `:test-cases`,
`:valid-test-cases`, `:invalid-test-cases`, `:overrun-test-cases`,
`:interesting-test-cases`, `:n-failures`,
`:failures`, `:final`, `:observed-failures`, `:flaky?`, and `:error`. The seed
is a string; use `(bigint (:seed result))` when rerunning by seed so the full
uint64 domain is preserved. Keep engine, property and settings unchanged.

`:observed-failures` contains up to 16 stable origins seen during exploration.
Each entry has `:origin`, `:count`, and structured `:first` and `:last` throwable
summaries with `:type`, `:message`, and `:data`. These observations are not
minimal counterexamples. They remain useful when final replay does not reproduce
or run-level nondeterminism has no failure blob.

```clojure
(when (:flaky? result)
  (doseq [{:keys [origin count first last]} (:observed-failures result)]
    (prn :origin origin
         :observations count
         :first-data (:data first)
         :last-data (:data last))))
```

There are two flakiness shapes. A shrunk failure that does not reproduce with
the same stable origin keeps its failure entry, records `:replay-origin`, and
sets `:flaky? true`. If libhegel detects different
outcomes or different generation for the same choice prefix, the result has
`:status :error`, `:passed? false`, `:flaky? true`, and the explanation in
`:error`; it has no counterexample to replay. Other run-level errors, including
health-check failures, still throw `:hegel.core/run-error`.

## Replay bundles

Current source adds `hegel.replay-bundle/from-result` for stable, reproduced
failed results and `hegel.replay-bundle.codec/encode` / `decode` for bounded
schema-v1 EDN. These pure namespaces need no native library or type checker.
Export requires the new result's captured `:replay-options`; do not invent
settings for older results. Keep uint64 seeds as decimal strings.

`(h/replay-bundle! expected-provenance bundle case-fn)` compares every
provenance field and replays native blobs directly with captured settings and
persistence disabled. Obtain expected provenance independently from the
current deployment/property manifest, never by copying the supplied bundle.
It requires the Hegel SHA, native version, runtime host/version/OS/architecture,
property ID, generator revision, and an explicit nil or versioned model
revision. These are caller assertions, not authenticated identities; the
adapter additionally checks the selected host and native version gate.

Results distinguish `:incompatible`, `:reproduced`, and `:not-reproduced`;
reproducing a failure is not a passing property. A seed rerun is not direct
blob replay. Usage, native and inconclusive errors propagate with cleanup.

Trace is omitted by default. An optional `:trace` envelope identifies its own
event contract/revision; `:redact-trace` runs before validation and its failure
must not fall back to raw data. Export omits exceptions, values, observations,
database paths/keys and display names. Blobs themselves may contain secrets
and cannot be redacted without changing replay. Execute only trusted blobs:
bounded EDN and matching versions do not bound native decompression or
arbitrary property code. Consult `docs/REPLAY_BUNDLES.md` in the source checkout
for the exact limits and transport boundary; this envelope does not settle
the experimental trace/history semantic contract.

## Materialized corpora

Available at merged source `88cc32cc3c39cb445fa16f725ff5f9c1db115858`, not
the historical v0.5.0 API. `(hegel.materialize/materialize! opts generator check!)`
returns a complete schema-v1 envelope or fails; omit `check!` only when no model
assertion is intended. Checks must throw on failure: returning false passes.
Options require canonical uint64 decimal-string `:seed`, `:count` in 1..256,
and provenance including Hegel/native/runtime/property/generator/model/seam
identities. Each position uses seed `(seed+i) mod 2^64` in a separate sequential
one-valid-case generation-only native run. Equal successful values are retained;
rejected, failed, flaky or incomplete runs never become successful partial output.

For baked consumption, require only `hegel.corpus`, decode the envelope, then
call `(corpus/consume! expected envelope)`. The independent trusted `expected`
map contains `:sha256`, `:provenance`, `:count` and
`:valid-case-policy :exact-valid-count`. Verification hashes exact stored UTF-8
payload bytes before parsing that payload and checking provenance/count; only then
may a consumer model inspect the returned payload's `:values`. Do not reprint
before hashing, trust a self-hash, auto-regenerate a bad fixture, or require the
native engine/installer/generator from the baked path. Provision dependencies
before going offline. Runtime provenance identifies the producer, not consumer.

Bounds and integrity do not establish privacy, model correctness, non-vacuity or
producer authenticity. Add a domain-specific closed fixture profile and explicit
witness controls when needed. Jolt digesting uses OpenSSL on Linux/macOS and
Windows CNG; provision crypto dependencies before isolation, not libhegel.
jank/CLR corpus support is not established by their focused compatibility CI.
Repeated prefix sealing and per-position native-context costs are unmeasured.

The db example is aspect-packs merge `71a123cbeaf1bb7994f6524e27abb861c7ddd2c2`,
with distinct live/offline aliases and independently pinned fixtures. Its
BB/JVM/Jolt three-platform matrix exercised actual restricted model execution,
not just successful loading. Consult `docs/MATERIALIZED_CORPORA.md` for exact
transport limits and the linked integration evidence.

## Concurrency

One `run-test!` call is sequential. The public options have no worker setting,
and libhegel's generation and adaptive shrinking depend on the preceding case
history. Do not thread cases within a run.

`hegel.clojure-test/with` isolates its report capture for the active host.
Concurrent engine safety has not been verified by jolt-hegel's test suite, so
concurrent native runs remain unsupported until that contract has a dedicated
integration test.

libhegel 0.33's concurrent state-machine protocol is not exposed. The current
`hegel.stateful/run!` implementation drives the round protocol with fixed
concurrency one so deterministic shrinking and final replay keep their
existing contract. Upstream machines declared with maximum concurrency above
one are intentionally nondeterministic and disable shrinking, replay,
targeting, persistence, and flakiness checks. A future Clojure API therefore
needs a separate shared-state, worker-context, and join-point contract; it
must not be added as a worker option to `run!`.

## Verification commands

```bash
# Jolt
JOLT_CACHE_DIR=.jolt-cache/jolt-hegel-<jolt-hegel-commit-sha> \
  jolt -A:test -m hegel.install
JOLT_CACHE_DIR=.jolt-cache/jolt-hegel-<jolt-hegel-commit-sha> \
  jolt -M:test

# Babashka
bb setup-native
bb test

# JVM Clojure
clojure -J--enable-native-access=ALL-UNNAMED -M:jvm:test -m hegel.install
clojure -J--enable-native-access=ALL-UNNAMED -M:jvm:test
```

Use the consuming project's established test command and selected executable
when they differ from these examples. For process-backed properties, pre-resolve
every parent and worker dependency set before generation and preserve the first
worker transcript when infrastructure setup fails.
