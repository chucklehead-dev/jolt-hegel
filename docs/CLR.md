# Experimental ClojureCLR host

ClojureCLR support is an active portability experiment, separate from the
supported Jolt, Babashka, and JVM release matrix. Linux x86_64, Windows x86_64,
and macOS arm64 CI run the shared property API against the official libhegel
0.33 native artifacts. Ordinary Hegel code does not call a CLR-specific API.

The port uses a small AnyCPU .NET 8 bridge because ClojureCLR cannot declare
arbitrary P/Invoke methods dynamically. Its structs, P/Invoke declarations,
function invokers, and expected export list are generated from
`resources/hegel/abi.edn`; there is no second hand-maintained C signature list
and no native C shim.

## What works

- all 71 libhegel symbols are resolved from the exact selected library;
- fixed-width signed and unsigned integers, `size_t`, floating point, pointers,
  strings, out parameters, bulk bytes, and wide mixed signatures;
- `date`, `time`, and nested `datetime` structs passed by value;
- the shared property runner, assumptions, deterministic seeds, shrinking,
  stable failure origins, final replay, and reproduction blobs;
- primitive, Unicode, regex, formatted, temporal, and collection generators;
- shared stateful/swarm execution, reusable and consumed pools, reporting, and
  `clojure.test` integration; and
- the common checksum-verified native installer.

`hegel.abi/backend-report` reports `:clr/generated-pinvoke` for each binding.
The managed smoke program separately checks aggregate layout, export coverage,
memory operations, wide floating-point calls, and temporal by-value calls
before the Clojure property smoke runs.

## Generating and building the bridge

The repository currently targets ClojureCLR 1.12.2 and .NET 8. From a checkout
with Babashka and the .NET SDK installed:

```bash
bb clr-codegen-check
dotnet build clr/Hegel.Native/Hegel.Native.csproj --configuration Release
dotnet build clr/Hegel.Native.Smoke/Hegel.Native.Smoke.csproj --configuration Release
```

After changing the canonical ABI, regenerate and review the managed artifact:

```bash
bb clr-codegen
git diff -- clr/Hegel.Native/GeneratedBindings.cs
bb clr-codegen-check
```

Never edit `GeneratedBindings.cs` directly. `Bridge.cs` is the small stable
library-loading and native-memory implementation around the generated calls.

## Running the experiment

Set ClojureCLR's source path using the platform path separator and point the
backend at the built bridge. Then use the shared installer and semantic smoke:

```bash
export CLOJURE_LOAD_PATH="$PWD/src:$PWD/resources:$PWD/script"
export HEGEL_CLR_BRIDGE_ASSEMBLY="$PWD/clr/Hegel.Native/bin/Release/net8.0/Hegel.Native.dll"

dotnet /path/to/Clojure.Main.dll -m hegel.install setup
export HEGEL_LIBHEGEL_LIBRARY="$PWD/.hegel-lib/libhegel_c.so"

dotnet run --project clr/Hegel.Native.Smoke/Hegel.Native.Smoke.csproj \
  --configuration Release --no-build -- "$HEGEL_LIBHEGEL_LIBRARY"
dotnet /path/to/Clojure.Main.dll -m hegel.clr-property-smoke
```

Use `;` in `CLOJURE_LOAD_PATH` and `libhegel_c.dll` on Windows; use
`libhegel_c.dylib` on macOS. Hosted CI downloads the pinned ClojureCLR NuGet
artifact and verifies its SHA-256 before use, so the three operating-system
jobs exercise the same runtime bits.

## Ownership and deployment

The CLR backend retains the shared explicit lifetime rules. Unmanaged buffers
are allocated with `NativeMemory.Alloc` and released by lexical `finally`
paths; native strings and result bytes are copied before their owners are
released. The bridge holds the selected libhegel library for process lifetime
because bindings are constructed once and reused.

The bridge is AnyCPU and uses `LibraryImport`-generated marshalling. That makes
the bridge itself compatible with .NET trimming and NativeAOT constraints, but
it does not make the full ClojureCLR runtime NativeAOT-compatible: current
ClojureCLR relies on runtime code generation. This experiment therefore makes
no full-application NativeAOT claim.

## Remaining release gates

- package the managed bridge and Clojure sources into a practical consumer
  dependency instead of requiring a repository checkout;
- move from the focused CLR semantic smoke to the complete shared suite;
- validate optional Malli only after a supported ClojureCLR dependency path is
  available; and
- define supported ClojureCLR/.NET version policy and release artifacts.

Until those gates exist, do not present ClojureCLR as a supported release host.
