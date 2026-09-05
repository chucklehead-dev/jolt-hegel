# Pinned C-header audit

Run `bb header-audit` or `clojure -M:header-audit` from the repository root.
These development-only commands do not load libhegel. They require a C11
compiler using cc/clang-style flags; `HEGEL_ABI_CC` selects its executable
(default `cc`). Windows CI uses `clang`, not MSVC command-line syntax.

The complete pinned header and provenance are under
`test/fixtures/hegel-0.33.3/`. The audit checks its SHA-256 before parsing,
rejects unsupported declarations, and compares all exported signatures,
callback signatures, opaque handles and ordered struct fields against
`resources/hegel/abi.edn`. The EDN remains the backend/code-generation input;
the header snapshot is independent test evidence, not another binding source.

A generated C11 program measures primitive widths/alignment, enum storage,
every concrete struct's size/alignment/field offsets, and compiler-evaluated
constants. The audit compares these with canonical layouts and parsed values.
Literal wrapper status, span and option constants are read as source data,
without evaluating their namespaces. Temporal fields additionally declare
their unit and domain; changing microseconds to nanoseconds cannot pass merely
because both use a 32-bit field. Unsupported source/header shapes fail closed
and require a reviewed audit update.

Negative controls cover missing/replaced functions, argument order/type,
pointer depth, callbacks, scalar widths, same-sized field changes, nested
layouts, enum values, temporal units, malformed declarations and compiler
failure. Compiler source and logs remain in unique `target/header-layout-*`
directories for diagnosis. The CI matrix measures each selected target; a
Linux result alone does not establish Windows or macOS layouts.

This gate does not prove native calling conventions, lifetime ownership,
thread safety, or arbitrary C preprocessing. Keep `abi-test`, generated-output
checks, and the real BB/JVM/Jolt native suites. Regenerate with
`bb jank-codegen` and `bb clr-codegen` after changing canonical EDN, and review
both generated differences and unchanged native behavior.
