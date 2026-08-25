# ADR 0006: One portable library with a canonical ABI

- Status: Accepted
- Date: 2026-08-24

## Context

jolt-hegel began as a Jolt library, but its public property-testing behavior is
ordinary Clojure. Generators, shrinking, replay, stateful rules, pools,
reporting, and Malli translation do not benefit from host-specific copies. The
part that differs is how one native ABI is described, called, and given memory.

Maintaining separate native signature lists would make ABI review difficult and
allow one runtime to drift from the others. The JVM also has several third-party
FFI choices, but adding one would bring a second type and ownership model for a
small, fixed interface. Babashka already has a native FFI designed for its
runtime.

## Decision

Maintain one public library and one behavioral contract for Jolt, Babashka, and
JVM Clojure. Keep portable behavior in shared namespaces and isolate runtime
selection behind `hegel.host` and `hegel.ffi.backend`.

Make `resources/hegel/abi.edn` the canonical source for C types, structs,
function pointers, symbols, signatures, blocking metadata, and ownership notes.
The data contains C semantics only. Each backend must mechanically report
whether it supports every descriptor entry before normal operation.

Use the native facility of each host:

- Jolt uses `jolt.ffi`, including aggregate layouts and by-value calls.
- Babashka uses `babashka.ffi`, preferring its compiled trampoline and using
  its general libffi route for supported unusual fixed signatures.
- JVM Clojure uses the finalized `java.lang.foreign` API directly on JDK 22 or
  later. Bindings cache library-local symbols, function descriptors, and
  downcall method handles.

Do not add Coffi, JNA, or dtype-next as a runtime dependency. dtype-next remains
useful architectural prior art for separating descriptions, memory, and
backend selection.

## Consequences

- The repository name remains jolt-hegel while portability is completed.
- Public properties and result maps have one source and one parity suite.
- Adding a libhegel call begins with one EDN edit, not three binding lists.
- Backend limitations are visible through structured coverage and route
  diagnostics.
- JVM native memory uses explicit arena-backed ownership matching the common
  lifecycle; garbage collection is not the release protocol.
- Babashka FFI gaps are fixed generally in the Babashka fork with independent
  tests and documentation, never with libhegel symbol special cases.
- CI must cover host and operating-system combinations independently so a pass
  on one ABI does not imply another.
- Malli remains an optional consumer dependency and may be limited only where
  the host or Malli itself is incompatible.

## 2026-08-25 implementation note

The supported decision above is now implemented on Jolt, Babashka, and JVM
Clojure. Experimental jank and ClojureCLR ports were subsequently added without
changing the boundary: both derive checked native artifacts from the same EDN
descriptor and reuse shared property behavior. Their focused CI is useful
portability evidence, but neither port joins the supported release contract
until it passes the corresponding full semantic, consumer, packaging, and
platform gates.
