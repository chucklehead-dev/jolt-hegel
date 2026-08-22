# ADR 0005: Bind temporal aggregates directly

- Status: Accepted
- Date: 2026-08-22

## Context

Jolt now supports declarative C struct layouts, structs passed by value, and
lexically scoped native allocations. The limitation which required
`native/hegel_shim.c` in ADR 0002 no longer applies.

## Decision

Require Jolt 0.7.23 or later.
Bind `hegel_generate_date`, `hegel_generate_time`, and
`hegel_generate_datetime` directly with literal `[:by-value [:struct ...]]`
descriptors. Use the same compiled layouts for allocation and field access.

Remove the shim source, loader, compiler fallback, environment variables,
workflow jobs, and release artifacts. Continue to download and verify libhegel
itself through `hegel.install`.

## Consequences

- Chez derives temporal sizes, alignments, offsets, and argument classification
  for each supported ABI.
- jolt-hegel no longer publishes or compiles native code.
- The minimum supported Jolt version is 0.7.23.
- Linux x86_64, Windows x86_64, and macOS arm64 remain required test targets.
