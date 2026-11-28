# Upstream adoption for the next release

This development checkout targets libhegel 0.53.2, Jolt 0.8.31 and Babashka
1.13.230. These are the v0.7.0 release pins. The
0.51.6 migration guide documents the earlier ABI transition.
[Jolt changelog](https://github.com/jolt-lang/jolt/blob/v0.8.31/CHANGELOG.md) · [libhegel changelog](https://github.com/hegeldev/hegel/blob/libhegel/0.53.2/libhegel/CHANGELOG.md) · [0.51.6 migration](LIBHEGEL-051-MIGRATION.md)

## Changes since Jolt 0.8.27 and libhegel 0.51.6

| Upstream change | Effect here |
| --- | --- |
| Jolt 0.8.31 provides library-scoped symbol lookup on POSIX. | When a packaged engine is already loaded, the shared-library override binds its own entry points through `pointer-fn`. Windows resolves them with `GetProcAddress`. Package tests load the previous libhegel patch and require the version check to identify it. |
| Jolt lowered the fixed cost of FFI allocation, free, confined arenas and bulk double writes. | Existing temporary allocation and cleanup paths benefit when run on the new runtime. The allocation comparison below supports keeping explicit cleanup. On Linux amd64 one warmed, seeded property (200 valid cases, two integer draws per case, libhegel 0.51.6, five timings per runtime) took a median 3.84 ms on 0.8.27 and 2.94 ms on 0.8.30. |
| Jolt exposes the exact running build. | External pilots now record `jolt.host/build-id` in the replay manifest. Two nightlies can now disagree on provenance even if they advertise the same release version. |
| Static linking and native regions | The Linux and macOS relocation checks now target 0.8.31 and run a property through shrinking and confirmed fixture extraction in the relocated binary. The Windows bcrypt link fix concerns static OpenSSL. Windows static libhegel still needs its own relocation test. The new `defextern` targets native numeric regions. The adapter uses aggregate structs, out parameters and managed handle lifetimes. The conformance project exercises source and compiled regions against JVM Clojure, including aliased arrays. [Native region properties](https://github.com/chucklehead-dev/clj-conformance/tree/59cc78aa5d2bff4c08791e33cd14b912fcd85cd7/packs/native-regions) |
| Jolt 0.8.28 extended callbacks to by-value structs and added `pointer-fn`. | The normal, quiet, and verbose runner paths pass Hegel’s native stderr sink directly to the engine. A separate Linux integration test receives real Hegel verbose output via an arena-owned Jolt callback and copies each UTF-8 line while valid. Hegel passes two pointers and a byte count, so this path uses an ordinary callback. The same test calls Hegel’s native stderr sink through its returned function pointer. |
| Hegel 0.52 changed how a frontend completes and reports a failing case. | The binding supplies a one-line summary when the engine asks for output and a full diagnostic when it asks for detail. Native CI exercises rejection and retry of incomplete diagnostics, quiet and normal reporting, and verbose output through a callback. |
| Hegel 0.53 added temporal precision and calendar filters. | Time and datetime draws land directly on the selected clock tick. Date generation can select weekdays and months inside an inclusive range. The engine can target leap days, year boundaries and clock limits during exploration. |
| Hegel 0.53.1 raised the minimum probability for sampled invariants to 1%. | The public state-machine API checks invariants on every eligible step. The 1% floor affects callers who explicitly select native sampling. |
| Hegel 0.53.2 changed mutation shrinking. | After five consecutive continuations exhaust the recorded choices, the engine moves on from that mutation. Expensive properties can avoid repeat executions of an unproductive candidate. |

## Calendar and clock properties

To exercise quarter-end reporting on business days, draw dates from the
last month of each quarter and restrict the weekday. For a millisecond clock,
draw times on millisecond ticks. Both generators still shrink within their
configured domains.

```clojure
(require '[hegel.generator :as g])

(g/date {:min {:year 2024 :month 1 :day 1}
         :max {:year 2024 :month 12 :day 31}
         :months [3 6 9 12]
         :weekdays [:monday :tuesday :wednesday :thursday :friday]})

(g/time {:precision :milliseconds})
```

The default for `g/time` and `g/datetime` is microseconds. Existing
`:microsecond` bounds retain their units. To exercise values a microsecond
apart or closer, choose `:precision :nanoseconds` and use a `:nanosecond`
field in each bound. Bounds and filters must contain at least one allowed value.

`g/timestamp` generates UTC instants as plain maps. For example, this generator
can produce the millisecond before the Unix epoch as well as the epoch itself:

```clojure
(g/timestamp {:min {:second -1 :nanosecond 999000000}
              :max {:second 0 :nanosecond 0}
              :precision :milliseconds})
```

The fraction always counts forward from the given signed epoch second.
The interval includes both endpoints, so the first map denotes −0.001 seconds.
The plain representation lets two hosts compare the same instant without
depending on their date-time classes.

## Native data and replay across the upgrade

Run the installer from the same Git checkout as the binding. It selects the
0.53.2 library for the host and verifies the asset digest. Update any explicit
`HEGEL_LIBHEGEL_LIBRARY` override to that release. A saved failure bundle
whose manifest names an earlier engine version will report `:incompatible`.
Keep the old deployment to replay its saved choices, or run the original
property on the new engine and export a new bundle. Cross-host consumers can
use the plain EDN input retained by `hegel.report/failure-fixtures`.
[Replay guide](REPLAY_BUNDLES.md)

## Changes made in this frontend

| Area | Adoption |
| --- | --- |
| Native ABI | The descriptor covers the released 0.53.2 header (233 exported functions); UTF-8 views, owned strings, aggregate returns, state-machine builders and generated jank/CLR adapters migrated. |
| Capture and replay | Follow native capture/confirmation and blob replay; retain caveats; expose stop-on-first-failure and nondeterminism strictness. Strictness defaults to `:error`. |
| FFI correctness | Check Jolt 0.8.27's stack-argument ABI probe when available. Older supported Jolt releases lack that probe. |
| Native distribution | Optional `packaging/jolt` dependency uses lazy `:jolt/native`, a checksum-verified build task, and static archives on Linux/macOS. Preloaded/static symbols avoid redundant shared-library loading. |
| Generator cost | Cache up to 64 compiled string/regex/grammar generators for each test case (additional generators use lexical ownership), with owned handles released before the native context. Constructors still validate eagerly. |
| Shrinking | Stable component-derived labels distinguish combinator shapes instead of giving every tuple, map, etc. the same label. |
| Generator defaults | Use native default-size sentinel (100, or minimum + 100 for larger minima) and native filter-attempt count. Explicit maximums remain explicit. |
| Stateful pools | Restore consumed values when filters or collection uniqueness discard a speculative draw; nested accepted draws remain part of their parent's rollback. |
| Settings | Expose named profiles, unbounded choices and reproduce-blob printing. Resolve native defaults and configured seeds before applying explicit options. Replay snapshots concrete settings and starts from `base`, avoiding later profile drift. |
| Grammars | `g/grammar` adds a validated Clojure data DSL; `g/grammar-str` accepts raw Lark. Both use native grammar generation/shrinking, start rule, size/depth bounds, alphabet options and explicit in-memory imports. |
| Stateful scheduling | Sequential rules accept positive finite `:weight`; native scheduling consumes it. |
| Antithesis | Source locations from `hegel.clojure-test`, optional `:pause-faults?` invariants, and correct observed-only failure reporting for unrecorded single-case workloads. Those failures have `:unrecorded? true`, `:reproduced? false` and no replay blob. |
| Other dependencies | JDK 25 minimum, babashka.ffi 0.1.3, Babashka 1.13.230, Malli 0.20.3, Typed Clojure 1.4.0, ClojureCLR 1.12.6, refreshed jank package pin and compatible CI runners. |

## Benefits inherited from the engines

These need no separate wrapper feature and are used by the updated dependency.
They are upstream improvements, not measured end-to-end speedup claims.

- Jolt's FFI stack-argument fixes, native allocation zeroing improvement,
  static-link path handling, and general runtime/AOT fixes. Its typed numeric
  array bulk-copy acceleration is available, but this adapter's existing byte
  transfers do not justify adding a new numeric-array conversion layer.
- Hegel's recursive and cross-failure shrinking fixes, improved float sampling
  and large-integer generation, earlier targeting for large runs, and stricter
  replay divergence detection.
- Hegel's cheaper draw spans, amortized constant-time pool insertion, bounded
  caches, safer database replacement and bounded decompression.
- Hegel's Unicode/regex updates and better minimal matches; embedded NUL regex
  patterns now also work through the frontend's length-delimited ABI.
- Hegel's long-workload memory fixes: choice recording can be disabled for
  single-case Antithesis runs, and empty clone/block output regions are released.
  This does not turn the experimental concurrent frontend into a production
  concurrent executor.

## Shared-library override timing on Jolt 0.8.31

Both runs preload the same libhegel 0.53.2 shared object. The default route
binds named functions. Setting the override to that same path selects
library-scoped pointers. On Linux amd64 the medians across nine fresh
processes per route were:

| Time measured | Default | Scoped override |
| --- | ---: | ---: |
| Process launch through first verified engine version | 715 ms | 563 ms |
| Loading the binding namespace and checking the engine version, after preload | 465 ms | 315 ms |
| One warmed 200-case property, two integer draws per case | 3.78 ms | 3.65 ms |

The launch measurement ends when the parent receives the flushed binding
timing line. The processes used warm filesystem and compilation caches, ran
on the same CPU, and alternated order by pair after one discarded pair.
Each process warmed up with five property runs, then timed twenty. Warmed
per-process averages ranged from 3.10–4.60 ms for the default and
3.48–5.14 ms for the override. Keep the existing binding paths. This workload
gives no performance reason to optimize override calls.

To repeat the binding and property timings from the checkout with an absolute
path to the shared library in `lib`:

```sh
env -u HEGEL_LIBHEGEL_LIBRARY jolt script/hegel/scoped_ffi_bench.clj "$lib"
HEGEL_LIBHEGEL_LIBRARY="$lib" jolt script/hegel/scoped_ffi_bench.clj "$lib"
```

## Decisions and remaining work

- **Keep explicit Jolt temporary allocation.** Repeating upstream's arena
  microbenchmark on 0.8.27 (Linux amd64, 30,000 allocations, five samples) gave
  medians 18.63 ms for alloc/free, 72.61 ms for an arena per allocation, and
  54.99 ms for batches of 100: approximately 3.90x and 2.95x slower. This is not
  an application benchmark. On 0.8.30, five samples of 30,000 64-byte allocations
  on Linux amd64, after warm-up and rotating measurement order, gave medians
  of 4.02 ms for explicit alloc/free, 43.26 ms for one arena per allocation,
  and 34.74 ms for arenas holding 100 allocations. We kept explicit cleanup.
- **Windows packaging:** the opt-in dependency supports dynamic loading;
  a statically linked Windows distribution remains unqualified. Linux/macOS
  relocation checks remove the original source and native-library paths.
- **Experimental joinpoints:** retain the existing compiler feature commit;
  official Jolt does not provide its required compiler extension. Its deleted
  process submodule source was replaced by the identical reachable upstream
  commit. Moving this lane to official Jolt requires upstream feature parity.
- **User-provided time zone data.** The [native callback test](../script/hegel/zone_loader_contract.clj) verifies two queries, one load and one release for a custom UTC+05:45 zone. Hegel’s `hegel_zone_loader_t` asks a
  frontend to load a zone on demand. Its zone name arrives as a
  `hegel_string_t` struct **by value**, an actual use for the callback support
  added in Jolt 0.8.28. A public loader would have to copy the borrowed name
  before returning, support concurrent requests for the same zone, keep the
  callback alive while Hegel owns it, and release loader state exactly once
  when Hegel drops it. The current output callback receives plain pointers
  and a byte count. `pointer-fn` handles the opposite call direction when
  Jolt receives a native function pointer and needs to invoke it.
- **Further temporal APIs.** The frontend now exposes UTC timestamps. Upstream can also
  generate time zones, zoned datetimes, offsets, durations and calendar spans. A useful next public
  generator would return the civil time, zone name, offset, and resolved instant
  for a daylight-saving transition. A cross-runtime property could then check
  which of two instants a repeated wall-clock time denotes. These types need
  portable Clojure result shapes before adding public generator constructors.
- **Additional API design:** custom grammar terminals, runtime profile
  registration, a process-global Unicode-version selector, native repeat
  wrappers, printer reflow controls and concurrent rule-group execution remain
  low-level ABI capabilities rather than new public frontend APIs. They need
  ownership/semantics contracts and dedicated use cases; the existing public
  operations already benefit from the engine updates above.
- **Release migration:** old native replay blobs/databases cannot be upgraded by
  relabelling provenance. Regenerate failures or use the old deployment to
  inspect them. Merge/tag/publication remain release-owner actions.

## Using the new options

```clojure
(g/grammar-str "start: \"hello\" | \"world\"")
(g/grammar-str "%import .words.word\nstart: word"
               {:imports {"words.lark" "word: \"hello\""}})

(h/run-test! {:profile "ci" :unbounded-choices? true :print-blob? false}
             (fn [_] ...))

(hs/rule :advance {:weight 2.0} (fn [state] ...))
(hs/invariant :consistent {:pause-faults? true} (fn [state] ...))
```

For Jolt 0.8.27+, select the optional dependency root at the commit you use:

```clojure
{:deps {io.github.chucklehead-dev/jolt-hegel
        {:git/url "https://github.com/chucklehead-dev/jolt-hegel"
         :git/sha "<full commit SHA>"
         :deps/root "packaging/jolt"}}}
```

Requiring a namespace from the packaged dependency acquires verified artifacts
when missing; an unused dependency stays inactive. Use the portable root for
pure-data consumers that must not acquire native files. `jolt build` can link the supplied
Linux/macOS archive. A working native linker is required. The portable root
continues to support explicit setup and `HEGEL_LIBHEGEL_LIBRARY`; that override
also takes precedence for a shared package. A statically linked Linux/macOS
executable rejects a nonblank override at startup. Rebuild it or use the shared
package to choose a different engine. The package build places files
at its declared dependency-local paths, irrespective of `HEGEL_CACHE_DIR`.

For the data-first grammar API and pure compiler, see [Grammars as Clojure data](GRAMMARS.md).
