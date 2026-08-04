# jolt-hegel API

## Contents

- [Install](#install)
- [`clojure.test` property template](#clojuretest-property-template)
- [Custom-runner property template](#custom-runner-property-template)
- [Generators](#generators)
- [Combinators and collections](#combinators-and-collections)
- [Core controls](#core-controls)
- [Stateful testing](#stateful-testing)
- [Run options](#run-options)
- [Concurrency](#concurrency)
- [Verification commands](#verification-commands)

## Install

Select the Jolt executable from the consuming project's toolchain contract,
then add the public jolt-hegel release by full commit SHA. The v0.1.2 release
repository was authored against Jolt 0.4.15, but a modern consuming project may
carry its own verified compatibility evidence. Never choose a stale installed
`joltc` merely because this release's historical documentation names it. A
test-only dependency normally belongs in the project's test alias. In the
commands below, set `JOLT_BIN` to the absolute project-selected executable:

```clojure
{:aliases
 {:test
  {:extra-deps
   {io.github.chucklehead-dev/jolt-hegel
    {:git/url "https://github.com/chucklehead-dev/jolt-hegel.git"
     :git/sha "<release-commit-sha>"}}}}}}
```

From the consuming project, activate the alias which contains jolt-hegel when
installing the verified native dependencies:

```bash
JOLT_CACHE_DIR=.jolt-cache/jolt-hegel-<release-commit-sha> \
  "$JOLT_BIN" -A:test -m hegel.install
```

If jolt-hegel is instead in the top-level `:deps` map, omit `-A:test`. Replace
`test` with the consuming project's actual alias name; otherwise the installer
namespace is not on Jolt's source roots.

Replace `<release-commit-sha>` with the full commit behind the current release
tag; never copy the placeholder into a project. Without a local checkout, list
and resolve the remote tags with:

```bash
git ls-remote --tags --sort=-v:refname \
  https://github.com/chucklehead-dev/jolt-hegel.git 'v*'
```

The highest version tag is listed first; confirm that it is the stable release
you intend to pin. For an annotated tag, use the full SHA on its `^{}` line (the
peeled commit), not the tag-object SHA on the plain tag line. A lightweight tag
has no peeled line, so its plain line already names the commit.

Use `HEGEL_CACHE_DIR` when the dependency checkout is not writable. If Jolt
cannot write its default AOT cache, set `JOLT_CACHE_DIR` to a writable project
or temporary directory. Key it by the pinned release SHA so another jolt-hegel
release cannot reuse its compiled namespaces. The installer compares the loaded
release with the resolved source checkout and fails before fetching a mismatched
shim when it detects stale AOT output. Cached downloaded shims carry a
jolt-hegel release marker; if the marker does not match, the installer fetches
and verifies the current release's checksum instead of trusting the shared
native cache. A local C compiler is needed only when the target has no published
shim.

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
API boundary that requires Jolt's signed byte representation. In the other
direction, `(bit-and signed-byte 0xff)` recovers the unsigned octet. Use
`g/bytes` when the property needs an array.

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
| `(g/chunkings payload)` | vector chunks that concatenate to `payload` |
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
the same wrapper with `(assoc opts :seed (parse-long (:seed result)))`.
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
`:failures`, `:final`, `:observed-failures`, `:flaky?`, and `:error`. The seed
is a string; use `(parse-long (:seed result))` when replaying.

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

There are two flakiness shapes. A shrunk failure that does not reproduce keeps
its failure entry and sets `:flaky? true`. If libhegel detects different
outcomes or different generation for the same choice prefix, the result has
`:status :error`, `:passed? false`, `:flaky? true`, and the explanation in
`:error`; it has no counterexample to replay. Other run-level errors, including
health-check failures, still throw `:hegel.core/run-error`.

## Concurrency

One `run-test!` call is sequential. The public options have no worker setting,
and libhegel's generation and adaptive shrinking depend on the preceding case
history. Do not thread cases within a run.

`hegel.clojure-test/with` also serializes complete property runs with a global
report lock. On Jolt, `clojure.test/report` is not dynamically bindable, so the
integration must use process-global `with-redefs` while it captures exploration
reports. Independent concurrent `run-test!` calls do not use that report lock,
but concurrent shim/engine safety has not been verified by jolt-hegel's test
suite. Treat concurrent direct runs as unsupported until that contract has a
dedicated test.

## Verification commands

```bash
JOLT_CACHE_DIR=.jolt-cache/jolt-hegel-<release-commit-sha> \
  "$JOLT_BIN" -A:test -m hegel.install
JOLT_CACHE_DIR=.jolt-cache/jolt-hegel-<release-commit-sha> \
  "$JOLT_BIN" -M:test
```

The first command assumes the dependency is in `:test :extra-deps`; omit the
alias only for a top-level dependency. Use the consuming project's established
test command when it differs from the jolt-hegel repository's own harness. The
SHA-keyed cache is especially important after changing the dependency pin.
