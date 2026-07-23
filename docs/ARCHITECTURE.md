# Architecture

jolt-hegel is a Jolt-native property-testing library backed by libhegel's C
ABI. Jolt owns the public API and property body; libhegel owns choice generation,
shrinking, replay blobs, collections, pools, and state-machine rule selection.
There is no JVM, daemon, or sidecar process.

## Runtime layers

```text
consumer test
    |
    +-- hegel.clojure-test  clojure.test reporting
    |
    +-- hegel.core          run lifecycle, seeds, replay, diagnostics
            |
            +-- hegel.generator  primitive and compositional generators
            +-- hegel.stateful   rules, invariants, and value pools
            |
            +-- hegel.ffi        checked C ABI and native ownership
                    |
                    +-- libhegel
                    +-- jolt-hegel temporal shim -> libhegel
```

| Path | Responsibility |
| --- | --- |
| `src/hegel/core.clj` | Run settings, engine loop, outcome mapping, failure snapshots, and final replay |
| `src/hegel/generator.clj` | Primitive, formatted, compositional, and collection generators |
| `src/hegel/stateful.clj` | Engine-managed state machines and value pools |
| `src/hegel/clojure_test.clj` | Serialized `clojure.test` report capture and publication |
| `src/hegel/ffi.clj` | Raw bindings, checked return codes, allocation ownership, and ABI compatibility |
| `src/hegel/native.clj` | Cross-platform source-root, cache, and library path resolution |
| `src/hegel/install.clj` | Verified native acquisition and local shim compilation |
| `native/hegel_shim.c` | Pointer-based adapter for date, time, and datetime aggregate arguments |

## Property-run lifecycle

1. `run-test!` checks that the loaded libhegel version matches the pinned ABI.
2. The wrapper resolves a seed before starting the run, creates a context and
   settings object, and supplies the resolved seed to libhegel.
3. The pull loop calls `hegel_next_test_case`, binds the resulting handle as the
   current Jolt test case, runs the property, and marks the case complete.
4. libhegel returns an aggregate result and reproduction blob for each failure.
5. jolt-hegel snapshots failure data, frees engine-owned result objects, and
   replays every blob through `hegel_test_case_from_blob` with `final?` set.
6. All test-case, result, settings, run, and context handles are freed in nested
   `finally` blocks.

The final replay is part of the public behavior. It produces stable diagnostics,
backs `final?`, `when-final`, and `fprn`, and detects failures that do not
reproduce.

## Native boundary

Most libhegel calls are ordinary scalar-or-pointer functions and bind directly
through `jolt.ffi/defcfn`. UUID and IP generation write into caller-owned byte
buffers. Engine-owned byte arrays, strings, string generators, failures, and
result handles are copied as needed and freed immediately by the wrapper.

`hegel_generate_date`, `hegel_generate_time`, and `hegel_generate_datetime` are
the exception: their bounds are C structs passed by value. Jolt 0.4.15 cannot
describe those arguments portably. The temporal shim accepts pointers to the
same structs, dereferences them in target-native C, and calls libhegel with its
real ABI. Compile-time size, alignment, and offset assertions guard the expected
layouts.

The shim does not link to a platform-specific libhegel filename. Its initializer
opens the exact `HEGEL_LIBHEGEL_LIBRARY` selected by the Jolt layer and resolves
only the three temporal symbols. This avoids embedded checkout paths and prevents
Windows from loading a second copy of the DLL.

Several boundary rules are load-bearing:

- Optional C strings use null pointers, never Clojure `nil` passed to a
  `:string` argument.
- Potentially blocking engine calls, including next-case and run cleanup, are
  declared `:blocking` so they do not pin Jolt's runtime.
- Numeric values are coerced to the exact FFI type before a call.
- Every native allocation has one explicit owner and a matching free path.

## Native installation and paths

Git dependencies do not contribute aliases or tasks to a consuming project, so
installation is a public namespace entry point. The consuming project must
activate the alias which contains the dependency:

```bash
joltc -A:test -m hegel.install
```

`test` is only the conventional alias name. A top-level dependency needs no
`-A` option, but an alias-scoped dependency is not on the source roots until its
own alias is active.

The installer downloads the version-pinned libhegel asset for the current target
and verifies it against a SHA-256 embedded in source. It then downloads the
matching jolt-hegel shim and checksum sidecar, or compiles the tagged
`native/hegel_shim.c` when no prebuilt shim is available.

Native paths are resolved from Jolt's current source roots instead of cached
`*file*` metadata. This is required because the AOT cache can retain the path of
the checkout that first compiled a namespace. All subprocess paths are absolute,
which also keeps native Windows builds working when launched from a WSL UNC
checkout.

The default cache is `.hegel-lib/` under the dependency root. Environment
overrides support writable external caches, caller-provided libraries, release
mirrors, alternate target architecture, and a custom C compiler; the complete
list is in the README.

## `clojure.test` boundary

Jolt's `clojure.test/report` is not dynamic, so report capture uses
`with-redefs`, guarded by a process-wide lock. Each generated case is evaluated
without publishing intermediate assertion events. A successful property emits
one pass. A failed property publishes only the final replay's minimal assertion
events, with a synthetic failure as a fallback when replay produced no report.
Every published failure includes the resolved seed. Exception diagnostics use
Jolt's throwable map when `ex-message` is blank, preserving native condition
text and original `ex-data` without exposing only a generic wrapper message.

`run-test!` turns libhegel's two explicit nondeterminism errors into failed
result maps with `:status :error`, `:flaky? true`, and the native explanation in
`:error`. Other run-level errors still throw. One native run is sequential, and
the `clojure.test` report lock serializes complete `with` runs. Independent
concurrent direct runs remain unsupported until shim and engine safety are
covered by a dedicated integration test.

## Release boundary

CI runs the same full integration and independent consumer tests on Linux
x86_64, Windows x86_64, and macOS arm64. A version-matching public tag rebuilds
the three shims, publishes each binary with a checksum sidecar, downloads those
public artifacts through the consumer-visible installer, and runs the consumer
fixture again. Publication is gated to `chucklehead-dev/jolt-hegel`; see
`RELEASING.md`.
