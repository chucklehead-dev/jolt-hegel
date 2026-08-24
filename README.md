# jolt-hegel

Property-based testing for [Jolt](https://github.com/jolt-lang/jolt), backed by
the [Hegel](https://hegel.dev/) generation and shrinking engine.

jolt-hegel runs properties against generated inputs, shrinks failures to
minimal counterexamples, replays the final failure, and reports the seed needed
to reproduce the run. It runs directly in Jolt through native FFI; no JVM or
sidecar process is involved.

The implemented surface includes primitive and formatted values, Unicode and
regex strings, protocol-oriented octet and chunking generators, collection and
composition combinators, dependent generation, `clojure.test` and framework-less
reporting, value pools, and engine-managed stateful testing with automatic swarm
rule selection.

## Requirements

- Jolt 0.7.23 or later
- libhegel 0.33.0, installed and verified by jolt-hegel
- A supported native target:

  | Target | Prebuilt libhegel |
  | --- | --- |
  | Linux x86_64 | yes |
  | Windows x86_64 | yes |
  | macOS arm64 | yes |

macOS also needs OpenSSL 3 available to Jolt. Windows native acquisition uses
PowerShell and does not require OpenSSL.

## Installation

Add the release commit as a SHA-pinned Git dependency. A test-only dependency
normally belongs in the test alias:

```clojure
{:aliases
 {:test
  {:extra-deps
   {io.github.chucklehead-dev/jolt-hegel
    {:git/url "https://github.com/chucklehead-dev/jolt-hegel.git"
     :git/sha "<release-commit-sha>"}}}}}}
```

Use the full commit SHA associated with the release tag. Then install the pinned
libhegel binary from the consuming project:

```bash
JOLT_CACHE_DIR=.jolt-cache/jolt-hegel-<release-commit-sha> \
  jolt -A:test -m hegel.install
```

Replace `test` with the alias containing jolt-hegel. If the dependency is in
top-level `:deps`, omit `-A:test`; without the dependency's alias, Jolt cannot
resolve the installer namespace.

Downloads are verified with SHA-256 and cached with the dependency. Temporal
generators bind libhegel directly through Jolt's by-value aggregate FFI; no
target-specific jolt-hegel shim or local C compiler is required.

The installer also compares the loaded `hegel.version` namespace with the
currently resolved dependency source. If Jolt reused AOT output from another
release, installation stops before fetching the wrong libhegel version. Re-run
installation and tests with a fresh cache directory keyed by the pinned SHA.
Use the same `JOLT_CACHE_DIR` for the subsequent Jolt test command.

## Choose an integration

Both integrations use the same generators, shrinking, replay, and result data.
Choose the boundary that matches the surrounding test suite:

| Suite | Entry point | Failure behavior |
| --- | --- | --- |
| `clojure.test` | `hegel.clojure-test/with` inside `deftest` | Reports one passing property or the final minimal failure through `clojure.test` |
| Any other runner or a program | `hegel.core/run-test!` | Returns a result map; the caller must treat `:passed? false` as failure |

In either form, put generated values in the property body, keep failure origins
stable, and keep resources needed by shrinking and final replay alive for the
entire property run.

## Use with clojure.test

`hegel.clojure-test/with` embeds a property in an ordinary `deftest`. Generator
bindings are drawn for each case, failures are shrunk, and only the final
minimal assertion is reported to `clojure.test`. The macro returns the
underlying `run-test!` result, but ordinary suites can ignore that value and use
their normal `clojure.test` runner and exit behavior.

```clojure
(ns example.reverse-test
  (:require [clojure.test :refer [deftest is]]
            [hegel.clojure-test :refer [with]]
            [hegel.core :as h]
            [hegel.generator :as g]))

(deftest reverse-roundtrip
  (with {:test-cases 200
         :database ""
         :verbosity :quiet}
    [xs (g/vector {:max-size 100} (g/integer))]
    (h/fprn :minimal-xs xs)
    (is (= xs (-> xs reverse reverse vec)))))
```

`g/let` provides the same dependent-binding behavior outside the initial
`with` bindings:

```clojure
(g/let [size (g/integer 1 10)
        xs (g/vector {:size size} (g/boolean))]
  [size xs])
```

## Stateful tests

Use `hegel.stateful/run!` for model-based tests that need generated operation
sequences. Rules receive the current state and return the next state.
Invariants run on the initial state and after each successfully applied rule.

```clojure
(ns example.stack-test
  (:require [clojure.test :refer [deftest]]
            [hegel.clojure-test :refer [with]]
            [hegel.core :as h]
            [hegel.generator :as g]
            [hegel.stateful :as hs]))

(deftest stack-agrees-with-model
  (with {:test-cases 200
         :stateful-step-count 50
         :database ""
         :verbosity :quiet}
    []
    (let [sut (atom [])]
      (hs/run!
       {:initial-state {:model [] :sut sut}
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

libhegel chooses rule sequences, their length, and a nonempty swarm subset for
each case; there is no separate swarm switch. Pass `:stateful-step-count` to
`run-test!` or `with` to replace libhegel's default 50-round budget. At the
supported concurrency of one, rejected rules do not consume that budget. Keep
rule names and order stable
across generation and replay. Construct mutable systems under test inside the
property body so every generated case and final replay starts fresh. Check a
rule's preconditions before mutation; a false precondition or failed
`h/assume!` skips that attempted rule without running invariants.

An expensive external service may instead wrap the whole property run when
each case opens a fresh connection or session and re-establishes equivalent
observable state. The service must remain alive through shrinking and final
replay. Use deterministic protocol signals, never sleeps. For a stream request,
half-close the write side and perform time- and byte-bounded reads through
actual EOF. Close the per-case connection in `finally`. If a failing case
aborts before half-close, connection close is the abort signal and the server
must still isolate the next fresh connection.

Value pools let later rules draw handles or resources created by earlier rules:

```clojure
(let [handles (hs/pool)]
  (hs/add! handles newly-created-handle)
  (h/draw! (hs/values-reusable handles)) ; keep it in the pool
  (h/draw! (hs/values-consumed handles))) ; remove it
```

`hs/pool-size` and `hs/pool-empty?` inspect the active values. Pools belong to
one test case and must not be retained between cases.

libhegel 0.33 also defines concurrent state machines. jolt-hegel currently
drives its round protocol at concurrency one; a public concurrent API remains
out of scope until state sharing, scheduling diagnostics, and nondeterministic
failure reporting have an explicit Jolt contract.

## Use without clojure.test

A property body draws values and throws when its invariant does not hold.
`run-test!` returns a result map; it does not report to a framework or choose a
process exit code.

```clojure
(ns example.integer-roundtrip
  (:require [hegel.core :as h]
            [hegel.generator :as g]
            [hegel.report :as report]))

(def result
  (h/run-test!
   {:test-cases 200
    :database ""
    :verbosity :quiet}
   (fn [_]
     (let [n (h/draw! (g/integer))]
       (when-not (= n (parse-long (str n)))
         (throw
          (ex-info "integer text round-trip failed"
                   {:hegel/origin "integer-roundtrip/text"})))))))

(when-not (:passed? result)
  (throw (ex-info "property failed" {:result result})))
```

For a suite that should continue after a property fails, use
`hegel.report/counting-runner` and `hegel.report/run!`. `run!` counts returned
failures and thrown setup or engine errors. At suite completion, use
`report/passed?` to select the surrounding runner's exit status:

```clojure
(let [runner (report/counting-runner)]
  (report/run! runner "integer text round-trip"
               #(h/run-test! opts property-fn))
  (System/exit (if (report/passed? runner) 0 1)))
```

Pass `{:reporter f}` to `counting-runner` to consume structured `:pass`,
`:fail`, and `:error` events instead of its default stdout report. Ordinary
property failures and engine-detected nondeterminism return `:passed? false`;
setup errors, health-check failures, and unexpected engine errors throw unless
wrapped by `report/run!`.

Failure origins must identify the property or assertion site and must not
contain generated values. Stable origins let Hegel group equivalent failures
and spend its shrink budget on the correct counterexample. Final replay counts
as reproduction only when it fails with that same origin; a different replay
origin is reported as flaky instead of being mistaken for the same bug.

Every result includes the selected seed as a string. To replay a run exactly,
pass it back as a number:

```clojure
(h/run-test! {:test-cases 200
              :seed (parse-long (:seed previous-result))
              :database ""}
             property-fn)
```

`:observed-failures` retains bounded, structured summaries of exceptions seen
during exploration, grouped by stable origin. This remains available when a
failure does not reproduce or libhegel reports run-level nondeterminism without
a counterexample.

One `run-test!` call executes cases and adaptive shrinking sequentially; there
is no worker option. The supported Jolt runtime provides dynamically scoped
`clojure.test` reporting, so separate `with` evaluations do not share a
process-global report sink. Concurrent native `run-test!` calls are not covered by
jolt-hegel's engine safety tests and should still be treated as
unsupported.

## Generators

Generators are passed to `hegel.core/draw!`.

| Generator | Value |
| --- | --- |
| `(g/integer)`, `(g/integer min max)` | signed 64-bit integer |
| `(g/octet)` | unsigned wire octet as an integer from 0 through 255 |
| `(g/boolean)`, `(g/boolean probability)` | boolean |
| `(g/double opts)`, `(g/double min max)` | 64-bit floating-point number |
| `(g/bytes opts)`, `(g/bytes min-size max-size)` | byte array |
| `(g/uuid)`, `(g/uuid version)` | canonical lowercase UUID string |
| `(g/ipv4)`, `(g/ipv6)` | canonical IP address string |
| `(g/date opts)` | ISO 8601 date string |
| `(g/time opts)` | ISO 8601 local-time string |
| `(g/datetime opts)` | ISO 8601 naive-datetime string |
| `(g/string opts)` | Unicode string |
| `(g/character opts)` | one-code-point string |
| `(g/regex-str pattern opts)` | regex-generated string |
| `(g/email)`, `(g/url-str)`, `(g/domain opts)` | formatted string |

Use `unchecked-byte` on an octet only at signed-byte API boundaries. Draw
`(g/integer -128 127)` when the property itself is defined over signed bytes;
`g/bytes` remains the byte-array generator.

Date bounds are maps such as
`{:year 2024 :month 1 :day 1}`. Time bounds use
`{:hour 0 :minute 0 :second 0 :microsecond 0}`. Datetime bounds
contain `{:date ... :time ...}`. All bounds are inclusive.

String options include `:min-size`, `:max-size`, `:codec`, code-point bounds,
Unicode category inclusion/exclusion, character inclusion/exclusion, and a
fixed `:alphabet`. Pass `:alphabet`, `:include-characters`, and
`:exclude-characters` as strings, not vectors or other character collections.
Regex generation uses full-match semantics by default; pass
`{:full-match? false}` to permit prefix or suffix text.

Composition and collection generators include:

| Form | Behavior |
| --- | --- |
| `(g/just value)` | constant value |
| `(g/sampled-from values)` | one value from a non-empty collection |
| `(g/fmap f generator)` | transform generated values |
| `(g/bind f generator)` | dependent generation |
| `(g/filter pred generator)` | retain values satisfying a predicate |
| `(g/one-of generators)` | choose a generator |
| `(g/optional generator)` | `nil` or a generated value |
| `(g/tuple generators...)` | fixed-size vector of draws |
| `(g/vector opts elements)` | vector, optionally unique |
| `(g/chunkings payload)` | nonempty vector chunks that concatenate to `payload` |
| `(g/list opts elements)` | list |
| `(g/set opts elements)`, `(g/sorted-set opts elements)` | set |
| `(g/map opts keys values)`, `(g/sorted-map opts keys values)` | map |
| `(g/hmap key-to-generator)` | fixed-key heterogeneous map |

Collection options are `:size`, `:min-size`, and `:max-size`; vectors also
accept `:unique?`. Use `g/composite-fn` to tag a custom
`(fn [test-case] value)` generator so `g/let` can distinguish it from an
ordinary function value.

The core namespace also provides:

- `assume!` to reject inputs outside a property domain;
- `target!` to guide Hegel toward larger finite scores;
- `final?`, `when-final`, `fprn`, and
  `note!` for final-replay diagnostics;
- `sample` for interactive generator inspection.

Prefer generators that directly produce valid inputs. Use
`assume!` for infrequent exclusions; rejecting most generated cases
makes a property ineffective.

## Native configuration

Most users only need `jolt -A:test -m hegel.install`, with the dependency's
actual alias substituted for `test`. These environment variables are available
for custom installations:

| Variable | Purpose |
| --- | --- |
| `JOLT_CACHE_DIR` | writable Jolt AOT cache directory; key it by the pinned release SHA |
| `HEGEL_CACHE_DIR` | writable native cache directory |
| `HEGEL_LIBHEGEL_LIBRARY` | caller-supplied libhegel path |
| `HEGEL_NATIVE_ARCH` | target override: `amd64` or `arm64` |
| `HEGEL_LIBHEGEL_RELEASE_BASE` | mirror for the pinned libhegel release |

## Agent skill

The repository includes an Agent Skill for installing jolt-hegel, identifying
useful properties, and writing tests:

```text
$skill-installer install https://github.com/chucklehead-dev/jolt-hegel/tree/main/skills/jolt-hegel
```

## Development

```bash
jolt -m hegel.install fetch-libhegel
jolt -M:test
(cd test/consumer && jolt -A:test -m consumer.smoke)
```

Implementation details are in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md),
[docs/DESIGN.md](docs/DESIGN.md), and the
[architecture decision records](docs/adr/README.md). Maintainer release steps
are in [docs/RELEASING.md](docs/RELEASING.md).

## Acknowledgments

The original jolt-hegel design and implementation were based in part on Kyle
Kingsbury's [hegel-clj](https://github.com/aphyr/hegel-clj), including its
imperative generator style, `run-test!` and `clojure.test` integration, and
final-replay diagnostics. jolt-hegel is now an independent Jolt implementation
which talks directly to libhegel, but that earlier Clojure binding established
the shape of its first public API.

## License

jolt-hegel is licensed under the [Eclipse Public License 2.0](LICENSE).
libhegel remains under its upstream MIT license; see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
