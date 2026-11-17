# Instaparse semantic coverage-gap study

Confirmed current-target defect: line/column annotation rejects valid hidden-root parse trees when the first child is a raw terminal string, or when the hidden root has no children. No target mutations were made. This report makes no claim that the fork introduced the defect.

## Pins and scope

Target `/workspace/pilot-targets/instaparse` at `6105ad194ab5f52c255fe475fb20c59f0dde64e9`; Hegel `/workspace/jolt-hegel` at `6321d5ab485db656a48823030453bc4b2d0af1cc`; Jolt 0.8.27; native libhegel 0.51.6 at the assigned explicit library path. `environment.log` records SHAs, runtime, platform, and native binary checksum. Target/Hegel remained read-only. No prohibited external packs, prior results, or sibling work were consulted.

Read skill SKILL.md, external-pilots.md, API run/generator/replay documentation, and REPLAY_BUNDLES.md. Reviewed target README, Performance.md, core.cljc, repeat.cljc, transform.cljc, line_col.cljc, core_test.cljc and repeat_test.cljc. This was a provisioned agent study, not a cold-install or human usability study.

## Contract and missing test family

README lines 809–846 describe `add-line-and-column-info-to-metadata` as annotating the supplied parse tree with positions and support initial line/column offsets. Hiding a root is an ordinary grammar feature. The function documentation in line_col.cljc promises annotation of a parse tree; it does not exclude hidden roots with literal children. Its existing hidden-root branches establish intended support for hidden roots. `transform.cljc` also explicitly handles sequences representing trees with hidden roots.

`core_test.cljc` line-col-test (around 804) covers visible-root word/number trees and start-line offsets. `repeat_test.cljc` extensively tests optimized equality and hiding, but does not compose hidden roots with line/column annotation. The new family varies root hiding, initial literal versus tagged child, empty tree, hiccup versus enlive, line breaks, and initial line/column offsets.

Root cause is in `src/instaparse/line_col.cljc` `add-line-col-spans`: hidden-root dispatch examines only the first child and requires a tagged map or tagged vector. Empty results and first-child strings fall through to IllegalArgumentException before any tagged descendants are annotated. This is direct source-level behavior, not generated parser ambiguity, Hegel failure, or an unsupported grammar.

## Independent domain and oracle

The fixed grammar is `S = a* T*; T = b | n`, also with `<S>`. Generate 0–8 initial `a` literals, 0–8 tagged `b`/newline tokens, either format, and starting line/column each 1–5. All generated text is valid and unambiguous. The independent position model folds the input prefix: newline increments line and resets column to 1; every other ASCII character increments column. Every surviving tagged node must retain its span and receive that model’s start/end coordinates. A separate property compares normal and memory-optimized tree values and node spans.

A semantic broken control changes a correctly annotated root start column to 2 and verifies the independent model rejects it with `annotation/start`. This is a harness control only, not an alleged target mutation or bug. Explicit witnesses cover both formats with `""`, `"a"`, `"b"`, `"a\nb"`, and `"\nb\n"`.

Initial strict equality of unannotated/annotated enlive trees was invalid for empty content (`nil` versus empty sequence). That oracle failure was corrected to checking preserved node spans and independently computed coordinates. Its original log is retained. It is not counted as a target defect.

## Results and minimized evidence

`study-run2.log` and `verification-original.log` explicitly report `:passed?`:

- Visible-root annotation: passed, 250 valid generated cases, 0 invalid/overrun/interesting cases.
- Memory optimization tree/span preservation: passed, 250 valid generated cases, 0 invalid/overrun/interesting cases.
- Hidden-root annotation: failed stably, nonflaky. Native counters: 289 test-cases, 1 valid, 0 invalid, 266 overrun, 22 interesting, 1 retained failure. These include search/shrinking work; they are not 289 independent generated samples. No coverage distribution claim is made.
- Existing focused target tests: 7 tests, 248 assertions, 0 failures/errors (`target-tests-original.log`).

The retained Hegel minimum is `{:text "", :format :hiccup, :line 1, :col 1}`. `minimum.edn` preserves it. The simpler ordinary grammar `<S> = a*` parses this empty text successfully, then annotation throws.

A nonempty minimum is `<S> = a` on `"a"`. The impactful mixed case is `<S> = a T; T = b` on `"ab"`: parsing returns `("a" [:T "b"])`, but annotation throws instead of adding start `[1 2]`, end `[1 3]` to `T`. The visible-root reference independently demonstrates those coordinates. Both formats fail, including mixed multiline input. `target-only/repro.clj` asserts all these known outcomes with target-only dependencies and no Hegel imports.

`failure.bundle.edn` is native replay exported via the public codec. `original/` retains the initial bundle, ordinary input and summary before verification reruns. `study.clj` declares independent expected provenance using the pinned deployment and property/model/generator revisions; it never extracts expected provenance from the bundle. A fresh-process replay reports `:status :reproduced`, `:reproduced? true`, `:flaky? false`. Changing the expected model revision rejects before the property executes. See `replay-original.log`.

## Verification

Run `bash /workspace/hegel-gap-study/parser-gap/verify.sh`. Every command has a 120-second bound. It checks source SHAs, replays saved native failure, runs properties, and runs the isolated target-only repro. Known target failures are positively asserted; unexpected success, mismatched origin, harness failure, or failed passing properties cause nonzero exit. Initial verification exited 0 in approximately 3 seconds. Existing target tests can be rerun with `JOLT_CACHE_DIR=$PWD/.jolt-cache timeout 120s /workspace/tooling/jolt/jolt -A:target-tests target_tests.clj` from this directory.

## DX and limits

Evidence ledger: choosing a finite-value generator → study-original.log → mistakenly borrowed `g/elements` → API reference correctly says `g/sampled-from` → corrected and reran. This was author error, not an API defect. Empty enlive equality → study-run.log → invalid nil/empty-content normalization assumption → changed to semantic span/position checks → 250 passing visible-root cases. Replay artifact capture → bundle/provenance docs → worked as documented → stable fresh-process reproduction.

The independent grammar and coordinate oracle required no shared helper or core Hegel API changes. Useful follow-up is a target regression matrix for hidden roots beginning terminal strings, empty output, and mixed tagged descendants in both output formats. A likely fix is format-aware traversal that leaves raw terminals unchanged and supports empty sequences. No fix was implemented or claimed. Tests ran only on Jolt; other supported runtimes and the complete upstream suite were not run. Bounds deliberately exclude supplementary Unicode and arbitrary transformed overlapping spans. No performance, leak, exhaustive coverage, or provenance authentication claims are made.
