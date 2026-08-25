# ADR 0005: Bind temporal aggregates directly

- Status: Accepted
- Date: 2026-08-22

## Context

libhegel's date, time, and datetime generators pass nested C structs by value.
Every supported host FFI can describe native aggregate layouts and by-value
arguments directly.

## Decision

Bind `hegel_generate_date`, `hegel_generate_time`, and
`hegel_generate_datetime` directly from canonical type and function data. Use
the same backend-derived layouts for allocation and field access. Jolt requires
0.7.23 or later; other minimums are recorded by ADR 0006 and CI.

## Consequences

- Each host backend derives temporal sizes, alignments, offsets, and argument
  classification for its supported ABI.
- The minimum supported Jolt version is 0.7.23.
- Linux x86_64, Windows x86_64, and macOS arm64 remain required test targets.
