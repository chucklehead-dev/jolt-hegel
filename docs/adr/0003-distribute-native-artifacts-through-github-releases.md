# ADR 0003: Distribute native artifacts through GitHub Releases

- Status: Accepted
- Date: 2026-07-22

## Context

jolt-hegel is consumed as a SHA-pinned Git dependency, and dependency aliases or
tasks are not inherited by the root project. libhegel publishes target-specific
shared libraries, while jolt-hegel must supply its own target-specific temporal
shim. Generated binaries and local research checkouts do not belong in Git.

## Decision

Expose native setup as `joltc -A:<dependency-alias> -m hegel.install`, with the
alias omitted for a top-level dependency. Download libhegel from its upstream
release using source-pinned hashes. Build jolt-hegel shims in the public tag
workflow, publish each with a SHA-256 sidecar, and verify the released assets by
downloading them through the same consumer-visible installer.

Use a private development repository as a proving ground. Publish source and
release artifacts only from `chucklehead-dev/jolt-hegel`, guarded by the
repository identity in the release workflow.

## Consequences

- The repository contains source and checksum pins, not generated libraries.
- A release requires Linux x86_64, Windows x86_64, and macOS arm64 builders.
- Unsupported shim targets can use a local C compiler fallback.
- The tagged EPL-2.0 C source remains available for every released shim binary.
