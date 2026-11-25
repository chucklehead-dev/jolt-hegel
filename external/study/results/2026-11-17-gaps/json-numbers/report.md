# JSON numerical test-gap study

No target defect was established. Five independently authored properties passed: 4,500 reported valid Hegel cases, plus explicit boundaries. A deliberately lossy test-only negative control failed, minimized, and replayed in a fresh process. This is not a target mutation or alleged target defect.

## Pins and setup

- Target: `e9562890a5f74b1cfd8added087860e7533df605` at `/workspace/pilot-targets/yyjson-jolt`.
- Hegel: `6321d5ab485db656a48823030453bc4b2d0af1cc` at `/workspace/jolt-hegel`.
- Jolt 0.8.27 at `/workspace/tooling/jolt/jolt`; supplied libhegel 0.51.6.
- Target native library compiled in scratch with its documented `cc -O3 -shared -fPIC ... -lm` command. `YYJSON_JOLT_LIB` prevents writes/builds in target; `JOLT_CACHE_DIR` is in scratch.
- `logs/runtime.txt` records compiler/platform/runtime. Local/root dependencies are in `deps.edn`.

Read target README, src/yyjson/core.clj, src/yyjson/native.clj, native/shim.c, test/yyjson/core_test.clj and deps.edn; applied Hegel skill, external-pilots reference, API generator/run/replay sections and docs/REPLAY_BUNDLES.md. No prior packs/results or sibling work consulted. Both checkouts remained read-only.

## Gap and justified domains

Existing numeric tests list a few long/double boundaries, unsigned maximum, two decimals, one huge integer, ratio 1/2 and two nonfinite errors. They lack broad numeric range generation and combinations with container paths/options.

The source writes integers and BigDecimals through `yj-raw` using `(str v)` and ratios as `(double v)`. Parsing uses `YYJSON_READ_NOFLAG`, then dispatches signed, uint64 and double native kinds separately. The following independent properties are in study.clj:

| Property/domain and oracle | Valid cases | Result |
|---|---:|---|
| Integer parse in [-2^63,2^64-1]; decimal text parses to original mathematical integer, no double coercion | 1,000 | pass |
| Integers up to ±10^40; scalar and array writing preserves exact decimal text | 500 | pass |
| Finite binary64, excluding nonfinites; writer/parser roundtrip across five contexts and pretty option | 2,000 | pass |
| BigDecimal int64 coefficients and exponents [-400,400]; raw text preserved, also via map value-fn | 500 | pass |
| Ratios numerator ±10^9 and denominator 1..10^6; compare to documented double conversion, across five contexts | 500 | pass |

Five contexts: scalar, array, object, NUL-key object and nested array. Explicit witnesses add 12 integer boundaries, six zero/subnormal/max-finite doubles, and NaN/±Infinity rejection in all five contexts. These prove presence, not distribution. The floating oracle concerns binary64 serializer roundtrip, not universal decimal-real exactness; it does not distinguish signed zeros. Huge integer/decimal write checks do not assume exact arbitrary-precision readback. No unsupported read options were assumed.

All five runs explicitly checked `:passed?`; all reported zero invalid cases, zero failures and no flakiness. `logs/initial-study.log` preserves original output; summaries.edn holds the returned counters.

## Negative control and replay

The control explicitly inserts a lossy double conversion after target integer reading. It minimizes to `9007199254740993`, converted by the control to `9007199254740992`. The target itself preserves that integer, as target-only.clj verifies. This is solely a harness sensitivity check.

Control counters: 36 test-cases, 8 valid, 10 interesting, one failure, no flakiness. These adaptive engine counters are not 36 independent generation samples. control-minimum.edn is selected retained exception data, not an arbitrary final callback. control-bundle.edn is a codec-validated native bundle; fresh-process replay reports `:reproduced`. Wrong generator provenance is rejected before its throwing property runs. Expected provenance is independently declared in study.clj, not copied from a consumed bundle. Original artifacts are archived in original-artifacts/.

No target failure replay bundle exists because no target failure was established.

## Documentation boundary observation

README describes reading “longs (or larger integers beyond 64 bits)”. This is ambiguous: `-9223372036854775809` and `18446744073709551617` become Doubles and lose the last unit; `18446744073709551616` also becomes a Double, while `18446744073709551615` stays exact. `1e309` is rejected as invalid, code 9. The source docstring specifically mentions uint64 results. Therefore this is a documentation/domain follow-up, not an established arbitrary-precision reading bug. Target-only input/output is retained in target-only.clj and logs/target-only.log.

Useful follow-up: document the exact integer-read interval, out-of-range floating conversion and overflow rejection, and add boundary examples. Exact arbitrary-precision reading would need a deliberate API/native-option design. No fix was made.

## Verification and limitations

Run `./verify.sh`: it checks exact SHAs, builds in scratch, runs properties/witnesses/control, and replays the bundle in a separate process. Commands are bounded to 120 seconds. Harness errors, unexpected target failure, unexpected control pass, flakiness and failed replay exit nonzero. The original full run exited 0. target-only.clj requires no Hegel namespace and its original run exited 0.

Attempting the complete existing target suite using copied test dependencies in upstream-suite/deps.edn exited 1 before tests: Maven/Clojars network access failed for cljc.java-time and time-literals. This is a setup limitation, not a target failure. Original transcript: logs/upstream-tests.log. Full upstream suite therefore remains unverified; numeric study used only available dependencies.

Self-reported DX: provisioned setup does not measure cold install. Hegel docs supplied correct retained-input/replay APIs and enough information to independently declare provenance. Cosmetic namespace-shadow warnings occurred. No core API change is justified. One report-writing shell quoting mistake occurred after all study runs; it affected no experiment and was corrected by writing through apply_patch.

Evidence ledger: exact-writing versus exact-reading question → source inspection and target-only boundary probes → ambiguous README range → clarify docs and add examples. No follow-up fix verified. Another ledger item: full baseline suite → logs/upstream-tests.log → unavailable dependencies/network → preprovision exact target test dependencies for future offline studies.
