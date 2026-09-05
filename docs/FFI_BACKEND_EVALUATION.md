# FFI backend evaluation (#60)

Scope: whether Jolt's `jolt.ffi` and upstream `babashka.ffi` (shared by Babashka
and the JVM) can each express the canonical libhegel ABI in
`resources/hegel/abi.edn`, and whether their differences justify a shared
abstraction layer or a shared native-memory arena strategy. `abi.edn` remains
the sole ABI source (`:schema-version 1`); both adapters translate it, they
never restate it.

## Canonical surface (measured)

`test/hegel/signature_policy_test.cljc` derives every fact below from
`hegel.abi/functions` and `hegel.abi/types`. The fixed counts are non-vacuity
and drift controls, not a second signature definition. Citations below name
`deftest`s rather than line numbers, which drift independently of the facts
they support as the file is edited:

- 103 functions, 34 types, and the type-kind census -- 1 void, 1 boolean, 9
  integer, 2 float, 1 string, 13 opaque, 6 struct, 1 function-pointer --
  asserted in `the-canonical-abi-is-the-single-definition-of-the-native-surface`.
- 365 argument forms (277 `:pointer`, 70 scalar, 10 `:c/string`, 6
  `:by-value`, 2 `:callback`), 103 return forms (101 scalar, 1 pointer, 1
  string; zero `:by-value` or aggregate returns), all 6 by-value aggregate
  arguments being date/time bounds (2 each on `:generate-date`,
  `:generate-time`, `:generate-datetime`), and the one callback type
  `:hegel/output-callback` being used by exactly two functions (`:run-start`
  and `:test-case-from-blob`) -- all asserted in
  `canonical-argument-and-return-forms-have-an-exact-census`.

## Adapter comparison (`src/hegel/ffi/jolt.clj`, `src/hegel/ffi/babashka.clj`)

Both adapters walk the same canonical forms (`native-type`/`scalar-type`) but
diverge in two places, asserted side by side in the `carrier-policy` data and
the `the-two-target-policies-differ-only-where-intended` deftest:

1. **32-bit integer carrier names.** Jolt maps `:c/int32`/`:c/uint32` to
   `:int`/`:uint` (`jolt.clj:15-16`); babashka.ffi maps them to
   `:int32`/`:uint32` (`babashka.clj:22-23`). This is the only scalar delta,
   and it propagates into every struct field of that width: `:hegel/date`
   (via `:year`) and `:hegel/time` (via `:nanosecond`) get different derived
   layouts on the two targets, and `:hegel/datetime` inherits *both* deltas
   because it embeds both a `:date` and a `:time` field, while
   `:hegel/bytes-result`, `:hegel/string-result`, and
   `:hegel/printer-value-result` (pointer/`size_t` fields only) stay
   identical (asserted in `the-two-target-policies-differ-only-where-intended`).
2. **By-value call convention.** Jolt tags a by-value aggregate argument as
   `[:by-value layout]` and the caller passes a storage pointer
   (`jolt.clj:39`, `by-value` at `jolt.clj:138` returns the pointer
   unchanged). Upstream babashka.ffi takes the bare layout directly and the
   caller supplies a field map (`babashka.clj:50`; `by-value` at
   `babashka.clj:277-281` reads native memory into that map). These are
   asserted as genuinely different call conventions, not a naming variant, in
   the `by-value-form` helper and its callers.

Pointers, all 13 opaque handles, and the one function-pointer type collapse to
a single `:pointer` on both targets (`jolt.clj:37-38,50`;
`babashka.clj:47,60`; asserted in
`every-declared-type-id-translates-under-every-canonical-form`).
Bare opaque handles and unrepresentable forms (`[:array :c/uint8]`,
`:c/int128`, an undeclared type-id) are rejected by both adapters, asserted in
`bare-opaque-handles-and-unrepresentable-forms-are-rejected`.

### Decision: keep two small adapters, reject a shared walker for now

A shared, parameterized scalar-mapping/struct-walking helper is viable: the
two adapters' `native-type`/`scalar-type`/`signature` bodies are structurally
close enough that parameterizing the carrier-name table and the by-value
wrapper rule could plausibly remove on the order of 40 duplicated lines
across `jolt.clj` and `babashka.clj`. It is rejected for now on cost/benefit
grounds, not because it is infeasible: the duplication it would remove is
small and has stayed stable since both adapters were written, while the two
real deltas it would have to parameterize -- the int32/uint32 carrier names
and the by-value convention (below) -- are genuinely different target
conventions rather than incidental copy-paste. Centralizing them behind one
shared helper would add a layer of indirection and couple both adapters to
that helper's parameterization surface, with no demonstrated correctness
benefit over the current two independently readable translation sections.
Reconsider this decision if a third backend is added or if the two adapters'
translation logic drifts apart in a way the current small duplication no
longer tolerates.

### Jolt binding macros are not a runtime binder

`jolt.ffi/foreign-fn` and `jolt.ffi/layout` must see literal argument-type and
symbol forms; `hegel.ffi.jolt` builds an s-expression per function and
`eval`s it (`jolt.clj:81-108`, `make-foreign-function`, and `layout` at
`jolt.clj:55-57`). Upstream babashka.ffi's `cfn`, by contrast, is an ordinary
function called at runtime with computed `args`/`return` values
(`babashka.clj:143`, `make-binding`). This asymmetry is why Jolt cannot adopt
a drop-in runtime function binder shared with babashka.ffi: per-function
generation via `eval` is retained deliberately, not as unfinished work.

## Arena decision: reject per-wrapper adoption

A per-wrapper confined-arena allocation strategy (mirroring
`hegel.ffi.babashka`'s `with-native-scope`/`ffi/confined-arena`,
`babashka.clj:216-219`) was evaluated for Jolt and rejected for now.

**Measurement.** A direct released-Jolt 0.8.1 benchmark at source
`51f10a0239096f804a6017f850b7b235ebe40168` on Linux x86_64 with threaded
Chez 10.4.1 measured 30,000 8-byte allocate/write/read/release
operations, 5 samples: raw `alloc`/`free` median 55.98ms; a fresh arena per
operation, 487.11ms median (8.70x raw); a single arena batching 100 operations
per open/close cycle, 190.83ms median (3.41x raw). In the separate Hegel
prototype, temporary roundtrips changed from 45.40ms to 541.28ms and integer
draws from 59.08ms to 276.53ms. These are baseline-to-arena median changes,
not ranges. This is diagnostic evidence from one Linux release build only —
it is not a portable threshold, not validated against current upstream Jolt,
and not a profiled root-cause proof.

**Inference (source-grounded, not measured directly).** Reading the observed
multiplier pattern against Jolt's arena implementation: each arena groups
separate `calloc` blocks rather than allocating one contiguous slab, so batching
retains per-block allocation/registration/release work instead of replacing it
with a bump allocator. That is consistent with a large fresh-arena cost and a
remaining batched gap; it does not establish which bookkeeping phase dominates
or that bookkeeping is the sole cause.

**Decision.** Preserve `hegel.ffi.jolt`'s current explicit temp-pointer
ownership (`alloc`/`free` pairs, `jolt.clj:120-121`) rather than introducing a
per-wrapper arena. Constraints carried forward regardless of arena strategy:
no `ByteBuffer` or other native handle may escape a wrapper's dynamic extent,
there is no public zero-copy view of native memory, and a freed pointer must
never be dereferenced.

**Follow-up.** Upstream tracking issue:
[jolt-aspect-packs #100](https://github.com/chucklehead-dev/jolt-aspect-packs/issues/100).
After the released-build measurement above, that issue reproduced the result
against then-current Jolt `023285d283493fbfe121db58246118d57d7ed3e4`:
fresh confined arenas measured 8.54x raw allocation and batches of 100 measured
3.38x raw. Its phase decomposition points to close/bookkeeping as the dominant
diagnostic cost and records precommitted source/AOT improvement gates before an
upstream implementation. This newer evidence reinforces the decision not to
adopt arenas in Hegel yet; it does not turn an unmerged optimization hypothesis
into a supported runtime capability.

## Local test evidence (honest state)

`test/hegel/signature_policy_test.cljc` currently passes locally: 9
`deftest`s and 687 assertions on each of Babashka, the JVM, and Jolt via the host reader
conditionals in the namespace's `:require` clause (`:jolt`/`:bb`/`:clj`). The
same count on every host proves that no target silently skips the private
signature-composition seam. It is now wired into the aggregate scenario suite
as the `:ffi-signature-policy` entry in `test/hegel/scenario_manifest.clj`,
invoked through `hegel.suites.ffi/signature-policy-contract`, alongside the
suite's other selected scenarios (`ffi-nullable`, `ffi-adapter`,
`ffi-write-order`). The selected FFI suite and the complete aggregate both
pass locally on BB, JVM and Jolt with the verified Linux native asset. Hosted
CI has not yet run this addition; these are local results, not a CI signal.

Existing tests already exercise aggregate arguments and exceptional cleanup
independent of this new test:

- **Aggregate arguments (direct by-value bindings):**
  `hegel.suites.generators/temporal-generators` draws `g/date`, `g/time`, and
  `g/datetime` values, including fixed/minimum/maximum bound round-trips
  through `hegel_date_t`/`hegel_time_t`/`hegel_datetime_t`, and a shrinking
  run that reproduces a minimal leap-day counterexample.
- **Exceptional cleanup, ownership-precedence:**
  `test/hegel/generator_cleanup_test.clj` (run via
  `hegel.suites.generators/generator-cleanup-contract`) asserts that a body error
  always wins over a subsequent close/free error across span, filter,
  collection, string-generator, and recursive-generator cleanup paths (e.g.
  `span-cleanup-preserves-the-original-body-error`,
  `filter-and-collection-cleanup-retain-generator-errors`).
  `test/hegel/big_integer_ffi_test.clj` exercises the same precedence and
  full-reverse-order release for a multi-allocation call
  (`cleanup-preserves-primary-error-and-never-frees-twice`,
  `partial-allocation-and-write-failures-release-only-owned-storage`).

## Acceptance matrix

| Item | Result |
|---|---|
| Canonical EDN is sole ABI source | Met — both adapters derive from `abi.edn`; census cited above |
| Both backends express every function/type or reject explicitly | Met — `capability`/`check-signature` in `jolt.clj:63-73` and `babashka.clj:83-97`; rejects for opaque-by-value and unrepresentable forms asserted in `bare-opaque-handles-and-unrepresentable-forms-are-rejected` |
| Int32/uint32 carrier and by-value convention differences documented | Met — see Adapter comparison above |
| Shared walker/scalar-helper abstraction | Rejected — differences are material, not incidental (see Decision) |
| Jolt runtime function binder shared with babashka.ffi | Rejected/unsupported — Jolt's `foreign-fn` needs literal forms; Jolt uses macro-style `eval` generation while babashka.ffi's `cfn` is a runtime function |
| Per-wrapper arena adoption for Jolt | Rejected — 3.4x–8.7x overhead measured locally; explicit alloc/free retained; follow-up filed |
| New signature-policy test passes on all three supported hosts | Met locally (9 `deftest`s / 687 assertions on each; selected FFI suite and full aggregate on each); not yet run in CI |
| Existing aggregate-argument and cleanup-precedence coverage preserved | Met — cited tests above, unmodified by this work |
| Aggregate/callback/pointer census stability | Met — asserted by fixed-count tests, guards against silent drift |
| jank/CLR backend qualification | Out of scope — both are experimental hosts (`docs/JANK.md`, `docs/CLR.md`) not qualified by this signature-policy test |

## Not qualified by this work

The experimental jank and ClojureCLR hosts (`docs/JANK.md`, `docs/CLR.md`)
are outside the supported Jolt/Babashka/JVM matrix that
`signature_policy_test.cljc` targets. Neither host's adapter is exercised by
this test. The FFI scenario loads it only on supported hosts and records an
explicit unqualified experimental-host control rather than trying to load the
wrong adapter. CLR's own docs already note that `g/big-integer`/`g/float32`
and full shrinking/replay parity are not yet qualified there — this
evaluation does not change or extend that boundary.
