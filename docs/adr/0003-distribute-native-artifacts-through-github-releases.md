# ADR 0003: Distribute native artifacts through GitHub Releases

- Status: Amended by ADR 0006
- Date: 2026-07-22

## Context

jolt-hegel is consumed as a SHA-pinned Git dependency, and dependency aliases or
tasks are not inherited by the root project. libhegel publishes target-specific
shared libraries. Generated binaries and local research checkouts do not belong
in Git.

## Decision

Expose native setup as the public `hegel.install` namespace on every host, with
the dependency alias active where required. Download libhegel from its upstream
release using source-pinned hashes and verify it through the same
consumer-visible installer.

Use a private development repository as a proving ground. Publish source tags
only from `chucklehead-dev/jolt-hegel`, guarded by repository identity in the
release workflow.

## Consequences

- The repository contains source and checksum pins, not generated libraries.
- A release requires Linux x86_64, Windows x86_64, and macOS arm64 validation.
- jolt-hegel publishes no native artifact and requires no local C compiler.
- libhegel remains an upstream release artifact under its own license.
