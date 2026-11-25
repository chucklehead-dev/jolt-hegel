# libhegel 0.51.6 migration

This historical migration used `hegeldev/hegel` tag `libhegel/0.51.6`, commit
`a6928220ae21e2658994a9246b93f84f21557447`. The complete upstream header,
provenance and independent SHA-256 live in `test/fixtures/hegel-0.51.6/`.
Earlier header fixtures remain historical evidence.

## Runtime and dependencies

Jolt 0.8.27 and Babashka 1.13.229 are the primary qualified releases. Jolt
0.8.1 remains the compatibility floor. JVM Clojure now requires JDK 25+ and
`org.babashka/ffi {:mvn/version "0.1.3"}`, with native access enabled.
Malli 0.20.3 remains optional; Typed Clojure 1.4.0 is development-only.
Experimental CLR uses ClojureCLR 1.12.6 on .NET 8. The separate experimental
join-point compiler remains on its existing feature-branch commit; it is not
interchangeable with the official Jolt release.

## Native interface

The descriptor covers all 159 functions, 18 opaque handles and six structures.
Strings use explicit UTF-8 byte lengths, including nullable pointers to views
and owned outputs. Embedded NUL is supported by regex patterns. Context error
messages return a structure by value. Every adapter normalizes that return to
a caller-owned destination argument before the native arguments.

State machines are constructed, populated with named rules and invariants,
then started. The public step count is applied per machine rather than through
the removed settings setter. Sequential rules share group zero and default to weight
1.0 (`:weight` can override it); invariants are checked every step. Failed construction frees the machine.
Span labels use stable names hashed by native Hegel rather than retired enum
values. `:auto` remains an alias for the native default backend.

## Capture, replay and nondeterminism

Hegel now drives capture and confirmation through the run protocol. `final?`
means that the current case requests diagnostic capture; it can be true more
than once during shrinking. Do not use it to change property behavior or assume
its first invocation is the minimum. Result `:final` and `:counterexample`
select the retained capture per failure origin. clojure.test emits assertion
reports from that selected failure, avoiding duplicate confirmation reports.

`:stop-on-first-failure? true` ends exploration at the first failure before
shrinking. `:nondeterminism-strictness` accepts `:quiet`, `:warn` or `:error`;
the default is `:error`, retaining the frontend's previous strict policy.
Native typed error kinds identify nondeterminism, and native failure caveats
are retained and mark a run flaky. Replay options preserve these settings.
A property that rejects its only input without drawing data now reports the
native Unsatisfiable error instead of a passing run with one rejected case.

Saved-failure replay uses `hegel_run_start_blob` and follows its capture loop,
with persistence disabled. Native blobs and databases are version-specific.
Do not rewrite old bundle provenance or feed 0.36.x artifacts to 0.51.6: retain
the old deployment to inspect them or regenerate failures with the new engine.
Seeds alone do not promise identical examples across engine upgrades.

## Additional adoption

See [the adoption report](UPSTREAM_ADOPTION.md) for packaging, profiles, generator
caching and grammar generation, component labels, discarded pool consumption,
Antithesis integration, inherited engine fixes and deliberate deferrals.
Unrecorded single-case Antithesis failures retain their observed exception with
`:unrecorded? true`, `:reproduced? false` and no replay blob; absence of replay
in that mode does not itself mean nondeterminism.

## Validation

Run `bb header-audit`, `bb abi-test`, both code-generation checks, and the shared
semantic suites on Jolt, FFI-capable Babashka and JDK 25. The focused upgrade
suite covers the step-51 stateful replay, builder cleanup, native label hashes,
stop-on-first-failure, Unicode/NUL regex patterns and aggregate error returns.
CLR has native bridge and shared semantic smoke gates. Hosted CI remains the
authority for Windows, macOS and the experimental jank runtime.
