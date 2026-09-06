# Experimental ClojureCLR host

ClojureCLR support is an active portability experiment, separate from the
supported Jolt, Babashka, and JVM release matrix. Linux x86_64, Windows x86_64,
and macOS arm64 CI run the shared property API against the official libhegel
0.36.3 native artifacts. Ordinary Hegel code does not call a CLR-specific API.

The implementation is merged on `main`. Hosted CI pins ClojureCLR 1.12.2 and
.NET 8, verifies the ClojureCLR NuGet package checksum, and runs both the
independent managed ABI smoke and focused shared-semantics smoke on all three
operating systems. This is continuous experimental evidence, not a supported
consumer-release promise.

The port uses a small AnyCPU .NET 8 bridge because ClojureCLR cannot declare
arbitrary P/Invoke methods dynamically. Its structs, P/Invoke declarations,
function invokers, and expected export list are generated from
`resources/hegel/abi.edn`; there is no second hand-maintained C signature list
and no native C shim.

## What works

The new `g/big-integer` and `g/float32` domains are qualified on the supported
BB/JVM/Jolt matrix only. This experimental host's symbol/ABI and focused semantic
smokes do not yet qualify arbitrary-precision values, exact binary32 bounds,
or their complete shrinking/replay contracts. Do not assume parity from loading
the shared namespace successfully.

- all 103 libhegel symbols are resolved from the exact selected library;
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

## Packaging and clean consumer contract

The generated `Hegel.Native` bridge can be packed with the project Clojure
sources and resources for an experimental ClojureCLR consumer. The package does
not include `libhegel`; continue to use `hegel.install` (or an explicit
checksum-verified `HEGEL_LIBHEGEL_LIBRARY`) for the native library.

From a checkout, build a local feed artifact:

```bash
dotnet pack clr/Hegel.Native/Hegel.Native.csproj --configuration Release \
  --output ./artifacts -p:PackageVersion=0.0.0-local
```

The package ID is `ChuckleheadDev.JoltHegel.CLR`. A .NET consumer references it
normally, then runs ClojureCLR with `CLOJURE_LOAD_PATH` set only to its build
output directory. The package copies `hegel` source/resources and
`Hegel.Native.dll` there, so the CLR backend discovers the bridge without a
repository checkout:

```xml
<PackageReference Include="ChuckleheadDev.JoltHegel.CLR" Version="0.0.0-local" />
```

```bash
dotnet restore path/to/consumer.csproj --source ./artifacts
dotnet build path/to/consumer.csproj --configuration Release --no-restore
export CLOJURE_LOAD_PATH="$PWD/path/to/consumer/bin/Release/net8.0"
unset HEGEL_CLR_BRIDGE_ASSEMBLY
dotnet /path/to/Clojure.Main.dll -m your.consumer.namespace
```

PowerShell uses the same output-only contract:

```powershell
$env:CLOJURE_LOAD_PATH = (Resolve-Path path/to/consumer/bin/Release/net8.0).Path
Remove-Item Env:HEGEL_CLR_BRIDGE_ASSEMBLY -ErrorAction SilentlyContinue
dotnet C:\path\to\Clojure.Main.dll -m your.consumer.namespace
```

`HEGEL_CLR_BRIDGE_ASSEMBLY` remains an explicit override for checkout and
diagnostic use. Without it, the backend accepts only an `Hegel.Native.dll`
beside a configured `CLOJURE_LOAD_PATH` root; it never searches the repository
or a global checkout. Hosted CLR CI first removes the packaged bridge and
requires that lookup to fail, then restores it and runs a real property from a
local-feed-only consumer. This is package-layout evidence, not a supported
release or public-feed publication.

## Running the checkout experiment

For a checkout, set ClojureCLR's source path using the platform path separator
and point the backend at the built bridge. Then use the shared installer and
semantic smoke:

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

The bridge is AnyCPU and uses `LibraryImport`-generated marshalling, an
AOT-friendly design. Current CI runs it on the managed .NET runtime; it does not
publish or validate a NativeAOT bridge artifact. More importantly, current
ClojureCLR relies on runtime code generation, so the full ClojureCLR application
is not a NativeAOT target. This experiment makes no full-application NativeAOT
claim.

## Remaining release gates

- package the managed bridge and Clojure sources into a practical consumer
  dependency instead of requiring a repository checkout; the experimental
  local-feed package contract exists, but a released consumer artifact remains
  to be defined and verified;
- move from the focused CLR semantic smoke to the complete shared suite;
- validate optional Malli only after a supported ClojureCLR dependency path is
  available;
- define the supported ClojureCLR and .NET version policy; and
- define and verify release artifacts and a clean consumer installation.

Until those gates exist, do not present ClojureCLR as a supported release host.
