# The libhegel ABI descriptor

`resources/hegel/abi.edn` is the single source of truth for the native interface
used by jolt-hegel. It is plain, inspectable EDN: loading or validating it does
not load native code.

The top-level map records a schema version, the pinned libhegel release and
authoritative header, named types, and named functions:

```clojure
{:schema-version 1
 :library {:name "libhegel"
           :version "0.33.0"
           :header {:repository "hegeldev/hegel-rust"
                    :tag "v0.33.0"
                    :commit "..."
                    :path "hegel-c/include/hegel.h"}}
 :types {...}
 :functions {...}}
```

## Type model

Fixed-width integer entries specify `:bits` and `:signed?`. `:c/size` uses
`:pointer-width`. The model also represents floating-point values, NUL-terminated
UTF-8 strings, opaque handles, function pointers, ordered structs, pointers,
and explicit by-value use.

```clojure
:hegel/time
{:kind :struct
 :fields [{:name :hour :type :c/uint8}
          {:name :minute :type :c/uint8}
          {:name :second :type :c/uint8}
          {:name :microsecond :type :c/uint32}]}

:hegel/datetime
{:kind :struct
 :fields [{:name :date :type :hegel/date}
          {:name :time :type :hegel/time}]}
```

Pointers and by-value use are type forms, not backend vocabulary:

```clojure
[:pointer :hegel/context]
[:pointer [:pointer :hegel/test-case]]
[:by-value :hegel/datetime]
```

Backends are responsible for the target C ABI's alignment, padding, and
aggregate classification. No offsets or register rules belong in the EDN.

## Function model

A function has a logical keyword, exported symbol, ordered argument vector,
and return type. Optional metadata describes behavior that affects binding or
lifetime:

```clojure
:context-new
{:symbol "hegel_context_new"
 :args []
 :return [:pointer :hegel/context]
 :ownership {:return {:kind :owned :release :context-free}}}

:next-test-case
{:symbol "hegel_next_test_case"
 :args [[:pointer :hegel/context]
        [:pointer :hegel/run]
        [:pointer [:pointer :hegel/test-case]]]
 :return :c/int32
 :blocking? true}

:generate-date
{:symbol "hegel_generate_date"
 :args [[:pointer :hegel/context]
        [:pointer :hegel/test-case]
        [:by-value :hegel/date]
        [:by-value :hegel/date]
        [:pointer :hegel/date]]
 :return :c/int32}
```

Ownership metadata is documentation and validation data for the shared wrapper;
it does not replace the lexical release paths in `hegel.ffi`.

## Inspection and coverage

```clojure
(require '[hegel.abi :as abi])

(abi/descriptor)
(abi/types)
(abi/functions)
(abi/validate!)
```

`validate!` checks the schema version, type references, integer semantics,
ordered and acyclic struct definitions, function signatures, symbol uniqueness,
and metadata shape.

Each runtime backend exposes a pure signature capability check. The shared
checker returns complete, structured coverage instead of waiting for an
unsupported call to fail during a property:

```clojure
(abi/check-backend backend (abi/descriptor))
;; => {:backend :jvm
;;     :supported? true
;;     :summary {:supported 71 :unsupported 0 :total 71}
;;     :functions {:generate-date
;;                 {:status :supported :route :jvm/ffm}
;;                 ...}}
```

After native binding construction, `(abi/backend-report)` returns the report
registered by the selected runtime. Current route names are:

| Route | Meaning |
| --- | --- |
| `:jolt/direct` | direct `jolt.ffi` binding |
| `:bb/trampoline` | Babashka's compiled fixed-signature fast path |
| `:bb/libffi` | Babashka's general fixed-signature libffi path |
| `:jvm/ffm` | cached JDK FFM downcall handle |
| `:jank/generated` | generated jank C++ interop downcall |

The report is intended for CI, diagnostics, and ABI upgrades. Application code
should not branch on the route.

## Backend construction

All three backends consume the function map; there are no hand-maintained
per-host symbol lists.

The Jolt backend translates type forms to `jolt.ffi` descriptions and builds a
foreign function once. The Babashka backend translates to `babashka.ffi`, which
selects its trampoline or libffi path per signature. The JVM backend builds a
`FunctionDescriptor`, resolves the symbol through the selected library's
`SymbolLookup`, and caches its downcall `MethodHandle`.

Native memory operations deliberately live outside the EDN. They implement the
small boundary needed by the common wrapper: allocation and release, scalar
and field access, byte ranges, and UTF-8 conversion.

## Adding or updating a binding

1. Pin the intended libhegel release and authoritative header commit.
2. Add or update types and the function in `resources/hegel/abi.edn`. Express
   C semantics, never a Jolt, Babashka, or FFM spelling.
3. Run the native-code-free ABI validation and header drift checks.
4. Run backend coverage on every host. An unsupported entry must be visible in
   the report before native calls begin.
5. Add one checked common wrapper in `hegel.ffi` with explicit ownership. Do
   not call a host backend from generator, core, or stateful code.
6. Add backend-neutral semantic tests, including boundary widths and ownership
   cleanup. Add aggregate layout cases when a struct changes.
7. Run the property parity suite on Jolt, Babashka, and JVM Clojure across the
   platform matrix.

If Babashka cannot express a new signature, first determine whether the gap is
general. Improvements belong in `babashka.ffi` with independent tests and
documentation; jolt-hegel symbols must never be special-cased in Babashka.

## ABI safety checklist

- Do signed and unsigned widths match the header exactly?
- Is `size_t` represented as pointer-width unsigned data?
- Are nested structs laid out by the backend rather than manually packed?
- Is by-value use explicit at the argument or return site?
- Are output parameters allocated at the correct size and alignment?
- Are strings copied before releasing their native owner?
- Is every owned handle paired with the descriptor's release function?
- Are potentially blocking calls annotated?
- Does symbol lookup use the selected library rather than an unrelated global
  symbol of the same name?
