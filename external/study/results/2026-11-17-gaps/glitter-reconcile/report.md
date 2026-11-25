# Glitter reconciliation test-gap study

Two stable headless-renderer defects found in the unchanged target. All three failing properties have retained ordinary inputs, native replay bundles, and successful fresh-process replay. A separate target-only script reproduces both defects without Hegel. No issues filed, commits pushed, or target files changed.

## Pins and isolation

- Target: `/workspace/pilot-targets/glitter-core`, SHA `95b4d94322e4d901091d9d45b15b52b8c9146d54`.
- Hegel: `/workspace/jolt-hegel`, SHA `6321d5ab485db656a48823030453bc4b2d0af1cc`.
- Runtime: `/workspace/tooling/jolt/jolt`, `jolt v0.8.27`, Linux x86_64.
- Native: `HEGEL_LIBHEGEL_LIBRARY=/workspace/jolt-hegel/.hegel-lib/libhegel_c.so`, expected API/native version 0.51.6, successfully native-gated in replay. Native SHA256 and exact command output in `environment.txt`.
- Used the jolt-hegel SKILL.md, references/external-pilots.md, relevant API sections and docs/REPLAY_BUNDLES.md. Did not inspect packs, prior results, or sibling studies. No subdelegation.
- Target local/root dependency resolution failed for unrelated time dependencies because network fetch was unavailable. Preserved `deps-original.edn` and `logs/probe.log`; final consumer places unchanged target src/test on classpath and Hegel via local/root. No time namespace is needed by this focused study. Parent independently advised this workaround after the same failure; it had already been applied here. This is provisioning assistance, not a cold-install result.

## Gap, domain and independent oracle

Existing `test/glitter/core_test.clj` contains four tests/six assertions: initial construction, one right rotation, one mount hook, and event-map node propagation. The right-rotation regression checks the fixed `insert-before` path; it does not cover relocation with `append-child`, arbitrary insert/remove sequences, preserved keyed identity across sequences, or descendant unmount when an ancestor disappears.

Valid domain: unique integer keys 0..7 on sibling `:li` elements under one `:ul`; ordinary string text and `:data-id` attributes. No duplicate keys, aliases, timers, unmount transitions, external DOM edits or custom renderers. Every case constructs a fresh renderer/root and threads both `:vdom` and returned unmount state between calls. Frames contain 0..6 nodes and 2..5 transitions; a separate permutation domain has 0..7 nodes. Lifecycle domain is a leaf nested in 0..5 ordinary `:ul` ancestors, then either removed or rendered unchanged.

The snapshot oracle builds expected tag/attribute/text vectors directly from desired keys, independent of target vdom/diff/mutation logs. It also checks `identical?` for every surviving key between adjacent frames. The lifecycle oracle expects one mount plus one unmount for a removed leaf; unchanged render expects only mount. It also checks root removal. The source contract is `core/reconcile` at line 1037 (DOM should match requested hiccup), `core/reusable?` at line 664 (same tag/key eligible for reuse), and `get-hooks-to-call` at line 1016 (explicitly collects detached descendant hooks).

Semantic broken control: an independently built snapshot with a duplicated first node is rejected by the same equality assertion, with the expected stable origin. This is a harness control only; no target mutation is presented as a defect. Positive boundary witnesses cover empty trees, noop, known fixed right rotation, insertion, removal, direct unmount, and nested unchanged lifecycle.

## Defect 1: append relocation duplicates a live child

Minimal permutation: `[[0 1 2] [1 2 0]]`. Expected final text `["1" "2" "0"]`; actual `["0" "1" "2" "0"]`. First and last entries are the identical atom. Mutation log is exactly `[[:append-child "li" :to "ul"]]`.

`core/move-nodes`, lines 753–780, explicitly uses append-child to move the first existing node to the end. The headless `test_renderer.clj` append-child implementation (line 75) only conjoins; it does not remove the existing occurrence. The analogous insert-before implementation (line 56) already removes the old occurrence before splicing, and its fixed right-rotation regression passes here. This is a different, still-broken protocol operation, not rediscovery of that fixed regression. It can corrupt subsequent get-child/reconcile behavior.

The broader edit generator also minimized to `[[0 1] [1 2 0]]`; expected `["1" "2" "0"]`, actual `["0" "2" "1" "0"]`. Both failures have origin `keyed/snapshot` and the same root cause. Files: `permutation-{minimum,evidence,summary,bundle}.edn`, `keyed-{minimum,evidence,summary,bundle}.edn`.

## Defect 2: removed descendants retain attachment and miss unmount

Minimum `{:depth 1 :remove? true}` corresponds to rendering `[:ul [:li {:glitter/on-mount record :glitter/on-unmount record} "leaf"]]`, then nil using prior state. Root becomes empty; expected lifecycle `[mount unmount]`, actual `[mount]`.

`test_renderer.clj` line 24 implements `attached?` as unconditional true. Core directly schedules the removed parent hook; descendant hooks are discovered by `get-hooks-to-call` by checking whether their nodes remain attached. Because the fake reports true even after the ancestor is removed, the descendant hook is skipped. A depth-zero direct removal and a depth-five unchanged render pass, isolating this from general hook dispatch. Source comments explicitly describe this detached-node hook sweep, so this expectation is not inferred merely from browser behavior.

Files: `lifecycle-{minimum,evidence,summary,bundle}.edn`. This is a limitation/defect of the shipped headless renderer; no claim is made that GTK/AppKit implementations have the same issue.

## Outcomes and counts

| Property/run | Verdict | Engine test cases | Valid | Overrun | Interesting |
| --- | --- | ---: | ---: | ---: | ---: |
| Permutations, seed 142857 | failed, nonflaky | 1076 | 101 | 967 | 8 |
| Arbitrary keyed frames, seed 142857 | failed, nonflaky | 3615 | 1132 | 2447 | 36 |
| Lifecycle, seed 142857 | failed, nonflaky | 40 | 7 | 22 | 11 |
| Ordered insertion/removal frames, seed 424242 | passed | 500 | 500 | 0 | 0 |

Each failing result explicitly had `:passed? false`, `:status :failed`, `:n-failures 1`, `:flaky? false`; no invalid cases. Counts are Hegel aggregate run counters including shrinking/replay work, not independent generated samples. The nominal 500-case failing budgets stop on discovery then shrink, and can produce larger total/valid counters. Separate generation/shrink/diagnostic counts were not instrumented; do not interpret these totals as coverage frequencies.

Passing generated test: 500 sequences of 2..8 sorted subsets from the same key domain exercise actual snapshots and surviving node identity under insertion/removal without reordering survivors. This does not establish arbitrary reordering correctness. Existing focused tests pass (4 tests, 6 assertions). Explicit witnesses and the semantic negative control pass their intended expectations. No distribution thresholds are claimed.

Fresh-process native replay reproduces all three retained failures, nonflaky. For each bundle, a deliberately mismatched independent generator revision returns `:incompatible` and executes zero callbacks. Expected provenance is declared in `src/study.clj`, not derived from bundle metadata. Target-only ordinary repro succeeds with expected-known-failure assertions, no Hegel dependency/native loaded. `verify.sh` exits 0 on this exact target and exits nonzero if positive checks fail, replay fails, SHAs/runtime mismatch, or known behavior changes. It writes fresh logs and never regenerates saved bundles.

## Friction and limitations (agent self-report)

- Dependency resolution was infrastructure failure, not target/Hegel bug. Full target suite including time/action-log was not runnable through the original dependency graph. Focused actual core tests were run unchanged.
- First property implementation read keyword `:data-id` from renderer atoms, while core intentionally supplies string attribute names. The initial failure was an invalid oracle, preserved in `logs/generate-original.log`; fixed after inspecting `set-attr-val`. It is excluded from target findings.
- Hegel generation, shrinking, retained ex-data, bundle codec, replay and mismatch protection worked after that correction. The standard generator namespace shadow warning appears in logs and is not a failure.
- Native replay artifacts retain choices for these bounded properties; no timing/concurrency claims. Memory retention, real renderer attachment semantics, event listeners, style state, transitions, alias lifecycle and broader nested identity are not covered.

Evidence ledger: initial examples lacked opposite relocation and descendant teardown → independent snapshot/lifecycle properties → two stable target-only defects with native replay. Attribute lookup mistake → source inspection → corrected oracle and passing positive witnesses. Missing time artifacts → source-path focused consumer → useful bounded run; not full-suite success.

## Runnable verification and follow-up

Run `timeout 120s /workspace/hegel-gap-study/glitter-reconcile/verify.sh`. Exact commands and original transcript paths are in `commands.md`; original successful verification transcript is `logs/verify-original.log`.

Useful target follow-ups: make append-child relocate an existing child; model headless parent/attachment state so removing an ancestor detaches descendants; add these minima as source-level regression tests with snapshots, identities and lifecycle counts. No fixes were applied. Hegel guidance follow-up: explain aggregate shrinking counters next to :test-cases; current retained-input/replay documentation was sufficient, and this study does not justify a new core API.
