# ADR 0001: Bind libhegel directly

- Status: Accepted
- Date: 2026-07-22

## Context

Hegel's older `hegel-core` daemon protocol is deprecated. `hegel-cpp` and
`hegel-clj` provide useful implementation and API references, but each is tied
to its host language. The supported engine is libhegel, which exposes a pull-model
C ABI and does not require callbacks for ordinary property runs.

## Decision

Bind libhegel's C ABI directly. Implement the run loop, resource ownership,
generator surface, and `clojure.test` integration in this repository. Host FFI
details are amended by ADR 0006. Do not introduce a daemon, subprocess
protocol, or C++ wrapper.

## Consequences

- Every supported host calls the real engine with no serialization or sidecar
  process.
- jolt-hegel owns a carefully checked native lifecycle and ABI version pin.
- Host-level combinators must preserve libhegel span and collection structure.
- Changes to the libhegel ABI require an explicit pin update and matrix test.
