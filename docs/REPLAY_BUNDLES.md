# Portable replay bundles

Replay bundles retain native failure blobs together with a versioned property
and deployment contract. They are not seed-only reruns. This API is additive;
ordinary `run-test!` calls continue to work and now include `:replay-options`
in their result, capturing present replay-relevant settings without inventing
defaults. Transport bounds apply when exporting, not to ordinary runs.

## Export and replay

```clojure
(require '[hegel.core :as h]
         '[hegel.replay-bundle :as bundle]
         '[hegel.replay-bundle.codec :as codec])

(defn encode-failure [provenance result]
  (codec/encode (bundle/from-result provenance result)))

(defn replay-from-text [expected-provenance text property]
  (h/replay-bundle! expected-provenance (codec/decode text) property))
```

Supply provenance from an independently recorded deployment/property manifest:

- `:hegel-sha`: exact lowercase 40-character source commit SHA;
- `:libhegel-version`: native version, currently `"0.36.3"`;
- `:runtime`: a map requiring `:host` (`:jolt`, `:bb`, `:jvm`, `:jank`, or
  `:clr`), and nonblank `:version`, `:os`, and `:arch` strings;
- `:property-id` and `:generator-revision`: stable nonblank identifiers;
- `:model-revision`: required, either a stable nonblank identifier or nil for
  a property without a model.

These fields are assertions by the application, not authenticated identities.
Do not copy the input bundle's provenance as the expected manifest: that would
bypass the compatibility check. The library cannot infer the identity of a
generator or property from its function object. Include the actual runtime
version/build identifier in your manifest, not merely its Clojure compatibility
version. Update revisions whenever the property's choice or model contract
changes. The adapter additionally checks the selected host and pinned native
version, then verifies the loaded native library before replay allocation.

All provenance fields compare exactly, including runtime version, OS and
architecture. A portable artifact can be decoded on another supported host;
that is not a claim that executing it under changed provenance is compatible.
Malformed schema throws a marked usage error. A well-formed mismatch returns
`{:status :incompatible :reproduced? false :mismatches [...]}` with stable paths
and expected/actual values. `:expected` always names the independent manifest
value; `:actual` comes from the indicated `:source` (`:bundle`, `:runtime`, or
`:native-binding`). Mismatches return without executing the property or allocating a
replay context. Loading `hegel.core` still initializes the native backend;
use the pure bundle/codec namespaces for offline validation.

Compatible replay uses each native blob directly with the captured settings
and string seed, with persistence disabled. It never starts a generation run.
The property executes in final-replay mode, so recreate per-case state exactly
as for `run-test!`. Results are `:reproduced` or `:not-reproduced`, with
`:reproduced?`, `:flaky?` and per-failure `:failures`/`:final` evidence. Passing,
rejecting, overrunning, or failing at a different origin does not reproduce
the expected failure. Inconclusive, usage and native errors propagate; owned
resources are cleaned up. Reproducing a known failure does not mean the
property passed, so this API does not return a misleading `:passed? true`.

Export requires a failed, nonflaky `run-test!` result whose failures all
reproduced and which contains `:replay-options`. Older results without captured
settings are rejected; do not fabricate defaults to make them exportable.
The export contains seed, options, provenance, and origin/blob pairs. It
omits exceptions, return values, exploration observations, database paths,
database keys and display names. The uint64 seed remains a decimal string;
direct replay handles values above signed `Long/MAX_VALUE` without `parse-long`.

## Trace, redaction and bounds

Trace is absent unless explicitly supplied:

```clojure
(bundle/from-result provenance result
  {:trace {:contract-id "application/journal"
           :contract-revision "v1"
           :events [{:phase :return :operation-id 1}]}
   :redact-trace redact-trace})
```

The optional redactor runs before trace validation; its failure propagates and
never falls back to raw data. Events must be portable maps. This envelope does
not perform trace/history semantic validation or settle experimental compiler
join-point metadata (#23). Use `hegel.event-contract/check-envelope!` for an
explicit `hegel.operation-events` revision 1 claim, then apply the domain model;
generic producer contracts continue to use their appropriate trace rules.

Schema version 1 rejects unknown envelope fields. Portable values are maps,
vectors, nil, booleans, strings, keywords, signed-int64 through uint64 integers,
and finite floating values. Lists, sets, records, symbols, character literals,
ratios, decimal objects, nonfinite floats and host resources are rejected.
The codec additionally requires readable, round-trippable representations;
for example, a dynamically constructed keyword containing spaces is invalid.
Metadata is not exported. NUL strings are forbidden.

| Limit | Maximum |
| --- | --- |
| Encoded text UTF-16 code units | 262144 |
| Data depth (root is zero) | 32 |
| Data nodes, including map keys | 8192 |
| Individual string/keyword spelling | 8192 UTF-16 code units |
| Failures | 16 |
| Trace events | 256 |

Text limits use UTF-16 code units on every host, including codepoint-indexed
Jolt: a supplementary character such as an emoji consumes two units. This is
a transport measurement, not a change to host string indexing.

The data validator bounds traversal and aggregate string content; the codec
also bounds escaped output. The decoder checks text length, nesting and token
count before calling the EDN reader. It rejects reader dispatch (including
tags, discards and reader-eval) and requires exactly one form. Reader errors
do not retain raw input messages or causes. No native library or type checker
is needed to use `hegel.replay-bundle` or its codec. Bound file/network reads
at the transport boundary too: a bounded decoder cannot undo an earlier
unbounded `slurp` or HTTP body allocation.

## Trust boundary

**Execute only trusted artifacts.** Bounded EDN and matching provenance do not
authenticate an artifact, constrain arbitrary property code, or bound native
blob decompression. The pinned native decoder uses
`decompress_to_vec_zlib` without an output cap for compressed blobs
([exact source](https://github.com/hegeldev/hegel-rust/blob/caafb40bbc37b5c44c7843f2442b62e382d73894/hegel-c/src/native/blob.rs)).
This API is not a sandbox for attacker-supplied counterexamples.

Blobs encode generated choices and may disclose secrets even when all trace
data is omitted. Redacting a blob would change the reproducer. Do not publish
a bundle unless its blob, origin and provenance are safe to disclose. Opt-in
trace redaction is a convenience, not a guarantee of secret removal.

Bundles are failure artifacts, not the materialized successful-value corpus
API proposed in #56. That API still needs its own digest, completeness and
offline-consumption contract. Types can help check these data shapes (#73),
but neither annotations nor structural validation replaces the checks above.
