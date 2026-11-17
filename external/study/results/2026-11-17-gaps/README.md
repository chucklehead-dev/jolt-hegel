# Source test-gap study: 2026-11-16–17

Six fresh agents inspected the pinned projects' source and existing tests, then
authored independent Hegel properties. Parent verification reproduced **seven
target defects** and reran all six submitted verification scripts successfully.
No target source was changed and no upstream issue was filed.

## Findings

| Target | Confirmed behavior | Evidence and suggested regression |
| --- | --- | --- |
| augur | Equal forecasts `[0.5 0.5]` with outcomes `[false true]` calibrate to 1.0; reversed outcomes give 0.5. Both should give empirical rate 0.5. | [Numerical report](augur-models/report.md): tie-group permutations |
| augur | After one false observation at 0.75, online calibration maps 0.5 to 0.5 but 0.500001 to 0.45454636, contradicting its monotonicity contract. | [Numerical report](augur-models/report.md): adjacent-bin boundaries and sparse data |
| augur | Explicit and overflow expiry bypass `:max-keys`; cap 1 can retain 100 ordinary key records. | [Ledger report](augur-ledger/report.md): caps after every mutating operation, excluding the documented `::other` bucket |
| augur | Resolving three distinct keys with cap 1 and a shared benchmark crashes during the second eviction. | [Ledger report](augur-ledger/report.md): repeated nested benchmark aggregation |
| glitter-core | Key order `[0 1 2]` → `[1 2 0]` produces `[0 1 2 0]` in the shipped headless renderer, duplicating the same live node. | [Renderer report](glitter-reconcile/report.md): append relocation, actual snapshots and identity |
| glitter-core | Removing an ancestor skips descendant unmount hooks because headless `attached?` always returns true. | [Renderer report](glitter-reconcile/report.md): ancestor teardown and descendant lifecycle |
| Instaparse | Line/column annotation rejects valid hidden-root results beginning with literals, including mixed literal/tagged children and empty results. | [Parser report](parser-gap/report.md): hidden roots × output formats × annotation |

Glitter findings concern `glitter.test-renderer`, a public source namespace.
They do not establish defects in GTK/AppKit renderers or the core reconciler.
The append defect is distinct from the already-fixed `insert-before` path.
The Instaparse finding is present in this fork; no claim is made that the fork
introduced it. Full defect narratives, source locations, expected contracts,
minimal inputs and fix directions are in the linked reports. These are local
bug-report drafts, not submitted issues.

## Passing checks and limits

| Agent task | Passing generated cases | Scope |
| --- | ---: | --- |
| [JSON numbers](json-numbers/report.md) | 4,500 | Exact bounded integer reads, arbitrary integer/decimal writes, finite doubles, ratios and container/options combinations |
| [JSON options](json-options/report.md) | 1,600 | Nested options, independent Unicode escapes, duplicate/transformed keys, NUL byte errors |
| [Augur models](augur-models/report.md) | 1,000 | Independent weighted-isotonic oracle and beta/gamma posterior updates |
| [Augur ledger](augur-ledger/report.md) | 200 | Independent stateful model in its stated domain |
| [Glitter](glitter-reconcile/report.md) | 500 | Ordered insertion/removal sequences and surviving identity |
| [Instaparse](parser-gap/report.md) | 500 | Two independent parser/annotation properties |

Total: **8,300 passing cases** in the original positive runs. Parent repeats
are not added to this count. Failing-property counters include shrinking and
diagnostic work and are not independent generated sample counts. Controls are
deliberate pack-local defects and are not included among the seven findings.
JSON produced no established defect; exact integer *reading* outside the
signed/unsigned 64-bit union remains a documentation/domain clarification.

The parent also ran augur's full existing suite (196 tests, 748 assertions)
and glitter's five dependency-free test namespaces (9 tests, 17 assertions),
all passing. Full glitter dependency resolution failed on unavailable time
artifacts; the focused run does not replace that suite. The JSON numerical
agent's full-suite bootstrap also failed; the options agent inspected, but did
not run, the existing suite. Selected parser tests passed 248 assertions.
This round ran Linux amd64 only; the maintained earlier packs' CI platform
coverage must not be attributed to these new experiments.

## Reproduction and provenance

`manifest.json` records four target pins, all matching remote HEAD at study
start, Hegel `6321d5ab485db656a48823030453bc4b2d0af1cc`, Jolt 0.8.27 and
libhegel 0.51.6. Preserve those pins when replaying original bundles. All seven
defects have plain minimized inputs, target-only repros and native replay;
fresh-process replay and independent-provenance mismatch rejection passed.
Two glitter generated properties exercise the same append defect, so three
glitter bundles represent two defects.

Each task directory contains its exact `task.txt`, source, dependency manifest,
`verify.sh`, report, logs and artifacts. Originals were copied **before** parent
verification. `parent-verification/` contains parent rerun output;
`verification.json` records exit statuses. No binaries or caches are archived.
Some historical scripts use absolute original workspace paths. To reproduce,
restore their scratch paths or adapt path references while retaining dependency
pins and original bundle provenance. The current branch later gains this
archive; verification deliberately requires the original Hegel commit.

Verification exits zero when the known defect, replay, controls and passing
properties behave as recorded. Zero does **not** mean the target is defect-free.
No agent was supplied earlier pack implementations, sibling findings or a
reference solution. Provisioning and parent assistance are documented in the
reports. One parser agent turn suffered an authentication infrastructure error
and resumed from saved artifacts; that is not a Hegel usability finding.

## DX/API conclusions and next work

The updated retained-input, generator-signature and replay guidance supported
all six tasks. Remaining mistakes were local oracle/authoring errors, not
evidence for a new core API. Numerical semantics and fake-renderer fidelity
needed explicit source-contract review. This is an agent study, not evidence
of human comprehension or usability rates.

Prioritize augur's ledger crash and unbounded expiry, then its calibration
contracts. Fix glitter's renderer fidelity before relying on it for wider
lifecycle studies. Promote the minimized inputs into target regression tests,
then add maintained augur/glitter packs and platform CI after fixes; these
archived experiments are not yet new green maintained CI packs. The online
calibration fix needs an estimator-contract decision, not merely a mechanical
patch. Upstream issue submission and target fixes remain follow-up work.
