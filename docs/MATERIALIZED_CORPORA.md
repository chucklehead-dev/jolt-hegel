# Seeded materialized corpora

Status: unreleased implementation for issue #56. Cross-platform CI and the
coordinated live/offline db pilot remain acceptance gates; the existence of these
APIs does not close that issue. Local Linux BB/JVM/Jolt corpus checks have passed
in new network namespaces with an unavailable libhegel path; this validates the
Hegel consumer path, not the still-pending db integration.

The purpose is to generate successful plain-data examples once and check those
same examples in environments without libhegel or network access. This is distinct
from replay bundles: a corpus contains values, not native reproduction blobs or
failed-property exceptions. Both modes must use the same consumer/model contract.

## Generation contract

The `hegel.materialize/materialize!` entry point accepts an options map,
a Hegel generator, and optionally a `check!` function of one generated value.
The check's return value is ignored, like a normal Hegel property; it must throw
to report failure. Generator/check assumptions reject a candidate before it is
collected. Model inconclusiveness, usage, setup, health-check, and native errors
retain their existing abort classifications.

Required options are a canonical unsigned-64 decimal string `:seed`, integer
`:count` in 1..256, and `:provenance`. Unknown options are rejected before native
work. The policy is `:exact-valid-count`; there is no implicit truncation,
deduplication, partial success, or invented native attempt-budget setting.

Each output position i uses a separate sequential native run with numeric seed
`(seed + i) mod 2^64`, one valid test case, backend `:default`, generation phase
only, quiet output, multiple-failure reporting disabled, and database `""`.
Native assumptions may require multiple attempts within that one run. Native
health checks remain enabled. This is not a wall-clock timeout or a promise of
statistical independence between adjacent seeds. Contexts are never concurrent.

This per-position policy deliberately permits a constant generator to produce
the requested number of equal values; it does not pad an exhausted run with
copies or deduplicate genuine equal draws. Its context/setup cost must be
measured and documented. The precise seed derivation and settings are part of
the versioned policy, not an implementation detail that can change silently.

Collect only after both the draw and optional check finish. Accept a position
only when its run reports passed, nonflaky, zero failures and exactly one valid
case, with exactly one collected value. Any contradiction or shortfall fails
materialization. Only after every position succeeds and the complete payload
passes its bounds may the API return a corpus. Never expose a partial corpus in
a successful return, or write files during generation. Local failure diagnostics
may retain a failed run; those diagnostics are not corpus content.

Repeatability requires deterministic generators/checks and the exact recorded
engine, frontend, runtime and source contracts. A seed is not an identity for
arbitrary side effects, and a passing run cannot prove absence of nondeterminism.
Cross-host regeneration is not promised to reproduce bytes. Cross-host
consumption of an existing payload is required.

## Envelope and exact-byte digest

The schema-v1 envelope is a closed map:

```clojure
{:format :hegel/materialized-corpus
 :schema-version 1
 :sha256 "<64 lowercase hexadecimal characters>"
 :payload "<restricted EDN text containing the payload map>"}
```

The payload is a closed map containing:

```clojure
{:provenance {:hegel-sha "<full peeled commit SHA>"
              :libhegel-version "<exact native version>"
              :runtime {:host :jolt :version "..." :os "..." :arch "..."}
              :property-id "..."
              :generator-revision "..."
              :model-revision "..."
              :seam-revision "..."}
 :seed "18446744073709551615"
 :count 2
 :valid-case-policy :exact-valid-count
 :values [<first value> <second value>]}
```

`:model-revision` is explicitly nil when no model is involved; the key is still
required. All other revision/identity fields are nonblank strings, and the
Hegel SHA is exactly 40 hex characters. Runtime provenance describes the producer,
not the offline consumer. The native generation adapter checks its actual host
and selected native version; other identities are independently supplied build
assertions, not inferred from a function object or copied from an input corpus.

SHA-256 covers the exact UTF-8 bytes of the stored payload string, including its
manifest/provenance fields. It does not hash the envelope containing the digest.
Readers must not parse and reprint before hashing. Therefore map iteration order,
whitespace, and host numeric printer choices cannot break verification of a
transferred artifact. Reformatting payload text intentionally changes its digest;
reformatting the outer envelope does not. No Unicode normalization is performed;
unpaired surrogate code units must be rejected to avoid host-specific UTF-8
replacement behavior.

Verification requires an independently pinned expected digest and expected
provenance, not merely agreement with the digest claimed inside the artifact.
Changing payload and its self-declared hash together must still fail against the
consumer pin. This is integrity relative to a trusted manifest, not a signature
or authentication scheme. Values may contain secrets; no implicit redaction or
safe-to-publish claim is made.

The consumer call is `(hegel.corpus/consume! expected envelope)`, where
`expected` is a closed map `{:sha256 digest :provenance provenance :count n
:valid-case-policy :exact-valid-count}` supplied by the consuming test's checked-in
manifest. `consume!` returns the validated payload map, including `:values`, only
after every comparison succeeds. The pin is trusted caller configuration; it
must never be derived by selecting fields from the artifact being checked.
Regeneration produces a candidate artifact and a separately reviewed manifest
update; consumption never rewrites either file or silently accepts a new pin.

Version 1 readers accept only version 1. Future formats require a new explicit
version branch plus cross-version controls and migration documentation; there is
no heuristic fallback. Updating a fixture's version, policy or provenance must
update the independent consumer pin in the same reviewed integration slice.

## Bounded offline consumption

The `hegel.corpus` schema/consumer namespace does not require
`hegel.core`, `hegel.ffi`, an installer, Malli, or a type checker. Its digest
adapter must not load libhegel or access the network. Dependency provisioning is
a separate explicit setup step; it must be complete before the offline gate.

Processing order is: bound and parse the outer envelope; validate closed schema
and digest syntax; hash the exact payload and compare both declared and pinned
digests; bound and parse the payload; validate provenance and complete count;
then release the values to the model. No model callback runs before verification.
Wrong versions, stale provenance, unknown keys, count mismatch and bad digests
fail closed. Parsing does not execute tagged literals or reproduction blobs.

Payload limits: 262144 UTF-16 code units of serialized text, depth 32
with root depth zero, 65536 data nodes (including map keys), 8192 UTF-16 units
per scalar string/keyword, and 256 values. The outer escaped envelope has a
separate 2097152-unit text bound and permits a payload string up to 262144 units;
these outer allowances must not relax payload scalar limits. Limits apply before
recursive reading and before accumulating unbounded generation output.

Values use the portable replay-data domain: concrete maps/vectors, nil,
booleans, strings, keywords, integers in [-2^63, 2^64-1], and finite floating
values. Lists, lazy sequences, sets, records, arrays, host handles, exceptions,
NUL strings, nonfinite numbers and unreadable keyword spellings are rejected.
The shared transport performs a round-trip check; this is not canonical EDN.

### Usage and digest dependencies

Generate with `(materialize/materialize! opts generator check!)`; omitting
`check!` performs generation without a model assertion. Return false is not a
failure. `corpus/seal` validates and hashes an already complete payload;
`corpus/encode` and `corpus/decode` handle the outer EDN transport. Decoding the
envelope is not verification: call `corpus/consume!` with an independent pin
before running the consumer model.

BB/JVM hashing uses the platform SHA-256 provider. Jolt uses a small optional
FFI adapter: system OpenSSL on Linux/macOS, and Windows CNG `bcrypt.dll` from
`SystemRoot\\System32` on Windows 10 / Server 2016 or later. macOS users must
provision OpenSSL (the CI uses Homebrew `openssl@3`) before offline operation.
No subprocess, installer, network request, or libhegel load is part of hashing.
Native output is copied into an owned array before temporary allocations are
freed; CNG handles are closed per call. Static crypto linkage is not claimed.
The ClojureCLR digest branch is experimental and not yet feature-validated;
jank corpus hashing currently throws an explicit unsupported-host error.

Generation seals each proposed prefix before retaining it, bounding both data
and its escaped representation before the next native run. This first
implementation repeats validation/encoding/hashing across prefixes, in addition
to creating one native context per position. Both costs must be measured before
claiming suitability for large-volume generation; the count ceiling is 256.

Focused engine-free checks (after dependency/toolchain provisioning):

```sh
bb corpus-test
clojure -M:corpus-test
jolt -M:corpus-test
```

The normal semantic suite includes corpus and actual materialization scenarios.
Engine-free tests run a fixed UTF-8 artifact against an independently calculated
digest, plus corrupt/stale/reader/bounds controls. These commands alone do not
prove network isolation; the physically offline fixture gate is separate.

## Cross-repository db pilot

Aspect-packs roadmap #69 requires `docs/spec-corpus.md`, both live generation and
baked fixtures, and a db pilot. That document was absent from public main when
checked on 2026-09-05. The prerequisite contract has since landed in
[aspect-packs PR #91](https://github.com/chucklehead-dev/jolt-aspect-packs/pull/91),
commit `96be2d7f9070dede16d1291be218d7e26f3c0b26`, after root/Claude review and its
applicable CI. It explicitly leaves both consumers and their new gates pending;
this Hegel document is not a substitute for those implementations or coordinated
pin updates.

For the db pilot, each generated value is the complete privacy-shaped event
vector accepted by `jolt.aspect-packs.db.model/check!`. Both modes call that same
pure model outside advice. Do not serialize database handles, SQL, parameters,
rows, spans, metrics, or arbitrary exception messages. The model's own event
bound and event-contract/seam revision remain separate from corpus transport
bounds. Corpus validity alone is not model validity or a non-vacuity proof.

## Remaining implementation and acceptance gates

- Extract and regression-test shared bounded data/EDN mechanics without changing
  replay-bundle v1 acceptance, errors, limits or native behavior.
- Verify the digest adapter on every supported host/OS, including UTF-8
  supplementary characters, standard SHA-256 vectors and malformed surrogates.
  Released upstream `jolt-lang/crypto` v0.0.5 supplies MessageDigest on Jolt 0.8,
  but its metadata only declares Linux/macOS native candidates. The new Jolt
  adapter implements Windows CNG; actual Windows execution is still a gate,
  not permission to narrow supported hosts or infer success from Linux.
- Test exact count, constant values, uint64 seed wrap, repeated seeded generation,
  rejected candidates, ordinary failure, flakiness, abort propagation and cleanup.
  Verify different seeds actually exercise a nonconstant domain; do not count
  deterministic constant output as evidence of seed use.
- Test bounded encoding/reading and sanitized errors; stale model/seam/native
  provenance; digest mutation; payload-plus-self-hash mutation against a pin;
  count mutation; and known positive controls on BB/JVM/Jolt.
- Consume a transferred fixture with libhegel unavailable and network disabled;
  exercise the actual digest and model path, not only namespace loading.
- Land and independently review the aspect-packs contract and both db consumers;
  retain mandatory witness families/known-bad semantic controls.
- Update public API docs, repository and installed Hegel guidance only after
  verified implementation. Keep experimental jank/CLR scope explicit.

Selective typing (#73) may later cover manifest and validated-data relationships.
It does not replace runtime digest/bounds checks or block this implementation.
