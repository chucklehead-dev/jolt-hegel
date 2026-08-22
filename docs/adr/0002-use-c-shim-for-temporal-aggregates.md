# ADR 0002: Use a C shim for temporal aggregates

- Status: Superseded by ADR 0005
- Date: 2026-07-22

This ADR is retained as history. It does not describe the current installation
or release process.

## Context

The date, time, and datetime functions pass C structs by value. Jolt 0.4.15 can
bind scalar and pointer arguments but cannot portably describe by-value structs.
Packing structs into integers worked in a Linux probe but depends on register
classification, padding, endianness, and target ABI. A general Jolt FFI extension
would be valuable but is outside this project's control.

## Decision

Ship a small C adapter whose public functions accept pointers to temporal
bounds. Target-native C dereferences those values and invokes libhegel with the
real by-value signatures. Assert structure sizes, alignment, and offsets at
compile time. Resolve the temporal symbols from the exact libhegel path selected
by the Jolt runtime.

UUID and IP generation remain direct FFI calls because their C functions write
to caller-owned buffers and do not pass aggregates by value.

## Consequences

- jolt-hegel does not depend on upstream Jolt FFI changes.
- CI must build and execute one shim per supported target.
- Consumers can download a prebuilt shim or compile the tagged source locally.
- The shim remains intentionally narrow; it is not a second general binding
  layer.
