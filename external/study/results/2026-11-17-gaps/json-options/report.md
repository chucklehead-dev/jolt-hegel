# yyjson-jolt nonnumeric options gap study

**Result: no target defect found.** Four independently authored Hegel properties passed 400 valid cases each (1,600 total per run), with explicit witnesses. A deliberately wrong local first-wins oracle failed, minimized to the empty key, and replayed in a fresh process. That failure is a harness sensitivity control, **not a target defect**. No target or Hegel source was edited.

## Deployment and verification

- Target SHA: `e9562890a5f74b1cfd8added087860e7533df605`.
- Hegel SHA: `6321d5ab485db656a48823030453bc4b2d0af1cc`.
- `/workspace/tooling/jolt/jolt`: v0.8.27; native Hegel 0.51.6.
- Linux amd64; GCC 14.2.0. Full output and native artifact digest: `logs/deployment.log`; runtime binding version: `logs/runtime.log`.
- Consumer uses local/root dependencies; native target built in this scratch directory with the prescribed cc command. Jolt cache also stays here.

Run `/workspace/hegel-gap-study/json-options/verify.sh`. It checks source SHAs, rebuilds native target, executes all properties and witnesses, requires the intended control failure, replays that control in a new process, checks provenance mismatch rejection, and runs target-only witnesses. Each executable command is bounded by 120 seconds. The script returns nonzero for any unexpected verdict, failed witness, build error, replay failure, or control that passes. The completed property/replay invocation exited 0, as did the separately added target-only command. Existing repository tests were inspected but not executed; this report establishes only this added consumer pack on this host.

## Gap and independent contracts

Existing `test/yyjson/core_test.clj:108` tests each option with small isolated examples. It has broad string roundtrip witnesses but no duplicate-key tests, no read-key collisions, and no combined nested key/value/pretty transform property. `README.md:51` explicitly says duplicate keys are last-wins. `src/yyjson/core.clj:136` documents map-key transformation and value-fn applied to every map value before writing; `obj` passes the original key to value-fn. `value` applies read-key transformation while associating each entry. The raw-NUL branch promises a byte offset. Consulted line references are recorded in `logs/contract-lines.log`.

| Property | Domain and oracle | Outcome |
|---|---|---|
| options | Recursive maps/vectors of strings, bools and nil. Lowercase and NUL-prefix every written key; prefix string map values with their original key plus NUL. Independently recursively transform the original structure, retaining last mapped key, and compare parsed compact/pretty output. | 400 pass; 174 map roots and 226 other roots |
| escaping | Same AST, independent encoder emits every Unicode scalar as JSON `\\uXXXX` or a valid surrogate pair. Target parser must recover AST; separately target writer/reader must roundtrip. | 400 pass |
| duplicates | Ordered key/value pairs form raw JSON objects, preserving duplicates; keys include a/A/empty/NUL/emoji. Fold pairs with assoc after identity, lowercase, or constant-nil read transform. Nested map keys transform too. | 400 pass; modes 102/162/136 |
| nul-errors | Insert raw NUL after a valid string array prefix. Compare invalid category and UTF-8 byte count of the prefix. | 400 pass |

AST generation bounds: depth 5, leaves 25, vectors/maps size at most 4; duplicate list size at most 8; strings at most 14 code points from ASCII case variants, slash, backslash, quote, newline, CR, tab, NUL, é, and emoji. No JSON numbers generated. Integer generator use selects an option mode only. This is a deliberate bounded domain, not full Unicode or arbitrary-depth coverage.

Mandatory witnesses include empty containers/scalars, nested maps inside arrays, NUL keys and values, astral escapes, duplicate keys ending in nil/false, lowercasing collisions, constant nil read keys, raw-NUL prefixes after multibyte text, invalid lone surrogates/trailing data/trailing commas, and unsupported non-string write-key results. These do not depend on random coverage. Read-key collision behavior beyond exact duplicate keys is supported by source; it is not separately promised in README. No unspecified numeric behavior was tested.

Counts in `logs/*-result.edn` are native exploration counts (all phases), not independent samples during shrinking. Every positive property had 400 valid, zero invalid, zero overrun, zero interesting, zero failures, `:passed? true`, and `:flaky? false`. Positive runs had zero final replay cases. Same-seed reruns are repeatability evidence, not extra independent coverage.

## Evidence and controls

`study.clj` contains properties, explicit witnesses, sensitivity control, explicit verdict checks, and failure capture. `target-only.clj` requires only target code and asserts four basic contract witnesses; it is not a failing bug reproduction. `replay.clj` reads a native replay bundle but obtains expected provenance from the independently declared deployment/property manifest in `study.clj`.

The first-wins control disagrees with the actual correct last-wins result for `{ "": true, "": false }`. It fails at stable origin `control/first-wins`; the retained ordinary input is the empty key, expected map `{"" true}`, actual map `{"" false}`. `control-input.edn` and `control-bundle.edn` retain this **control only**. Immutable first copies are under `logs/original-control-*`. Native replay reports `:reproduced`, true, nonflaky in a fresh process. A wrong generator revision reports `:incompatible` before executing a callback that would throw. Since no real defect exists, there is no target-bug minimum or target-bug replay bundle.

## Command history and friction

- Initial authoring shell command failed with exit 2 because a nested shell single quote was broken by an apostrophe in a comment. Partial source was repaired using a direct heredoc. This is agent shell quoting error, not target/Hegel behavior.
- First runnable attempt: `timeout 120s ./verify.sh > logs/original-run.log 2>&1`, exit 1, reader EOF from a missing parenthesis in failure-export code. Original log retained.
- Corrected source: same command to `logs/run-2.log`, exit 0, all four properties and control behaved as expected.
- Added saved control and fresh process replay: same command to `logs/run-3-replay.log`, exit 0. Native replay and mismatch gate passed.
- Target-only command appended to verify.sh and independently executed with identical env, exit 0; `logs/target-only.log`.
- Native namespace emits a `let` replacement warning. No setup/native/Hegel failures observed beyond the authored reader error.

This was a provisioned-environment agent study, not a human usability test or cold-install measurement. No assistance or reference packs were used. No sibling work was inspected.

## Useful follow-ups

Add small permanent examples for duplicate-key last-wins and read-key collision behavior, and a nested option-interaction property alongside existing examples. Document whether transformed read-key collisions are intentionally guaranteed. If further budget is available, examine exceptions thrown by callbacks and value-fn conversion that introduces nested maps; neither resource leak freedom nor those callback exception paths is established here. No core Hegel API change is justified by this run; the authoring failures were local harness mistakes.
