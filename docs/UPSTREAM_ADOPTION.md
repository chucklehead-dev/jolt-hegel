# Upstream adoption for the next release

This accompanies [the 0.51.6 migration](LIBHEGEL-051-MIGRATION.md).
The baseline is the repository before PR #122; published v0.5.0 is unchanged.
The primary verification targets are Jolt 0.8.30 and Babashka 1.13.230.
The adapter loads libhegel 0.51.6. A separate CI job executes the contracts
below against the released 0.53.2 library. [Jolt changelog](https://github.com/jolt-lang/jolt/blob/v0.8.30/CHANGELOG.md) · [libhegel changelog](https://github.com/hegeldev/hegel/blob/libhegel/0.53.2/libhegel/CHANGELOG.md)

## Changes since Jolt 0.8.27 and libhegel 0.51.6

| Upstream change | Effect here |
| --- | --- |
| Jolt lowered the fixed cost of FFI allocation, free, confined arenas and bulk double writes. | Existing temporary allocation and cleanup paths benefit when run on the new runtime. The allocation comparison below supports keeping explicit cleanup. On Linux amd64 one warmed, seeded property (200 valid cases, two integer draws per case, libhegel 0.51.6, five timings per runtime) took a median 3.84 ms on 0.8.27 and 2.94 ms on 0.8.30. |
| Jolt exposes the exact running build. | External pilots now record `jolt.host/build-id` in the replay manifest. Two nightlies can now disagree on provenance even if they advertise the same release version. |
| Static linking and native regions | The Linux and macOS relocation checks now target 0.8.30 and run a property through shrinking and confirmed fixture extraction in the relocated binary. The Windows bcrypt link fix concerns static OpenSSL. Windows static libhegel still needs its own relocation test. The new `defextern` targets native numeric regions. The adapter uses aggregate structs, out parameters and managed handle lifetimes, with no measured numeric kernel to move. |
| Hegel 0.52 changed how a frontend completes and reports a failing case. | The candidate CI job runs a failing property at quiet and normal verbosity, checks rejection and retry of an incomplete diagnostic, and reads the retained output and detail. The shipping adapter still needs to change capture, printer ownership, callbacks, completion and direct replay together. |
| Hegel 0.53 added temporal precision and calendar filters. | The candidate job exercises weekday filtering and bounds between adjacent microsecond ticks. Public generators and all native backends still need the new argument layouts before the runtime pin can advance. |
| Hegel 0.53.1 and 0.53.2 improved sampled invariants and shrinking. | These engine fixes become available to ordinary properties once the adapter uses 0.53.2. |

## Changes made in this frontend

| Area | Adoption |
| --- | --- |
| Native ABI | All 159 functions audited; UTF-8 views, owned strings, aggregate returns, state-machine builders and generated jank/CLR adapters migrated. |
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
also takes precedence in the packaged adapter. The package build places files
at its declared dependency-local paths, irrespective of `HEGEL_CACHE_DIR`.

For the data-first grammar API and pure compiler, see [Grammars as Clojure data](GRAMMARS.md).
