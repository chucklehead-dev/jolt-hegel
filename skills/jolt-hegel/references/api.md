# jolt-hegel API

## Install

Require Jolt 0.4.15 and add the public release by full commit SHA:

```clojure
{:deps
 {io.github.chucklehead-dev/jolt-hegel
  {:git/url "https://github.com/chucklehead-dev/jolt-hegel.git"
   :git/sha "<release-commit-sha>"}}}
```

From the consuming project, install verified native dependencies:

```bash
joltc -m hegel.install
```

Replace `<release-commit-sha>` with the full commit behind the current release
tag; never copy the placeholder into a project. Use `HEGEL_CACHE_DIR` when the
dependency checkout is not writable. If Jolt cannot write its default AOT cache,
set `JOLT_CACHE_DIR` to a writable project or temporary directory. A local C
compiler is needed only when the target has no published shim.

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
            [hegel.generator :as g]))

(defn assert-passed! [result]
  (when-not (:passed? result)
    (throw
     (ex-info "property failed"
              (select-keys result
                           [:seed :n-failures :failures :flaky?]))))
  result)

(defn check-integer-roundtrip! []
  (assert-passed!
   (h/run-test!
    {:test-cases 200
     :database ""
     :verbosity :quiet}
    (fn [_]
      (let [n (h/draw! (g/integer))]
        (when-not (= n (parse-long (str n)))
          (throw
           (ex-info "integer text round-trip failed"
                    {:hegel/origin "integer-roundtrip/text"}))))))))
```

Property failures are thrown exceptions. `run-test!` returns normally
with `:passed? false` after shrinking and replaying the minimal
counterexample.

## Generators

All generators return `(fn [test-case] value)` and are consumed with
`h/draw!`.

| Form | Output and options |
| --- | --- |
| `(g/integer)` | full signed 64-bit range |
| `(g/integer min max)` | inclusive integer bounds |
| `(g/integer {:min x :max y})` | either integer bound may be omitted |
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

String options are:

| Option | Meaning |
| --- | --- |
| `:min-size`, `:max-size` | inclusive code-point length bounds |
| `:codec` | `:ascii`, `:latin-1`, `:iso-8859-1`, or `:utf-8` |
| `:min-codepoint`, `:max-codepoint` | inclusive Unicode code-point bounds |
| `:categories` | Unicode general-category allowlist, e.g. `[:Lu :Nd]` |
| `:exclude-categories` | Unicode general-category denylist |
| `:include-characters` | add characters to the generated alphabet |
| `:exclude-characters` | remove characters from the generated alphabet |
| `:alphabet` | exact character alphabet; cannot be combined with other character filters |

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
| `(g/list opts elements)` | Clojure list |
| `(g/set opts elements)` | set |
| `(g/sorted-set opts elements)` | sorted set |
| `(g/map opts keys values)` | map |
| `(g/sorted-map opts keys values)` | sorted map |
| `(g/hmap key-to-generator)` | fixed-key heterogeneous map |

Collection options are `:size`, `:min-size`, and `:max-size`. The forms without
an options map use engine-managed unbounded sizing. Duplicate set elements, map
keys, and unique-vector elements are rejected and redrawn.

`g/let` draws tagged generator right-hand sides and leaves ordinary expressions
alone:

```clojure
(g/let [size (g/integer 1 10)
        xs (g/vector {:size size} (g/boolean))
        pair [size xs]]
  pair)
```

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
`h/assume!` calls skip that attempted rule; invariants do not run after a
skipped rule. All other rule exceptions fail the property. Invariants are
truth-valued predicates checked on the initial state and after every successful
rule.

libhegel owns sequence length, rule choices, shrinking, and per-case swarm
selection. Keep rule and invariant names and rule order stable across generated
cases and final replay. Construct mutable systems under test inside the
property body so every case is fresh. Check all preconditions before mutating a
system because an assumption cannot undo side effects.

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

## Run options

Common `run-test!` options:

| Option | Meaning |
| --- | --- |
| `:test-cases` | maximum generated cases |
| `:seed` | numeric seed for exact replay |
| `:derandomize?` | derive repeatable behavior when no seed is supplied |
| `:name` or `:database-key` | stable identity for derived seeds and persistence |
| `:database` | database path; `""` disables persistence |
| `:verbosity` | `:quiet`, `:normal`, `:verbose`, or `:debug` |
| `:report-multiple-failures?` | retain multiple stable failure origins |

The result includes `:passed?`, `:status`, `:seed`, `:test-cases`,
`:valid-test-cases`, `:invalid-test-cases`, `:overrun-test-cases`,
`:interesting-test-cases`, `:n-failures`,
`:failures`, `:final`, and `:flaky?`. The seed
is a string; use `(parse-long (:seed result))` when replaying.

## Verification commands

```bash
joltc -m hegel.install
joltc -M:test
```

Use the consuming project's established test command when it differs from the
jolt-hegel repository's own harness.
