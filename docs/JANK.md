# Experimental jank host

jank support is an active portability experiment, separate from the supported
Jolt, Babashka, and JVM release matrix. The current Linux x86_64 spike loads
libhegel directly through jank's C++ interop and preserves the shared Hegel API;
it does not introduce another property-testing implementation.

The current gate has been exercised with `jank-0.1-alpha`, binary identity:

```text
x86_64-unknown-linux-gnu-a836dc6b7a6df7c434796123b874a4a0e89caa94399ea7c98b2116699e30088f
```

Run `jank check-health` first. Both JIT compilation and AOT compilation must be
healthy, and the C++ compiler bundled with jank must be available.

## What works

- all 71 libhegel 0.33 symbols are generated from the canonical ABI descriptor
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

Install libhegel 0.33 with one of the supported host installers, or point jank
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

- prove the backend and libhegel installer on the intended macOS and Windows
  jank targets;
- run the complete shared semantic suite rather than the focused jank gate;
- determine whether `clojure.test` integration is implementable on the current
  jank standard library; and
- evaluate the optional Malli adapter only after Malli itself is usable on
  jank.

Until those gates exist, do not present jank as a supported release host. The
generated backend is deliberately kept behind the same narrow boundary so the
experiment can mature without changing consumer property code.
