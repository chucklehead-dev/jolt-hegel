# Augur numerical/model gap study

Two real calibration contract defects were found in unchanged target source. Both have ordinary minimized inputs, target-only reproduction, encoded native bundles, and successful fresh-process replay. The weighted isotonic primitive and bounded beta/gamma posterior models passed. No target mutation was used. This is an agent self-report, not a human usability study.

## Pins and execution

- Target `/workspace/pilot-targets/augur`: `58fef3f2077515c216cd7a295e4599e8579ad5bb`.
- Hegel `/workspace/jolt-hegel`: `6321d5ab485db656a48823030453bc4b2d0af1cc`, loaded through `:local/root`.
- `/workspace/tooling/jolt/jolt`: v0.8.27; Linux amd64; native `/workspace/jolt-hegel/.hegel-lib/libhegel_c.so`, 0.51.6.
- Independent deployment/property provenance is in `provenance.edn`; it is never extracted from replay artifacts. `verify.sh` checks both checkout SHAs.
- All work and Jolt caches stay in this scratch directory. Target and Hegel source remain read-only. Every investigation execution used `timeout 120s`.

Run `./verify.sh`. Exit status 0 is recorded in `logs/verify-final.exit`. Verification explicitly expects the two known target defects, their exact ordinary witnesses, and native `:reproduced` outcomes; unexpected repairs or harness failures fail verification. Passing properties must return `:passed? true`; controls and target failures must return `:passed? false`, nonflaky.

## Defect 1: equal forecasts produce order-dependent isotonic recalibration

Contract: `src/augur/calibrate.clj:102-105` promises the isotonic fit of outcomes on forecasts (CORP). With one unique forecast, its least-squares fitted probability is the empirical success fraction, independent of observation order. For two outcomes false/true, minimizing q²+(1-q)² gives q=0.5. The domain uses nonempty, equal-length data, finite probabilities in [0,1], boolean outcomes, and no unsupported input.

Native minimum (`tied-minimum.edn`): `{:p 0.0 :ys [false true]}`. Calling `((cal/isotonic-map [0.0 0.0] [false true]) 0.0)` returns **1.0**, expected **0.5**. Reversing the outcomes returns 0.5. Target-only witnesses also reproduce this at interior p=0.5 and boundary p=1.0, so the issue does not depend on endpoint semantics.

Cause: lines 107-109 sort observations but retain separate entries for identical forecasts; the isotonic primitive receives `[0 1]`, which is already ordered. Lines 112-118 then select the last fitted entry of the tie, yielding 1.0. The primitive itself is operating correctly on its sequence contract; the forecast-to-function adapter fails to enforce equality at identical x values.

Coverage gap: `test/augur/calibrate_test.clj:30-38` has repeated forecasts, but all its tie groups place true observations before false observations. That order causes the primitive to pool each group and masks the defect. The new property independently checks the empirical mean over 1..20 outcomes at one drawn forecast; it does not duplicate target implementation.

Useful fix direction: aggregate identical forecast values into empirical means with count weights before isotonic regression and step lookup. Add permutations of each tie group and the two-point fixture as regressions. No fix was made here.

## Defect 2: online calibration violates explicit monotonicity at a bin boundary

Contract: `src/augur/calibrate.clj:69-74` says "so a higher forecast is never calibrated lower." Options comply with documented requirements: ascending edges `[0.5 1.0]`, shrink=10.0, forget=1.0. Tables are constructed only through public `learn`, rather than inventing internal states.

Native minimum (`online-minimum.edn`): `{:successes 0 :failures 1}`. Learn one false outcome with forecast 0.75 under key :k. The resulting table is `{:k {:pos [0.0 0.0], :n [0.0 1.0]}}`. Then:

- `calibrate(..., 0.5, opts)` = **0.5**.
- `calibrate(..., 0.500001, opts)` = **0.4545463636363637**.

A higher forecast is calibrated lower by about 0.04545, far above the 1e-10 absolute comparison tolerance. The generated family varies successes in the lower bin and failures in the higher bin, each 0..100. The engine shrank to a single total observation. Zero total observations are allowed by `learn` and do not fail.

Cause: line 78 replaces only the queried bin's representative probability with p, while other bins use their centers. Lines 79-81 consequently compute a different isotonic problem on each query. Being ordered within either fitted vector does not imply order across separate calls. Here neither call needs pooling, yet the change of bin changes the shrinkage formula discontinuously.

Coverage gap: `test/augur/calibrate_test.clj:21-28` compares only 0.45 and 0.65 in one large-data scenario. It does not compare forecasts immediately across bin boundaries or sparse/empty adjacent bins.

Useful follow-up: decide how to implement a globally monotone mapping while preserving intended shrinkage and empty-bin behavior; regression tests must compare values from separate calls across every bin boundary. The current docstring combines requirements that need an explicit design choice. This study establishes the contradiction with the current implementation, not a proposed replacement estimator.

## Other properties and controls

`study.clj` independently implements the max-min interval-average characterization of weighted isotonic regression: fitted value i is max over a<=i of min over b>=i of the weighted mean on [a,b]. This is structurally different from the target's pool-adjacent-violators algorithm. Inputs: lengths 1..12, integer y in [-20,20], positive integer weights 1..20. Absolute tolerance 1e-10. **500 passing cases**. Explicit empty, increasing, decreasing, equal, unequal-weight and mixed-sign witnesses also pass in `controls.clj`.

Beta/gamma conjugate posterior update properties use priors a,b in 1..20; n in 0..100 and dependent k in 0..n; exposure in 1..100. Independent closed forms check posterior mean and evidence for both families with 1e-10 absolute tolerance. **500 passing cases**. These are bounded update checks, not evidence about extreme special functions or sampling distributions. No stochastic target routines were used; Hegel seed is fixed at 98271.

A pack-local semantic negative control substitutes an unpooled identity fit for isotonic regression on `[a 0]` for a in 1..20. It fails at the intended `control/unpooled` origin under the same independent oracle, confirming that the harness detects semantic disagreement. It is explicitly a control, not a target defect.

## Results and replay

Original successful generation transcript: `logs/generate-02.log`. The earlier `logs/generate-original.log` is preserved as authoring-error evidence.

| Property | passed? | total test-cases | valid-test-cases | interesting-test-cases | failures | flaky? |
|---|---:|---:|---:|---:|---:|---:|
| tied forecast empirical rate | false | 150 | 48 | 14 | 1 | false |
| online monotonicity | false | 45 | 2 | 21 | 1 | false |
| weighted isotonic independent model | true | 500 | 500 | 0 | 0 | false |
| beta/gamma posterior model | true | 500 | 500 | 0 | 0 | false |

All invalid-test-case counts were zero. Counts are reported using Hegel's actual field names; totals include engine work and must not be described as independent random samples. No categorical coverage threshold was asserted. Explicit witnesses above establish boundary-family presence; no exhaustive coverage claim is made.

`tied-bundle.edn` and `online-bundle.edn` encode the original retained failures. `logs/replay-original.log` records fresh-process `:reproduced`, `:reproduced? true`, `:flaky? false` for both. The replay harness deliberately changes the independently supplied generator revision and verifies `:incompatible` without executing the property. `verify.sh` replays these original bundles without regenerating or overwriting them.

The target-only `target_repro.clj` requires Augur and clojure.edn only, never Hegel/native APIs. `logs/verify-final.log` includes exact outputs, replay, controls, and passing properties. The selected unchanged target suites (calibrate, beta, gamma) pass **17 tests / 70 assertions**, zero failures/errors. Parent agent additionally reported a full-suite run of 196 tests / 748 assertions; that is parent-provided evidence, not a run performed by this agent.

## DX evidence ledger and limitations

- Retained failure -> `:final first :exception ex-data :input` -> ordinary minimum and native bundle worked as documented; no Hegel correctness defect observed.
- First source load -> unmatched parenthesis in study authoring -> preserved EOF log, fixed locally. First target-only run likewise had a delimiter typo; preserved `logs/verify-original.log`, fixed locally. These are agent authoring errors, not product bugs.
- Replay -> separately declared provenance and mismatch test -> both failures reproduced in a new Jolt process; no assistance needed beyond documentation. Native tooling/dependencies were preprovisioned, so no cold-install claim.
- Documentation used: skill SKILL.md, external-pilots reference, API generator/runner/replay sections, docs/REPLAY_BUNDLES.md. Target source/tests inspected: calibrate, beta, gamma, score and pool. No external packs, prior results, or sibling work were inspected.
- Follow-up classification: target regression tests and estimator corrections. No new Hegel core API is supported by this evidence. A small documentation example about tie-aware numerical models could be useful, but this is a single agent study.
