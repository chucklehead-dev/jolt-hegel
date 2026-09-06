# Experimental jank host

jank support is an active portability experiment, separate from the supported
Jolt, Babashka, and JVM release matrix. Linux x86_64 and macOS arm64 CI load
libhegel directly through jank's C++ interop and preserve the shared Hegel API;
this does not introduce another property-testing implementation.

The implementation is merged on `main`, and both hosted cells run on every
relevant pull request and `main` push. They validate generated-artifact drift,
the direct ABI backend, and focused shared property/stateful semantics. Windows
remains outside the matrix because there is no comparably consumable pinned
jank toolchain for that runner yet.

The initial local gate was exercised with `jank-0.1-alpha`, binary identity:

```text
x86_64-unknown-linux-gnu-a836dc6b7a6df7c434796123b874a4a0e89caa94399ea7c98b2116699e30088f
```

Run `jank check-health` first. Both JIT compilation and AOT compilation must be
healthy, and the C++ compiler bundled with jank must be available.

Hosted Linux CI downloads the current noble package directly and verifies its
pinned SHA-256 before installation. Hosted macOS CI uses the official
`jank-lang/setup-jank` action at an immutable action commit on macOS 26, the
minimum OS targeted by its current binary. Both jobs record the installed binary
identity as runtime provenance. The macOS action installs jank's rolling main
build rather than a versioned artifact, so that experimental cell is a live
compatibility canary, not a reproducible compiler pin. This matters because the
installer currently consumes jank's bundled, non-public SHA-256 helper; hosted
compilation detects upstream API drift. After installation, both cells also
verify the resulting libhegel bytes with an independent host digest command.

## What works

The new `g/big-integer` and `g/float32` domains are qualified on the supported
BB/JVM/Jolt matrix only. This experimental host's symbol/ABI and focused semantic
smokes do not yet qualify arbitrary-precision values, exact binary32 bounds,
or their complete shrinking/replay contracts. Do not assume parity from loading
the shared namespace successfully.

- all 103 libhegel 0.36.3 symbols are generated from the canonical ABI descriptor
  and resolved from the selected library;
- fixed-width integer, floating-point, pointer, UTF-8, out-parameter, and bulk
  byte operations;
- `date`, `time`, and nested `datetime` structs passed by value;
- the shared property runner, deterministic seeds, shrinking, final replay,
  and failure reproduction;
- shared temporal generators;
- shared stateful/swarm execution and reusable or consumed pools;
- qualified assumption-rejection accounting (`h/assume!`) and framework-less
  counting/structured reporting via `hegel.report`, including run and failure
  counts and event typing across a pass, a property failure, and a setup error;
  and
- a focused `clojure.test` integration smoke: `hegel.clojure-test/with` hosts
  a passing property with an independent execution-count witness, a
  deliberately failing shrinking/replaying property, and a bounded
  plain-exception control that exercises the jank fallback in
  `throwable-details` (no `Throwable->map` or `class`), all inside real
  `deftest` bodies; the pass and property-failure controls also exercise `is`.
  `clojure.test/run-test` is the public call surface
  exercised here -- it always calls `test-var` internally, so this is not a
  claim that `test-var` itself goes unexercised, only that the smoke never
  calls it directly -- and it also exercises the dynamic `report` rebinding
  `hegel.clojure-test/with` relies on internally, with exact
  pass/fail/error summary and event-type accounting and a bounded, stable
  shrunk counterexample. The hosted Linux and macOS jank jobs confirm this
  slice; it qualifies only that narrow slice, not the complete
  `hegel.clojure-test` suite, and it does not run on Windows.

The EDN descriptor remains the source of truth. Because the current jank reader
cannot load that resource during compilation, the development generator emits:

- `src/hegel/abi_data.jank`, the canonical descriptor as jank data;
- `src/hegel/ffi/jank_generated.jank`, function metadata and direct invokers;
  and
- `generated/hegel/jank/libhegel_abi.hpp`, layouts, symbol resolution, memory
  operations, and typed downcalls.

Never edit those files directly. Regenerate and verify them with:

```bash
bb jank-codegen
bb jank-codegen-check
```

## Running the spike

Install libhegel 0.36.3 with jank's native installer, or point jank at an
existing compatible library. The native installer uses jank's C++ interop for
filesystem, SHA-256, and staged rename-publication mechanics. Because current
jank does not expose a portable consumer HTTP/process API, downloads use `curl`
with shell-quoted URL and destination arguments; Linux and macOS users must
have `curl` on `PATH`. It downloads to a staging path, verifies the pinned
release digest, and publishes only the verified file:

```bash
HEGEL_CACHE_DIR="$PWD/.hegel-lib" \
jank -I generated --module-path src:resources:script \
  run-main hegel.install -- setup
```

The same installer can be used with a pre-existing compatible library by
setting `HEGEL_LIBHEGEL_LIBRARY`; that path is checked for existence and is
not downloaded or rehashed. Then run the native and shared semantic gates:

```bash
export HEGEL_LIBHEGEL_LIBRARY=/absolute/path/to/libhegel_c.so

jank -I generated --module-path src:resources:script \
  run-main hegel.jank-backend-smoke

jank -I generated --module-path src:resources:script \
  run-main hegel.jank-property-smoke
```

`hegel.abi/backend-report` reports `:jank/generated` after the bindings load.

## Remaining work

- provide Windows coverage once upstream offers a consumable setup action or
  pinned binary; building jank's custom LLVM/Clang toolchain in every Hegel PR
  is not a practical substitute;
- run the complete shared semantic suite rather than the focused jank gate,
  which now also covers assumptions, framework-less counting/structured
  reporting, and a focused `clojure.test` deftest/is/run-test smoke, but still
  omits arbitrary-precision and exact binary32 generators and broad generator
  coverage;
- broaden `clojure.test` integration beyond the focused deftest/is/run-test
  smoke to the complete `hegel.clojure-test` suite, and add Windows coverage
  for it once Windows jank exists;
- evaluate the optional Malli adapter only after Malli itself is usable on
  jank; and
- produce consumer packaging and installation evidence, since the current gate
  runs from a repository checkout with generated include and source paths.

Until those gates exist, do not present jank as a supported release host. The
generated backend is deliberately kept behind the same narrow boundary so the
experiment can mature without changing consumer property code.
