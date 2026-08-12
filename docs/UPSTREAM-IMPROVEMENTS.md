# Upstream improvements for jolt-hegel

This document records changes to Jolt, `jolt.ffi`, libhegel, or shared Jolt
tooling that would make jolt-hegel safer, smaller, or easier to use. It is a
local planning document, not an upstream issue tracker.

The original observations were recorded on 2026-07-23 against:

- jolt-hegel 0.1.2 at `4fff14c3c4324a6a0e9cfbc49011c1d2d1214735`;
- Jolt 0.4.15 at `260a392a795089de3fb5ab700b386a334f01c051`; and
- libhegel 0.30.1 at `f81c6cceabe3c6695a249588c751a5ce93dffa00`.

The cross-project audit also checked installed `joltc v0.4.15` and Jolt commit
`d5aaf503fc7a45c5638d21215eb153b426a7e8dc` vendored by jolt-tcp. Record the
exact Jolt SHA, target, reproducer, observed result, and current-main result in
each upstream issue; these host/runtime details can change independently of
jolt-hegel.

## Jolt 0.7.5 and libhegel 0.32.3 revalidation

The migration was rechecked on 2026-08-11 against the official Jolt 0.7.5 tag
and libhegel 0.32.3 C ABI. The historical sections below retain their original
reproducers, but their current disposition is:

| Original proposal | Current disposition |
| --- | --- |
| Collision-safe, dependency-aware AOT identity | Implemented in Jolt 0.7.5: full-source hashing, runtime fingerprinting, dependency digests, tracing-mode identity, and atomic publication are present. Keep jolt-hegel's release-version check only as a cheap defense-in-depth canary. |
| Dynamically bindable `clojure.test/report` | Implemented in Jolt 0.7.5. jolt-hegel now uses `binding` and no longer owns a process-global report lock. |
| C aggregates passed by value | Still missing from `jolt.ffi`; the temporal shim remains required. |
| Streaming SHA-256 | Available through the official `jolt-crypto` library rather than Jolt core. Moving the installer to it is a jolt-hegel dependency decision, not a core runtime patch. |
| Platform-aware process launch | Still incomplete in the Chez host: executable lookup and path handling remain POSIX-shaped, so the Windows installer fallback remains. |
| Atomic replace on Windows | `java.nio.file.Files/move` and replace options now exist, but core `spit` still calls `rename-file` directly. Keep the installer workaround until an exact Windows overwrite gate proves otherwise. |
| Declarative native artifact acquisition | Still missing; `:jolt/native` declares already-present libraries but does not fetch and verify release assets. |
| FFI layout and scoped-allocation helpers | Still missing from the public `jolt.ffi` surface. libhegel 0.32.3's caller-owned opaque handles make this more useful, not less. |
| Complete target descriptor | Partly improved (`availableProcessors` is real), but no single public ABI/artifact descriptor replaces jolt-hegel's target normalization. |
| Typed libhegel run errors and resolved-seed getter | Still absent from libhegel 0.32.3's C ABI. |

libhegel itself added changes which jolt-hegel must consume rather than propose
upstream: configurable stateful step counts (0.30.5), caller-owned opaque
collection/pool/state-machine handles with exact free functions (0.31.0),
rejected-rule notification (0.32.0), unbiased rule selection (0.32.1), improved
repeated-span mutation (0.32.2), and correct replay of stateful blobs beyond 50
steps (0.32.3).

## Priority summary

| Priority | Upstream area | Change | Main payoff |
| --- | --- | --- | --- |
| Done | Jolt core | Collision-safe, dependency-aware AOT identity | Implemented in Jolt 0.7.5 |
| Available | Jolt crypto/tooling | Native streaming SHA-256 | Use the official `jolt-crypto` library if its added dependency is acceptable |
| P1 | `jolt.ffi` | Support C aggregates passed by value | Delete the target-specific temporal shim and its release pipeline |
| Done | Jolt `clojure.test` | Dynamically bindable report sink | Consumed by this migration |
| P1 | Jolt process API | Make `ProcessBuilder` genuinely platform-aware | Remove the Windows `system(3)` and quoting workarounds |
| P1 | Jolt I/O | Atomically replace an existing file on Windows | Remove delete-before-`spit` workarounds without weakening atomicity |
| P1 | libhegel C ABI | Return typed run-error details and structured nondeterminism evidence | Remove message-prefix parsing and improve flaky failure reports |
| P2 | Jolt dependency tooling | Acquire checksum-pinned native artifacts declared by dependencies | Replace most of `hegel.install` with a shared mechanism |
| P2 | `jolt.ffi` | Add struct-layout and scoped-allocation helpers | Reduce manual pointer arithmetic and native-lifetime boilerplate |
| P2 | `jolt.host` | Expose a complete target descriptor | Centralize artifact and ABI selection facts |
| P2 | libhegel C ABI | Expose the resolved seed | Let libhegel choose fresh seeds while preserving exact replay |

P0 means a demonstrated correctness problem. P1 changes remove a significant
constraint or maintenance surface. P2 changes are worthwhile consolidation or
ergonomic improvements.

## 1. Make the Jolt AOT cache collision-safe and dependency-correct

### Current constraint

Jolt 0.4.15 names a namespace cache entry using the source string's length and
Chez `equal-hash`. The path is not part of the key. See Jolt's
[`aot-cache-key`](https://github.com/jolt-lang/jolt/blob/260a392a795089de3fb5ab700b386a334f01c051/host/chez/loader.ss#L350-L396)
and
[`aot-load-or-compile`](https://github.com/jolt-lang/jolt/blob/260a392a795089de3fb5ab700b386a334f01c051/host/chez/loader.ss#L471-L486).

This has produced a real false cache hit. The 0.1.0 and 0.1.2 versions of
`src/hegel/version.clj` are both 297 bytes and resolve to:

```text
hegel.version-129-46D5E3157AB267D
```

With a fresh shared cache, loading 0.1.0 produced a cache miss and printed
`0.1.0`. Loading the 0.1.2 checkout with that cache then produced a cache hit
and still printed `0.1.0`. The current installer caught the bad load:

```text
Jolt loaded jolt-hegel 0.1.0 from a stale AOT cache, but the resolved
source checkout is 0.1.2.
```

The cache also preserves emitted source metadata. A cache hit from a different
checkout can therefore leave var or form `:file` metadata naming the checkout
which first populated the cache, even when the source bytes are identical.
That is why [native path resolution](../src/hegel/native.clj) uses current
source roots instead of trusting cached `*file*` metadata.

There is a second, independent stale-code mode. A two-namespace v0.4.15 probe
compiled an unchanged consumer which called a macro expanding to `:v1`. After
changing only the macro namespace so it expanded to `:v2`, the next run reported
a cache hit for the consumer and a miss for the macro namespace, then still
returned `:v1`. A full SHA-256 digest of only the consumer source would not fix
that result: the compiled artifact embeds compile-time dependency output.

### Upstream change

The cache identity should cover every compile input:

- a cryptographic digest of every namespace source byte;
- fingerprints of macro providers, data readers, and namespaces loaded during
  compilation;
- compiler options, feature/configuration inputs, and Jolt/compiler version;
  and
- the complete target identity.

Changing the key format should also advance the cache-format directory so old
entries cannot be mistaken for new ones. Jolt should supply a native streaming
SHA-256 primitive and use it here; substituting another small runtime hash only
moves the collision boundary.

Source provenance needs a separate explicit policy:

- include the normalized resolved source identity in the key; or
- keep cross-checkout code reuse, but rebase load-site metadata so `:file`,
  `*file*`, and diagnostics describe the source which was actually resolved for
  the current run.

The second option preserves more cache reuse, but is only safe if no other
checkout-specific values are captured in emitted output.

### Acceptance criteria

- Two same-length sources which differ only in a late string literal get
  different cache keys and load their own values.
- An unchanged consumer whose macro dependency changes is recompiled and
  observes the new expansion.
- Compiler option or target changes cannot reuse an incompatible artifact.
- Requiring identical source from two checkout paths reports the second
  checkout in source metadata and exceptions.
- Parallel cache publication and corrupt-entry recovery continue to pass.
- `:reload` and `:reload-all` continue to bypass the cache.

### jolt-hegel payoff

After the fix is available in the minimum supported Jolt release, jolt-hegel can
remove `hegel.install/verify-source-version!`, stop requiring consumers to key
`JOLT_CACHE_DIR` by release SHA, and simplify native-root discovery. The guard
should remain for at least one compatibility window because older Jolt binaries
can still populate or read unsafe entries.

`verify-source-version!` should be described as a canary, not as the cache
correctness boundary. It catches the known visible release mismatch but cannot
detect an arbitrary stale macro expansion, constant, or compiled dependency.

## 2. Support by-value C aggregates in `jolt.ffi`

### Current constraint

`jolt.ffi` accepts scalar, pointer, and string type keywords, but has no struct
or union descriptor. Its current public type list is in
[`stdlib/jolt/ffi.clj`](https://github.com/jolt-lang/jolt/blob/260a392a795089de3fb5ab700b386a334f01c051/stdlib/jolt/ffi.clj#L14-L21).

libhegel's date, time, and datetime generators take their minimum and maximum
bounds as C structs by value. jolt-hegel consequently ships
[`native/hegel_shim.c`](../native/hegel_shim.c), which accepts pointers,
dereferences them in target-native C, and calls the real functions. The shim
also has to open the exact libhegel library and resolve the three symbols at
runtime.

This is more than a missing `sizeof` helper. Passing aggregates correctly
requires the foreign-call lowering to follow each target ABI's aggregate
classification rules, including the differing Windows x64, System V AMD64, and
AArch64 conventions.

### Upstream change

`jolt.ffi` needs named or inline aggregate descriptors usable in argument and
return positions, plus enough layout information to construct their values.
For example, the API needs to express nested fields, integer widths, alignment,
and padding without hard-coding offsets in every consumer. The exact surface is
less important than delegating call ABI classification to the native FFI layer
on every supported target.

### Acceptance criteria

- A conformance library can pass and return small, large, and nested structs by
  value on Linux x86_64, Windows x86_64, and macOS arm64.
- The direct libhegel date, time, and datetime calls honor lower and upper
  bounds on all three targets.
- Layout inspection agrees with a C compiler's `sizeof`, `_Alignof`, and
  `offsetof`.
- Optimized builds and interpreted runs use the same ABI.

### jolt-hegel payoff

jolt-hegel can bind the three libhegel functions directly and delete:

- `native/hegel_shim.c`;
- shim loading and symbol-resolution code in
  [`hegel.ffi`](../src/hegel/ffi.clj);
- shim download, checksum, marker, and compiler fallback paths in
  [`hegel.install`](../src/hegel/install.clj);
- `HEGEL_SHIM_LIBRARY`, `HEGEL_SHIM_RELEASE_BASE`, and shim-specific cache
  state; and
- the target-specific shim build and release jobs.

This is the single largest code and release simplification available from an
upstream FFI change.

## 3. Add a dynamically bindable `clojure.test` report sink

### Current constraint

Jolt defines `clojure.test/report` as a normal multimethod and `do-report`
invokes it directly. It is not dynamic; see
[`stdlib/clojure/test.clj`](https://github.com/jolt-lang/jolt/blob/260a392a795089de3fb5ab700b386a334f01c051/stdlib/clojure/test.clj#L70-L89).

[`hegel.clojure-test/with`](../src/hegel/clojure_test.clj) must temporarily
replace the root of `clojure.test/report` to capture the assertion events from
each generated case. Because `with-redefs` changes process-global state, the
whole property run is protected by a global lock. Separate `with` properties
cannot report concurrently even if their native runs are otherwise independent.

### Upstream change

`do-report` should call a dynamically bindable sink whose default delegates to
the existing `report` multimethod. This preserves `defmethod report` as the
normal extension point while letting a library capture events with a thread- or
binding-local override.

The sink should cover every assertion path, including custom `assert-expr`
methods, fixtures, and errors raised while evaluating an assertion.

### Acceptance criteria

- Two concurrent evaluations can bind different sinks without receiving each
  other's events.
- An unbound sink preserves the current multimethod and counter behavior.
- Nested bindings restore the outer sink.
- Existing custom `report` methods continue to work.

### jolt-hegel payoff

jolt-hegel can replace `with-redefs` with `binding`, delete the report lock, and
isolate report capture per property. This removes a consumer-visible
serialization point. It does not by itself prove that concurrent direct
libhegel runs are safe; that remains a separate integration test.

## 4. Make Jolt process launching platform-aware

### Current constraint

Jolt's current `ProcessBuilder` implementation resolves programs using `/`,
splits `PATH` on `:`, recognizes only a leading `/` as absolute, and constructs
a shell command. See
[`host/chez/java/process.ss`](https://github.com/jolt-lang/jolt/blob/260a392a795089de3fb5ab700b386a334f01c051/host/chez/java/process.ss#L268-L310).

On Windows, a valid absolute executable such as `C:\...\gcc.exe` can fail the
preflight check. Windows also needs drive and UNC path handling, `;`-separated
`PATH`, `PATHEXT`, and different argument quoting rules.

[`hegel.install`](../src/hegel/install.clj) currently binds libc `system(3)`,
builds a Windows command string manually, and invokes PowerShell for downloads
to get around these limitations. That duplicates process behavior and makes
argument-boundary correctness the installer's responsibility.

### Upstream change

`ProcessBuilder` should resolve and launch programs with platform-native path
and argument semantics. On Windows this should include:

- drive-absolute and UNC paths;
- both path separators;
- `PATH` separated by `;` and executable lookup through `PATHEXT`;
- correct handling of spaces, quotes, and empty arguments;
- working-directory and environment overrides; and
- stream redirection without routing argv through an extra command shell.

### Acceptance criteria

- Launch an absolute `C:\...\program.exe` whose path contains spaces.
- Resolve a bare executable through Windows `PATH` and `PATHEXT`.
- Preserve arguments containing spaces, quotes, metacharacters, and empty
  strings on Windows and POSIX.
- `clojure.java.shell/sh` and `jolt.process` pass the same cross-platform cases.

### jolt-hegel payoff

The installer can use `clojure.java.shell/sh` consistently and delete
`c-system`, `windows-command`, and the custom Windows shell-exit handling. It
also makes local shim compilation safer while the shim still exists.

## 5. Atomically replace existing files on Windows

### Current constraint

Jolt's non-append `spit` correctly writes to a sibling temporary file, then
calls `rename-file` over the destination. See
[`jolt-spit`](https://github.com/jolt-lang/jolt/blob/260a392a795089de3fb5ab700b386a334f01c051/host/chez/java/io.ss#L387-L415).
That replaces an existing file on POSIX, but the current Windows rename path
does not.

jolt-hegel deletes an existing release marker before calling `spit`; its test
runner does the same for the progress file. This avoids the Windows error but
creates a window in which the destination does not exist, defeating the
atomic-replacement guarantee.

### Upstream change

Jolt should expose and use a replace-existing atomic move on each platform,
with a documented fallback when the filesystem cannot provide atomic
replacement. On Windows that likely means a native replacement operation
rather than delete followed by rename.

### Acceptance criteria

- Repeatedly `spit` over an existing file on Windows without a pre-delete.
- A failed render or write leaves the previous destination intact.
- Successful replacement leaves no temporary file.
- Concurrent readers see either the old or new complete contents, never a
  missing or partial destination on filesystems that support atomic replace.

### jolt-hegel payoff

The installer and test runner can remove their delete-before-`spit`
workarounds and regain the atomicity their comments currently have to disclaim.

## 6. Return typed libhegel run errors and nondeterminism evidence

### Current constraint

libhegel's C ABI reports a run as passed, failed, or errored. Every errored run
then exposes only one string, even though the documented causes include health
checks, nondeterministic tests, and engine panics. See
[`hegel_run_status_t`](https://github.com/hegeldev/hegel-rust/blob/f81c6cceabe3c6695a249588c751a5ce93dffa00/hegel-c/include/hegel.h#L162-L178)
and
[`hegel_run_result_error`](https://github.com/hegeldev/hegel-rust/blob/f81c6cceabe3c6695a249588c751a5ce93dffa00/hegel-c/include/hegel.h#L1593-L1620).

[`hegel.core/run-test!`](../src/hegel/core.clj) distinguishes the two known
nondeterminism outcomes by matching the prefixes `"Flaky test detected:"` and
`"Your data generation is non-deterministic:"`. A wording change can therefore
turn a countable flaky result back into a thrown generic run error.

Run-level nondeterminism has no failure snapshot or reproduction blob.
jolt-hegel retains bounded `:observed-failures` while driving cases so a
consumer has some structured evidence, but the wrapper cannot report the
engine's original and replayed outcomes or the exact mismatch which caused
libhegel to classify the run as flaky.

### Upstream change

The C ABI should expose a stable run-error category independently of the human
message. Useful categories include health check, flaky property outcome,
nondeterministic data generation, engine panic, and other internal error.
Health checks should additionally expose their specific kind.

For nondeterminism, a structured diagnostic should describe as much of the
comparison as the engine safely retains: phase, origin, original and replayed
statuses, and a reproduction blob or choice prefix when one is meaningful.
This data should supplement, not replace, the readable error message.

### Acceptance criteria

- A binding can classify every run error without parsing English text.
- Flaky outcome and nondeterministic generation have distinct stable codes.
- Health-check failures expose the health-check kind.
- Nondeterminism diagnostics distinguish the original and repeated outcomes.
- Existing error strings remain available for people and logs.

### jolt-hegel payoff

jolt-hegel can remove `nondeterministic-run-error?`, map result categories
directly, and provide materially better `:flaky?` diagnostics. The local
`:observed-failures` hook remains useful for application-specific exception
data, but no longer has to compensate for an untyped engine result.

## 7. Add declarative native-artifact acquisition to Jolt tooling

### Current constraint

Jolt already collects inherited `:jolt/native` declarations and loads matching
local shared-library candidates. The declaration describes libraries which are
already present; it does not download a target artifact, verify its digest, or
manage a dependency-scoped native cache. See
[`jolt.deps/resolve-project`](https://github.com/jolt-lang/jolt/blob/260a392a795089de3fb5ab700b386a334f01c051/jolt-core/jolt/deps.clj#L323-L421)
and
[`jolt.main/load-natives!`](https://github.com/jolt-lang/jolt/blob/260a392a795089de3fb5ab700b386a334f01c051/jolt-core/jolt/main.clj#L18-L43).

Git dependencies also do not contribute executable aliases or tasks to the
consumer. jolt-hegel therefore exposes `hegel.install` as a public namespace
and owns all of the following:

- OS and architecture normalization;
- release URL and filename selection;
- embedded SHA-256 values and verification;
- mirrors and caller-supplied path overrides;
- cache directories and release markers;
- cross-platform download commands; and
- a local compiler fallback for the shim.

On POSIX, the installer also searches for and loads OpenSSL `libcrypto`, reads
the complete artifact into native memory, and calls `SHA256` directly. Windows
uses PowerShell's `Get-FileHash`. This is security-sensitive machinery that
every checksum-verifying Jolt tool would otherwise have to repeat.

### Upstream change

A shared Jolt command or small companion library could install declaratively
specified native artifacts before a run. It should be explicit rather than
silently performing network access during `require`. The declaration needs:

- a target-descriptor map rather than ad-hoc OS/architecture strings;
- immutable artifact identity and SHA-256;
- deterministic cache layout;
- offline and mirror behavior;
- a caller-provided-library override;
- clear optional-versus-required semantics; and
- support for inherited dependency declarations.

Verification should use Jolt's native streaming SHA-256 primitive. It must not
require a consumer library to locate OpenSSL or hold the entire artifact in one
native allocation.

Local build fallback is useful but can be a separate hook. It should not be
required for the first version of artifact acquisition.

### Acceptance criteria

- A consumer with jolt-hegel in a test alias can invoke one standard command
  which discovers the inherited native declaration.
- The command installs the correct target artifact, rejects a digest mismatch,
  and is idempotent.
- Offline reuse and mirror overrides are testable without changing library
  source.
- Ordinary source loading never performs an implicit download.
- Large artifacts are hashed incrementally with bounded memory on every
  supported host.

### jolt-hegel payoff

Once by-value aggregate support removes the shim, this mechanism could replace
nearly all of `hegel.install` for libhegel itself. It would also eliminate the
alias-specific installer command that every test-only consumer must currently
copy into setup instructions.

## 8. Add struct-layout and scoped-allocation helpers to `jolt.ffi`

### Current constraint

[`hegel.ffi`](../src/hegel/ffi.clj) manually implements repeated native
patterns:

- allocate an out parameter, call a function, read the value, and free it;
- encode a temporary UTF-8 C string and free it in `finally`;
- build and free arrays of C-string pointers;
- calculate aggregate sizes and field offsets; and
- allocate several related buffers with nested cleanup paths.

The explicit ownership is correct and should remain visible, but every binding
has to reproduce the same exception-safe scaffolding. Manual offsets are also
easy to get right on one ABI and wrong on another.

### Upstream change

`jolt.ffi` or a shared companion namespace could provide:

- scoped `with-alloc`, `with-out`, `with-c-string`, and C-string-array helpers;
- named struct layouts with `sizeof`, alignment, and field offsets; and
- typed field read/write operations derived from those layouts.

These should use deterministic lexical cleanup. Garbage-collector finalizers
are not an adequate replacement for explicit native ownership.

### Acceptance criteria

- Allocations are freed on normal return and on a thrown Jolt or native error.
- Nested and array layouts agree with a C compiler on every supported target.
- Helpers compose without hiding which side owns returned engine memory.
- Existing primitive `alloc`, `free`, `read`, and `write` remain available.

### jolt-hegel payoff

The low-level binding becomes shorter and easier to audit, and the temporal
layout constants can be derived instead of copied. These helpers reduce risk
even if full by-value aggregate calls take longer to implement.

## 9. Expose libhegel's resolved seed

### Current constraint

The C settings API can ask libhegel to choose a fresh seed by calling
`hegel_settings_set_seed` with `has_seed = false`, but the finished result has
no seed getter. See
[`hegel_settings_set_seed`](https://github.com/hegeldev/hegel-rust/blob/f81c6cceabe3c6695a249588c751a5ce93dffa00/hegel-c/include/hegel.h#L748-L766).

jolt-hegel promises that every result and failure report contains a replayable
seed. It therefore creates a seed itself and always passes `has_seed = true`.
That is safe, but duplicates engine policy and prevents the binding from using
libhegel's native fresh-seed path.

### Upstream change

The run or run-result API should expose the actual seed selected after explicit,
fresh, or derandomized configuration. The getter must be available for errored
runs as well as passed and failed runs.

### Acceptance criteria

- An explicit seed is returned unchanged.
- A fresh engine-selected seed is returned and exactly replays the run.
- Derandomized runs return the derived seed.
- Passed, failed, health-check, and nondeterministic results all retain it.

### jolt-hegel payoff

jolt-hegel can delegate fresh and derandomized seed selection to libhegel while
keeping its public replay contract. Its local seed code can shrink to validation
and decimal presentation.

## 10. Expose a complete Jolt target descriptor

### Current constraint

jolt-hegel normalizes `os.name` itself and searches several environment
variables plus `uname -m` because `System/getProperty "os.arch"` is not a
reliable Jolt source. It also implements Windows/POSIX path recognition and
separator selection locally.

Native selection and FFI ABI decisions need more than OS and architecture. In
current Jolt source, `Runtime.availableProcessors` is hardcoded to `1`; a live
v0.4.15 probe returns `1`. The host layer also contains POSIX separator
assumptions.

### Upstream change

Expose one stable descriptor containing:

- operating system and architecture;
- ABI/calling convention and libc family;
- endianness and pointer width;
- file and path-list separators; and
- available processor count.

Unknown values should be explicit. ABI must not be guessed solely from OS and
architecture. This descriptor should participate in AOT identity and native
artifact selection where relevant.

### Acceptance criteria

- Descriptor values agree with a small compiler/native probe on Linux x86_64,
  Windows x86_64, and macOS arm64.
- Container/host CPU-count behavior is documented and tested.
- The same descriptor selects FFI ABI, cache target, and native artifact.
- Unsupported combinations fail with the observed target facts.

### jolt-hegel payoff

`hegel.native/platform`, installer architecture probing, and separator
heuristics can shrink to a release-name mapping over one upstream target value.
Artifact declarations and by-value aggregate support share the same tested ABI
identity.

## Changes which do not currently require upstream work

Several consumer pain points are library-level and should not be blocked on the
items above:

- scalar octets, stream chunkings, and the framework-less counting runner now
  belong in jolt-hegel's own API;
- richer exception and `:observed-failures` reporting remains a wrapper concern;
- libhegel's output callback can be bound with the existing callback support in
  `jolt.ffi`; and
- stateful tests against a shared external server are a documentation and
  isolation-pattern concern.

Concurrent direct `run-test!` calls are not, by themselves, an upstream defect.
libhegel 0.30.1 changed its run threading model, and Jolt already has
collect-safe foreign calls, but shim, engine, callback, and cleanup safety need
a focused jolt-hegel integration test. If that test reproduces runtime
corruption, reduce it to a standalone concurrent-FFI program with the exact
Jolt SHA, target, signature, collect-safe mode, concurrency, and failure before
assigning it upstream. That P1 safety work is independent of any proposal for
green/virtual threads. Adaptive generation and shrinking within one property
should remain sequential unless libhegel itself introduces a
semantics-preserving concurrency model.

## Recommended implementation order

1. Add a complete target descriptor so artifact and ABI work share one target
   identity.
2. Add narrow/scoped FFI helpers, then by-value aggregate support and remove the
   shim/release assets.
3. Finish platform-correct process launching and prove replace-existing writes
   on Windows as independent Jolt changes.
4. Add typed libhegel run errors before expanding flaky diagnostics.
5. Design native artifact acquisition after the shim is gone, so the shared
   mechanism only has to solve acquisition of the real dependency.
6. Add the seed getter opportunistically; it should not block the
   higher-impact correctness and maintenance work.

The AOT identity and bindable-report proposals are complete in Jolt 0.7.5 and
are no longer part of this queue. Streaming SHA-256 can be consumed from
`jolt-crypto` without another core patch.
