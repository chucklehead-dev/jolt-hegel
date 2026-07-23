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

- Jolt 0.4.15
- libhegel 0.30.1, installed and verified by jolt-hegel
- A supported native target:

  | Target | Prebuilt libhegel | Prebuilt jolt-hegel shim |
  | --- | --- | --- |
  | Linux x86_64 | yes | yes |
  | Windows x86_64 | yes | yes |
  | macOS arm64 | yes | yes |

- A C compiler compatible with `gcc` only when a prebuilt shim is
  unavailable

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
libhegel binary and target-specific shim from the consuming project:

```bash
JOLT_CACHE_DIR=.jolt-cache/jolt-hegel-<release-commit-sha> \
  joltc -A:test -m hegel.install
```

Replace `test` with the alias containing jolt-hegel. If the dependency is in
top-level `:deps`, omit `-A:test`; without the dependency's alias, Jolt cannot
resolve the installer namespace.

Downloads are verified with SHA-256 and cached with the dependency. If the
release has no prebuilt shim for the target, the installer builds
`native/hegel_shim.c` locally with `gcc` or
`CC`.

The installer also compares the loaded `hegel.version` namespace with the
currently resolved dependency source. If Jolt reused AOT output from another
release, installation stops before fetching the wrong version's shim. Re-run
installation and tests with a fresh cache directory keyed by the pinned SHA.
Use the same `JOLT_CACHE_DIR` for the subsequent Jolt test command. Downloaded
shims also carry a release marker, so a shared `HEGEL_CACHE_DIR` cannot silently
reuse a verified binary from an older jolt-hegel release.

## Use with clojure.test

`hegel.clojure-test/with` embeds a property in an ordinary `deftest`. Generator
bindings are drawn for each case, failures are shrunk, and only the final
minimal assertion is reported to `clojure.test`.

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
  (with {:test-cases 200 :database "" :verbosity :quiet}
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
each case; there is no separate swarm switch. Keep rule names and order stable
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

## Use without clojure.test

A property body draws values and throws when the property does not hold.
`run-test!` returns a result map, so the surrounding test runner must
treat `:passed? false` as a test failure.

```clojure
(ns example.integer-roundtrip
  (:require [hegel.core :as h]
            [hegel.generator :as g]
            [hegel.report :as report]))

(defn integer-roundtrip! [runner]
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
    (integer-roundtrip! runner)
    (println "Ran properties;" (report/failure-count runner) "failures")
    (flush)
    (System/exit (if (report/passed? runner) 0 1))))
```

Ordinary property failures return `:passed? false`. Engine-detected outcome or
generator nondeterminism returns `:status :error`, `:flaky? true`, and an
`:error` explanation. Setup errors, health-check failures, and unexpected
engine errors still throw. `hegel.report/run!` counts both forms, reports the
seed and diagnostics, and lets the suite continue. Pass `{:reporter f}` to
`counting-runner` to consume structured report events instead of stdout.

Failure origins must identify the property or assertion site and must not
contain generated values. Stable origins let Hegel group equivalent failures
and spend its shrink budget on the correct counterexample.

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
is no worker option. `hegel.clojure-test/with` also serializes complete runs
because Jolt requires process-global report capture. Concurrent independent
`run-test!` calls are not covered by jolt-hegel's shim/engine safety tests and
should be treated as unsupported.

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
fixed `:alphabet`. Regex generation uses full-match semantics by default; pass
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

Most users only need `joltc -A:test -m hegel.install`, with the dependency's
actual alias substituted for `test`. These environment variables are available
for custom installations:

| Variable | Purpose |
| --- | --- |
| `JOLT_CACHE_DIR` | writable Jolt AOT cache directory; key it by the pinned release SHA |
| `HEGEL_CACHE_DIR` | writable native cache directory |
| `HEGEL_LIBHEGEL_LIBRARY` | caller-supplied libhegel path |
| `HEGEL_SHIM_LIBRARY` | caller-supplied or build-output shim path |
| `HEGEL_NATIVE_ARCH` | target override: `amd64` or `arm64` |
| `HEGEL_LIBHEGEL_RELEASE_BASE` | mirror for the pinned libhegel release |
| `HEGEL_SHIM_RELEASE_BASE` | mirror for the matching jolt-hegel release |
| `CC` | compiler for the local shim fallback |

## Agent skill

The repository includes an Agent Skill for installing jolt-hegel, identifying
useful properties, and writing tests:

```text
$skill-installer install https://github.com/chucklehead-dev/jolt-hegel/tree/main/skills/jolt-hegel
```

## Development

```bash
joltc -m hegel.install fetch-libhegel
joltc -m hegel.install build-shim
joltc -M:test
(cd test/consumer && joltc -A:test -m consumer.smoke)
```

Implementation details are in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md),
[docs/DESIGN.md](docs/DESIGN.md), and the
[architecture decision records](docs/adr/README.md). Maintainer release steps
are in [docs/RELEASING.md](docs/RELEASING.md).

## License

jolt-hegel is licensed under the [Eclipse Public License 2.0](LICENSE).
libhegel remains under its upstream MIT license; see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
