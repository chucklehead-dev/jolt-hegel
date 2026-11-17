# First external-pilot and naive-agent results

The reference packs passed six properties with 200 valid generation cases each,
all required category-presence checks, all six semantic broken controls,
fresh-process native replay, deliberate provenance-mismatch rejection and
ordinary minimized value fixtures. No target defect was found in those bounded
domains. [Reference evidence](../evidence/2026-11-16-reference/execution.json)
records the source fingerprint and execution environment. Timings include
startup and are not performance benchmarks.

Six fresh agents then independently implemented the tasks from public docs and
pinned target sources, without the reference implementations or prior
conversation. Their original reports, source, prompts, journals and artifacts
are preserved under [results/2026-11-16](results/2026-11-16/). They are agent
self-reports, not observations of human users. A parent independently reran all
six sets of submitted commands; [verification.json](results/2026-11-16/verification.json)
records successful exits for baseline, control, native replay and value-fixture
checks. This verifies the implementations, not every claim in the journals.

## Outcomes

| Task | Agent baseline valid cases | Agent's minimized semantic control | Fresh-process replay and fixture |
| --- | ---: | --- | --- |
| [Parser acceptance](results/2026-11-16/01-parser-acceptance/report.edn) | 200 | `(0)` rejected after grouping support was removed | Both passed |
| [Parser optimization](results/2026-11-16/02-parser-optimization/report.edn) | 200 | `a` loses its sole repeated child | Both passed |
| [Parser meaning](results/2026-11-16/03-parser-meaning/report.edn) | 300 | `(0+1)` evaluates as -1 after addition becomes subtraction | Both passed |
| [JSON roundtrip](results/2026-11-16/04-json-roundtrip/report.edn) | 500 | `{"" nil}` changes key type under a keyword-converting decoder | Both passed |
| [JSON Unicode](results/2026-11-16/05-json-unicode/report.edn) | 500 | U+10000 becomes U+10001 under an off-by-one surrogate encoder | Both passed |
| [JSON error offset](results/2026-11-16/06-json-error-offset/report.edn) | 300 | `é`: expected byte offset 3 versus scalar offset 2 | Both passed |

All requested baseline coverage categories were observed. These are 2,000
completed valid case executions across the agent baselines, not 2,000 unique or
statistically independent inputs. Shrink/confirmation executions are separate.
Controls are deliberately broken local behavior, not discovered upstream bugs.
Generated maxima need not reach configured maxima: the optimization agent's
0–32 bound reached only 27 in that sample and reported that limitation.

## Evidence-backed improvements

1. **Make retained input capture and replay easier to find.** Every report
   discusses some aspect of ordinary minimized input capture, nil snapshots,
   or replay/result serialization. Quiet mode suppresses structured snapshots;
   the retained failure exception still provides explicitly attached input.
   Several agents used callback atoms or first searched the wrong result path.
   We clarified exact result paths, quiet behavior and retained exception data
   in the public README/replay documentation. The existing external runner is
   a complete worked example. These documentation edits occurred after all
   probes finished; their usability effect has not been measured.
2. **Contrast immediate draws with generator construction.** The acceptance
   agent used `g/let` at top level and received `no Hegel test case is currently
   bound`; `g/bind` solved it. The existing API reference stated the distinction,
   but the agent still missed it. We added an adjacent top-level `g/bind`
   example and an explicit contrast with immediate `g/let` execution. Consider
   a more actionable error only after a follow-up probe; no core API changed.
3. **Keep diagnostic data distinct from portable artifacts.** Multiple reports
   noted that `pr-str` of an entire result can contain `#error` printer forms.
   Replay bundles and plain input fixtures were sufficient. The docs now state
   that a printed result is not the portable replay format.
4. **Clarify target-side behavior separately.** The meaning agent initially
   hid the Instaparse root and obtained a sequence instead of the expected
   tagged tree. JSON agents compiled native output into scratch because the
   target's default loader writes into its source checkout. These are target
   integration/documentation findings, not missing Hegel generators.
5. **Explain counters before changing their semantics.** Some control runs had
   substantial overrun counts during shrinking. Their causes were not isolated;
   do not attribute them to target invalidity or infer a performance defect.
   All retained failures were stable and replayed. A follow-up should record
   bounded phase-specific evidence if overruns become costly.

The parent reference author separately assumed variadic `one-of`; it accepts a
collection. That is a single authoring observation, not a repeated naive-agent
finding. Keep signatures explicit in examples and test discoverability before
redesigning combinators.

## Scope and remaining questions

- The raw-NUL error-offset path is rejected by yyjson-jolt's wrapper before its
  native parser is called. The subsequent valid parse proves continued API
  usability, **not** native parse-error cleanup. A future malformed-JSON case
  should deliberately enter the native failure path.
- JSON roundtrip could miss paired reader/writer defects; the independent
  Unicode-encoding property provides a separate oracle for its stated domain.
- Coverage thresholds establish category presence only. Add explicit boundary
  witnesses where exact extrema are part of the intended contract.
- Several transient executor disconnects and truncated broad documentation
  reads occurred. These are tool/infrastructure observations; human
  discoverability and comprehension remain unmeasured.
- No human assistance or solution hints were given. The parent answered one
  executor-availability question; the affected agent recorded it. Sources,
  toolchain and dependency manifests were pre-provisioned, so this was not a
  cold-install study.
- The next domain packs remain augur and glitter-core, followed by duratom for
  resource/fault/replay lifecycle questions. Ebb/ensemble need explicit schedule
  evidence; native replay of generated values does not replay an OS schedule.

The next evaluation should test the documentation changes with fresh authors
and include a passing run that silently misses an important input category.
Use the [study protocol](README.md) for the human follow-up. Do not treat six
same-family agents as independent human usability participants.

## Archived artifacts

Archived scripts retain original absolute workspace paths. Adapt the paths and
provision the exact pinned sources to run them elsewhere. Some raw result dumps
contain printed exception objects; use the validated bundle codec or the plain
fixture files for machine-readable transport. Native binaries and caches are
excluded. Original logs are retained alongside parent verification logs so a
successful rerun does not erase the initial mistakes. The original baseline
Hegel SHA is intentionally preserved; do not rewrite old provenance to make a
bundle appear compatible with a different deployment.
