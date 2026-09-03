# Changelog

Notable changes to jolt-hegel are recorded here. Release links compare the
exact public tags in `chucklehead-dev/jolt-hegel`. This file begins at v0.5.0;
for v0.1.0 through v0.4.0, see the tag and release history in that repository.

## Unreleased

- Add bounded direct self-recursion to the optional Malli adapter, including
  engine-owned depth, leaf-budget, retry, and subtree-hoisting shrink behavior.
- Publish experimental compiler-derived Jolt aspect join points on
  `hegel.core/run-test!` and `hegel.stateful/run!` for property and
  state-machine runs.

## [0.5.0] - 2026-08-31

- Replace the development Babashka fork and private JVM adapter with the
  upstream `babashka.ffi` implementation shared by Babashka and JVM Clojure.
- Preserve the canonical libhegel ABI descriptor and common property API
  across Jolt, Babashka, and JVM Clojure.
- Require FFI-capable Babashka 1.13.220 or later and JDK 22 or later for the
  supported JVM host.

[0.5.0]: https://github.com/chucklehead-dev/jolt-hegel/compare/v0.4.0...v0.5.0
