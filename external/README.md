# External Hegel pilot packs

These isolated consumers exercise Hegel against real libraries using their
public APIs. The first six properties cover Instaparse and yyjson-jolt at the
exact commits in [targets.edn](targets.edn). Normal Hegel consumers do not resolve
these dependencies. The current pilot uses official Jolt 0.8.30 and libhegel
0.53.2; Linux amd64 and macOS arm64 have dedicated CI jobs. yyjson builds its
vendored C library with `cc`; Windows is outside this pilot's target contract.

From the repository root, with Jolt on PATH and Python 3 installed:

```sh
jolt -m hegel.install setup
python3 script/check-external-pilots.py
# Or: python3 script/check-external-pilots.py --pack yyjson --output /tmp/hegel-pilot
```

Each pack is a separate dependency root under `packs/`. The Hegel dependency is
the current local checkout. The runner records its exact HEAD and refuses dirty
`src`, `resources` or root `deps.edn` files. Target pins are checked against the
manifest. A SHA-256 over both packs' source/dependency files, shared runner and
manifest identifies the generator/model revision; it does not depend on notes
or study prompts. A changed HEAD/revision requires a new artifact, rather than
relabeling an old replay blob.

## Contracts and independent oracles

| Property | Domain/oracle | Broken control |
| --- | --- | --- |
| Instaparse acceptance | Native grammar strings; separately handwritten parser grammar; recursive/nonrecursive, compact/spaced forms | A parser that excludes zero and compound expressions |
| Instaparse optimization | Fixed `S = A*; A = 'ab'` grammar; normal versus memory-optimized hiccup and enlive trees; confirm optimization is actually used for nonempty input | Lose the optimized tree's child structure |
| Instaparse meaning | Bounded integer/addition AST; independent evaluator versus fully parenthesized print/parse/transform | Transform addition into subtraction |
| JSON roundtrip | Recursive vectors/maps with string keys, nil, booleans, integers -1000..1000 and scalar-valid Unicode strings | Decode top-level null as false |
| JSON Unicode | Independent minimal/raw and fully escaped encoders, including surrogate pairs; compare both decodings with original input | Replace supplementary characters with U+FFFD |
| JSON error offset | Raw NUL after a safe ASCII/multibyte prefix; independent UTF-8 byte count, plus successful subsequent parse | Use character count as a byte offset |

The JSON identity contract deliberately excludes floats, BigDecimal, ratios,
sets and keyword keys. Their normalization and numeric semantics need separate
properties. The encoders do not use yyjson's writer or data.json as their oracle.
No target library was modified. Broken controls are explicit pack-local
mutations, not claims that the pinned targets have those defects.

## Evidence and bounds

Each positive run requests 200 valid cases, seed 424242, no database, and
`:phases [:generate]`. Named coverage requirements ensure the important input
categories occurred at least once; reports retain actual hit counts. This is
presence evidence, not exhaustive coverage or a distribution-quality guarantee.
Control runs enable normal shrinking/confirmation and must fail at the intended
assertion without flakiness. An unexpected crash does not satisfy a control.

The script then launches a **new process** for native-blob replay, rejects a
deliberately mismatched generator revision without executing the body, and
checks the minimized ordinary value against both correct and broken behavior.
Every process has a configurable wall-clock timeout (240 seconds by default).

`target/external-pilot/` holds:

- `execution.json`: process exit statuses and wall time including startup/build.
- Per-pack run/replay EDN reports and logs.
- Each minimized `.value.edn` fixture and version-specific `.replay.edn` bundle.

Execution timings are not application benchmarks. Replay provenance is an
assertion about this controlled checkout; the script does not authenticate
untrusted artifacts. The fixed per-process limit also bounds replay execution.
CI uploads the evidence even when a property or setup step fails.

## Learning agenda

The [study protocol](study/README.md) separates automated conformance evidence
from agent authoring probes and questions requiring human observation. Initial
recommendations and observations live in the study notes. A pack-local helper is
preferred until repeated or correctness-critical needs justify changing Hegel.
