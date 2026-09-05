# libhegel 0.36 migration

The pinned C release is 0.36.3 at
`caafb40bbc37b5c44c7843f2442b62e382d73894`. Rust frontend versions advance
separately. This upgrade includes native recursive-size, state-machine round,
and whole-step/region shrinking fixes; their actual contracts still require
native regression tests rather than merely new version strings.

## Removed mode

The C ABI no longer has a mode enum or setter. Supplying `:mode`, including
`:test-run` or `:single-test-case`, is now a usage error before a run allocates
native state. Omit it for ordinary runs. `:test-cases 1` limits valid generated
cases; it does not promise one property invocation or disable shrinking and
replay. Native one-case runs now skip the simplest-example probe and its
large-initial-case health check. State-machine rounds remain bounded.

## Native nanoseconds, public microseconds

The canonical ABI and low-level `hegel.ffi` time maps use `:nanosecond` in
0..999999999. Direct low-level callers must migrate field names and values;
equal uint32 storage width is not equal temporal meaning.

Public `g/time` and `g/datetime` keep their existing microsecond bounds and
optional six-digit fractional strings. Each inclusive bound interval is
converted to its full native preimage: lower microsecond*1000, upper
microsecond*1000+999. Results use integer quotient by 1000. Fixed public bounds
stay fixed even when the native result contains sub-microsecond precision.
This intentionally discards precision; it never rejects a valid native draw
or introduces an extra random draw. Date, hour, minute and second fields are
unchanged. Datetime endpoint-day restrictions remain native-owned; public
date years remain 1..9999, despite upstream's wider native range.

No identical old/new distribution, seed stream, shrink path or reproduction
blob is promised. Record the engine version with artifacts, and regenerate
version-sensitive corpora deliberately. Within a pinned version the engine
continues to own recorded choices and replay; callers do not need to retain
raw nanosecond maps to reproduce a failure.

Every-step invariant checking remains unchanged. Native printer ownership and
observations are separately tracked in issues #53/#54; descriptor coverage does
not mean a new high-level API is available or that an experimental host has
been promoted.

## Stateful shrinking integration

The frontend records each state-machine round as a native stateful-rule span,
starting before the continuation/group choice and ending after the rule stream.
This supplies the structure needed by native whole-step deletion; upgrading the
shared library alone does not create those frontend spans. Rejected rounds are
marked discarded. Initial and every-successful-rule invariant checks retain
their existing timing; this does not enable native invariant sampling.

Span structure can change shrinking decisions and the resulting minimal blobs.
Keep the property/model revision as well as the engine version when retaining
reproduction artifacts. No globally minimal trace is promised for every model.
