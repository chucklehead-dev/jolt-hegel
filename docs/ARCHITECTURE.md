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
```

| Path | Responsibility |
| --- | --- |
| `src/hegel/core.clj` | Run settings, engine loop, outcome mapping, failure snapshots, and final replay |
| `src/hegel/generator.clj` | Primitive, formatted, compositional, and collection generators |
| `src/hegel/stateful.clj` | Engine-managed state machines and value pools |
| `src/hegel/clojure_test.clj` | Dynamically scoped `clojure.test` report capture and publication |
| `src/hegel/report.clj` | Counting and structured reporting for framework-less suites |
| `src/hegel/ffi.clj` | Raw bindings, checked return codes, allocation ownership, and ABI compatibility |
| `src/hegel/native.clj` | Cross-platform source-root, cache, and library path resolution |
| `src/hegel/install.clj` | Verified libhegel acquisition |

## Property-run lifecycle

1. `run-test!` checks that the loaded libhegel version matches the pinned ABI.
2. The wrapper resolves a seed before starting the run, creates a context and
   settings object, and supplies the resolved seed to libhegel.
3. The pull loop calls `hegel_next_test_case`, binds the resulting handle as the
   current Jolt test case, runs the property, marks the case complete, and
   retains bounded structured summaries of interesting outcomes.
4. libhegel returns an aggregate result and reproduction blob for each failure.
5. jolt-hegel snapshots failure data, frees engine-owned result objects, and
   replays every blob through `hegel_test_case_from_blob` with `final?` set.
6. All collection, pool, state-machine, test-case, result, settings, run, and
   context handles are freed by their exact owners in nested `finally` blocks.

The final replay is part of the public behavior. It produces stable diagnostics,
backs `final?`, `when-final`, and `fprn`, and detects failures that do not
reproduce.

## Native boundary

Most libhegel calls are ordinary scalar-or-pointer functions and bind directly
through `jolt.ffi/defcfn`. UUID and IP generation write into caller-owned byte
buffers. Engine-owned byte arrays, strings, string generators, failures, and
result handles are copied as needed and freed immediately by the wrapper.

`hegel_generate_date`, `hegel_generate_time`, and `hegel_generate_datetime`
pass their bounds as C structs by value. Jolt's declarative layouts derive each
size, alignment, and field offset from Chez, and `[:by-value ...]` signatures
pass those caller-owned buffers directly to libhegel. No target-specific adapter
or copied ABI constants remain in jolt-hegel.

libhegel 0.33 represents collections, value pools, and state machines as opaque
caller-owned handles. Collection and state-machine handles are freed in the
lexical operation that creates them. Pools remain public test-case values, so
their frees are registered on the active `TestCase` and run before its native
handle is released. Sequential state machines use Hegel's round protocol with
one all-zero rule group and fixed concurrency one; rejected rules are reported
back to the engine before the round continues.

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
jolt -A:test -m hegel.install
```

`test` is only the conventional alias name. A top-level dependency needs no
`-A` option, but an alias-scoped dependency is not on the source roots until its
own alias is active.

The installer downloads the version-pinned libhegel asset for the current target
and verifies it against a SHA-256 embedded in source.

Native paths are resolved from Jolt's current source roots instead of cached
`*file*` metadata. This is required because the AOT cache can retain the path of
the checkout that first compiled a namespace. All subprocess paths are absolute,
which also keeps native Windows builds working when launched from a WSL UNC
checkout.

Before fetching native artifacts, the installer parses the release
version from the currently resolved `src/hegel/version.clj` and compares it with
the loaded `hegel.version` var. A mismatch means Jolt reused stale AOT output;
the installer fails with instructions to use a fresh `JOLT_CACHE_DIR` keyed by
the pinned release SHA.

The default cache is `.hegel-lib/` under the dependency root. Environment
overrides support writable external caches, caller-provided libraries, release
mirrors, and alternate target architecture; the complete list is in the README.

## `clojure.test` boundary

The supported Jolt runtime makes `clojure.test/report` dynamically bindable, so
report capture uses `binding` and is isolated per evaluation. Each generated case is evaluated
without publishing intermediate assertion events. A successful property emits
one pass. A failed property publishes only the final replay's minimal assertion
events, with a synthetic failure as a fallback when replay produced no report.
Every published failure includes the resolved seed. Exception diagnostics use
Jolt's throwable map when `ex-message` is blank, preserving native condition
text and original `ex-data` without exposing only a generic wrapper message.

`run-test!` turns libhegel's two explicit nondeterminism errors into failed
result maps with `:status :error`, `:flaky? true`, and the native explanation in
`:error`. Other run-level errors still throw. One native run is sequential.
Independent concurrent direct runs remain unsupported until engine
safety are covered by a dedicated integration test.

## Release boundary

CI runs the same full integration and independent consumer tests on Linux
x86_64, Windows x86_64, and macOS arm64. A version-matching public tag runs the
same source and consumer gates; jolt-hegel publishes no native artifact of its
own. See `RELEASING.md`.
