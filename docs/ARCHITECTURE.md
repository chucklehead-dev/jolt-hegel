# Architecture

jolt-hegel is one Clojure-family property-testing library backed by libhegel.
Generators, shrinking and replay orchestration, stateful testing, result data,
reporting, and optional Malli integration are shared. Runtime-specific code is
confined to resource/process discovery and a narrow native boundary.

```text
                         resources/hegel/abi.edn
                                   |
                         hegel.abi validation
                                   |
       core / generators / stateful / reporting / Malli adapter
                                   |
                         hegel.ffi.backend
                         /        |        \
                jolt.ffi   babashka.ffi   java.lang.foreign
                         \        |        /
                                libhegel
```

## Source layers

| Namespace or resource | Responsibility |
| --- | --- |
| `resources/hegel/abi.edn` | Canonical libhegel types, functions, blocking metadata, and ownership |
| `hegel.abi` | Native-code-free descriptor loading, validation, and coverage reports |
| `hegel.core` | Run lifecycle, case outcomes, seeds, shrinking, and final replay |
| `hegel.generator` | Primitive, formatted, compositional, and collection generators |
| `hegel.stateful` | Rules, invariants, swarm-driven state machines, and pools |
| `hegel.clojure-test` | `clojure.test` capture and final publication |
| `hegel.report` | Framework-independent counting and reporting |
| `hegel.malli` | Optional Malli AST adapter built from shared generators |
| `hegel.ffi` | Backend-neutral checked libhegel operations and explicit ownership |
| `hegel.ffi.backend` | Selected-backend contract |
| `hegel.ffi.jolt`, `.bb`, `.jvm` | Signature construction, native memory, calls, and layouts |
| `hegel.host` | Host identity and resource loading |
| `hegel.native` | Platform, cache, and library path selection |
| `hegel.install` | Version-pinned, checksum-verified native acquisition |

The common implementation never asks which runtime it is running on. Reader
conditionals select a host implementation once; ordinary property and stateful
code calls the same boundary.

## Native backend contract

The contract is intentionally smaller than any host FFI API. It can load one
library, construct functions from the ABI descriptor, allocate and free native
memory, read and write scalar values and byte ranges, convert UTF-8 strings,
and access fields in descriptor-derived layouts.

- Jolt translates descriptor types to `jolt.ffi` aggregate and function
  descriptors. Calls marked blocking in the ABI use Jolt's blocking call path.
- Babashka translates the same data to `babashka.ffi`. Common fixed signatures
  use its compiled trampoline; signatures outside that fast family use its
  general libffi path. The backend report records the selected route.
- JVM Clojure constructs `MemoryLayout`, `FunctionDescriptor`, and downcall
  `MethodHandle` values once per binding with `Linker/nativeLinker`. Symbols are
  resolved through a lookup tied to the selected library.

By-value `date`, `time`, and nested `datetime` arguments are ordinary descriptor
types. Each backend computes native alignment, padding, offsets, and argument
classification for its platform rather than sharing guessed constants.

See [ABI.md](ABI.md) for the schema, diagnostics, and binding workflow.

## Property-run lifecycle

1. `run-test!` verifies the loaded libhegel version against the descriptor pin.
2. It resolves a seed, creates a context and settings, and starts a native run.
3. The pull loop binds each native test-case handle while the shared Clojure
   property draws values and returns, rejects, overruns, or throws.
4. libhegel shrinks interesting choices and returns reproduction blobs.
5. jolt-hegel copies failure data before releasing result handles, then replays
   each minimized blob with `final?` enabled.
6. Nested `finally` paths release every wrapper-owned context, run, result,
   collection, state-machine, pool, and temporary allocation.

Final replay is public behavior, not merely reporting. Reproduction requires a
failure with the same stable origin; a missing or different failure marks the
result flaky.

## Ownership and lifetime

Ownership remains explicit across all hosts:

- caller-owned allocations are paired with `free` in lexical `finally` paths;
- borrowed strings are copied while their owner remains alive;
- engine result bytes and strings are copied before their release function;
- collection and state-machine handles are released by the operation that
  created them; and
- pools register cleanup against their current test case and cannot escape it.

JVM arenas implement the backend's explicit allocation lifetime; garbage
collection is not the primary ownership mechanism. A backend must preserve the
same common ownership contract even if its host FFI offers automatic cleanup.

## Stateful boundary

libhegel owns rule choice, sequence length, shrinking, and random nonempty swarm
selection. The shared layer maps rule indices to named Clojure transitions and
runs invariants initially and after successful steps. The public API currently
drives the libhegel round protocol at concurrency one.

Pool variables are native integer identities mapped to arbitrary host values.
Reusable draws retain a mapping, consumed draws remove it, and an empty draw is
an assumption rejection.

## Installation boundary

All hosts use the same pinned version and checksum table. `HEGEL_CACHE_DIR`
selects a writable cache, while `HEGEL_LIBHEGEL_LIBRARY` selects an explicit
library. Common selection and verification orchestration calls the narrow
`hegel.install.backend` seam; filesystem, process, download, and digest
mechanics live in `hegel.install.jolt` or `hegel.install.jvm`. Jolt retains its
source/AOT identity check because cached compiled namespaces can otherwise
point at a different Git checkout. JVM Clojure and Babashka use their normal
download and digest facilities.

The runtime verifies libhegel's reported version before executing a property,
including when the library path was supplied by the user.

## Optional dependencies

Requiring `hegel.malli` opts into a consumer-supplied Malli dependency. The
adapter compiles a schema and validator once, rejects unsupported AST constructs
synchronously, and composes shared Hegel generators so structural spans remain
available to shrinking. The core library never loads Malli.

## Experimental hosts

The jank spike selects a generated C++ interop backend at the same boundary;
ordinary property, generator, and stateful namespaces remain shared. Its
generated artifacts are derived from `resources/hegel/abi.edn`, not maintained
as another signature list. See [JANK.md](JANK.md) for the proven surface,
commands, and remaining release gates.
