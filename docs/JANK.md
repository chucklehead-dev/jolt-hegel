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
identity as runtime provenance.

## What works

- all 77 libhegel 0.33.3 symbols are generated from the canonical ABI descriptor
  and resolved from the selected library;
- fixed-width integer, floating-point, pointer, UTF-8, out-parameter, and bulk
  byte operations;
- `date`, `time`, and nested `datetime` structs passed by value;
- the shared property runner, deterministic seeds, shrinking, final replay,
  and failure reproduction;
- shared temporal generators; and
- shared stateful/swarm execution and reusable or consumed pools.

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

Install libhegel 0.33.3 with one of the supported host installers, or point jank
at an existing compatible library. Then run the native and shared semantic
gates:

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
- add a jank-native installer backend; current CI deliberately installs
  checksum-verified libhegel through Babashka before starting jank;
- run the complete shared semantic suite rather than the focused jank gate;
- determine whether `clojure.test` integration is implementable on the current
  jank standard library;
- evaluate the optional Malli adapter only after Malli itself is usable on
  jank; and
- produce consumer packaging and installation evidence, since the current gate
  runs from a repository checkout with generated include and source paths.

Until those gates exist, do not present jank as a supported release host. The
generated backend is deliberately kept behind the same narrow boundary so the
experiment can mature without changing consumer property code.
