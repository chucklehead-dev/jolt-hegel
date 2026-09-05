# Typed Clojure pilot (dev-only, bounded)

This is a small, opt-in pilot of [Typed Clojure](https://typedclojure.org)
(coordinate `org.typedclojure/typed.clj.checker` `1.3.0`, entry point
`typed.clojure/check-ns-clj`) over one unchanged namespace:
`src/hegel/internal/portable_data.cljc`. It exists to answer "is external
static typing worth adopting here", not to ship a typed runtime. Nothing in
`:paths` changes, and no runtime behavior changes.

Everything lives behind the optional `:typed-check` alias
(`clojure -M:typed-check`, `:extra-paths ["script" "typed"]`,
`:extra-deps {org.typedclojure/typed.clj.checker {:mvn/version "1.3.0"}}`).
Ordinary consumers of jolt-hegel — including every other host and every
existing alias — never resolve or load the checker or its dependency; it is
JVM-only, dev-only.

## Layout

- `typed/hegel/typed/portable_data.clj` — external `t/ann`/`t/defalias`
  annotations for `hegel.internal.portable-data`'s public vars, keyed by their
  fully-qualified symbol. It does not redefine or `in-ns` into the annotated
  namespace; `src/hegel/internal/portable_data.cljc` is unchanged. `validate!`
  is annotated `^:no-check` — see "Checked vs. trusted boundary" below.
- `typed/hegel/typed/driver.clj` — a small, fully-annotated valid consumer,
  checked alongside `text-size`'s body and `validate!`'s declared contract so
  the pilot always exercises a realistic passing call site.
- `typed/hegel/typed/controls/*.clj` — four intentionally invalid consumer
  namespaces (mutants), each expected to be rejected.
- `script/hegel/typed_check.clj` (`hegel.typed-check`) — the runner
  (`clojure -M:typed-check`): checks the annotation namespace, `text-size`'s
  body, `validate!`'s call-site contract, and the driver with zero errors;
  checks that every named mutant is rejected; and asserts the exact count.

## Checked vs. trusted boundary

An actual `clojure -M:typed-check` run (checker 1.3.0, JDK 25) against an
earlier version of this pilot reported exactly two errors when checking
`hegel.internal.portable-data`: an unannotated `clojure.string/includes?`
call inside the private `consume-text!` helper, and a demand for inline type
annotations inside `validate!`'s iterative `loop`. Both are consequences of
`validate!`'s unchanged production body — an iterative loop over private,
unannotated helpers — not of anything wrong with the external annotations
themselves; the same run confirmed the external annotations do register and
do get consulted at checked call sites. Editing `src` to add inline
annotations, or fabricating types for helpers this pilot never claimed to
check, are both out of scope, so the pilot is constrained instead:

- **Fully checked**, body and all: `text-size`, the one public var whose
  entire implementation is straight-line code the checker can verify against
  its `t/ann` with no loop or private-helper involvement.
- **Contract-checked, body trusted (not checked)**: `validate!`. Its `t/ann`
  is marked `^:no-check` (see `typed/hegel/typed/portable_data.clj`), so its
  declared polymorphic signature — `(t/All [x] [x Limits InvalidFn -> x])` —
  is still enforced against every call site the checker examines: the valid
  driver (`typed/hegel/typed/driver.clj`) and all four mutant controls below.
  What is *not* checked is whether `validate!`'s own implementation actually
  satisfies that signature; `^:no-check` tells the checker to trust the
  annotation rather than verify it against the loop body. That loop, and the
  private helpers it calls (`consume-node!`, `consume-text!`, `record-value?`,
  `finite-floating?`, `portable-scalar?`), are the parts of `validate!` this
  pilot cannot check without editing `src`.
- The checker is separately configured (`production-check-config` in
  `script/hegel/typed_check.clj`, applied only to
  `hegel.internal.portable-data`) not to error on those unannotated private
  helpers themselves. Because the checker still resolves calls inside those
  forms, the dev-only annotation namespace also supplies a narrow two-string
  signature for `clojure.string/includes?`. That configuration is scoped to
  this one namespace; the driver and mutant controls keep strict defaults.
- Every other jolt-hegel namespace — including `hegel.corpus`, which is the
  main real caller of `portable-data/validate!` — remains completely
  untyped and untouched by this pilot. Its correctness still rests entirely
  on `test/`, not on the checker.
- Runtime enforcement is unchanged and remains the actual safety mechanism for
  *all* of `validate!`, checked contract or not: `invalid!` must still throw
  at runtime exactly as before, and the loop's actual behavior — including
  every private helper's behavior — is verified only by `test/`, never by the
  checker. The checker does not (and cannot) verify that a caller's
  `invalid!` implementation actually throws, only that it has the right
  static shape (`Path`, `Reason` -> bottom).

## JVM `:clj` reader branch only

`portable_data.cljc` has one `#?(:jank ...)` branch (in `record-value?`).
Typed Clojure's JVM checker (`typed.clj.checker`) type-checks the file as
ordinary `.clj`-flavored Clojure, i.e. it reads and checks the `:clj` (or
`:default`) reader-conditional branch only, on JDK 25. It never reads, and
therefore never checks, the `:jank` branch or any other host's branch. Those
remain entirely untouched by this pilot and are only exercised by the
existing per-host test suites in `test/`.

## Actual checking vs. annotation loading vs. compiler hints vs. runtime validation

These are four different things, easy to conflate:

1. **Runtime validation** — `validate!`/`invalid!` executing at actual
   program run time. Unaffected by this pilot; still the only thing that
   protects production callers.
2. **Annotation loading** — requiring `hegel.typed.portable-data` registers
   `t/ann`/`t/defalias` forms in the checker's global annotation table.
   Loading it proves nothing by itself: it does not run the checker, does not
   change bytecode, and is not reachable from any non-`:typed-check` alias.
3. **Compiler hints** — none exist in this pilot. No `:typed/...` metadata
   or macro here changes AOT compilation, inlining, or emitted bytecode for
   `portable_data.cljc` or any consumer. Annotations are purely a
   static-analysis-time artifact of the `:typed-check` alias.
4. **Actual checking** — only an explicit `(t/check-ns-clj ns-sym opts)` call,
   as performed by `script/hegel/typed_check.clj`, constitutes real
   verification. That script is the only place where "did the checker
   accept or reject this code" is decided. It checks
   `hegel.typed.portable-data` itself (catching a malformed alias/ann in the
   annotation namespace) before checking `hegel.internal.portable-data`, and
   passes `hegel.internal.portable-data` an opts map (`production-check-config`)
   that the driver and mutant controls do not get — see "Checked vs. trusted
   boundary" above.

## Mutation evidence

Four named control namespaces, one per category, each expected to be
rejected by `clojure -M:typed-check`:

- `hegel.typed.controls.wrong-callback-arity` — passes a one-argument
  failure callback where `InvalidFn` requires `[Path Reason -> Nothing]`.
- `hegel.typed.controls.wrong-limit-value` — passes a limits map whose
  `:max-depth` is a string, violating the `Limits` HMap.
- `hegel.typed.controls.text-size-wrong-input` — calls `text-size` with a
  number instead of the annotated `t/Str`.
- `hegel.typed.controls.validate-result-identity-mismatch` — calls
  `validate!` with a `Long` and binds the result to a `t/Str`-annotated var.
  This one specifically guards against a regression in the *shape* of the
  polymorphic annotation: `validate!` is typed
  `(t/All [x] [x Limits InvalidFn -> x])`, tying the result type variable to
  the input type variable. If that were accidentally weakened to an
  unconstrained free result type (e.g.
  `(t/All [x] [t/Any Limits InvalidFn -> x])`), this control would wrongly
  pass — so it is evidence that the identity-preserving polymorphism, not
  just "some annotation exists," is what's being checked.

The runner (`script/hegel/typed_check.clj`) keeps an explicit registry of
exactly these four controls and asserts its count, so dropping a registered
entry is a failure; a new control file must also be added to that registry.
Each control must produce exactly one genuine Typed Clojure error containing
mutation-specific evidence (such as `t/Str` plus `(t/Val 42)` for the
`text-size` control). This matters because a missing `text-size` annotation
would otherwise leave its production def unchecked while a generic
"unannotated var" error could still reject the bad caller. The accept/reject
criterion is a structured check on `check-ns-clj`'s return value or thrown
`ex-info` data (see `check-ns-report` and `expected-type-error?` in the
runner), deliberately **not** a bare `(try ... (catch Throwable _ true))`: a
missing namespace, a require-time syntax error, an unrelated type error, or
an analyzer crash must fail the CI job, not be miscounted as "the checker
correctly rejected this mutant."

The ordinary lint job also includes `typed/`. Its local hook treats `t/ann`
and `t/defalias` forms as declarations so clj-kondo can lint the surrounding
Clojure without interpreting type syntax; Typed Clojure remains responsible
for validating the declarations themselves. Only the deliberately invalid
`text-size` control disables clj-kondo's `:type-mismatch` linter, scoped to
that namespace, because its mismatch is the fixture under test.

**Confirmed by an observed run:** `check-ns-report`'s `errors-of`/
`normalize-report` shape guess was correct enough to surface two real,
specific, named errors from an actual `clojure -M:typed-check` run (checker
1.3.0, JDK 25) rather than an opaque crash — see "Checked vs. trusted
boundary" above for what those two errors were and how they were addressed.
That same run also confirms the external annotations in
`typed/hegel/typed/portable_data.clj` do register and do get consulted: the
errors were specific type/shape complaints against
`hegel.internal.portable-data`, not "no annotation found" failures.

`production-check-config` uses the checker's documented namespace controls:
`{:unannotated-def :unchecked :unannotated-var :error}`. An observed run showed
that `:unannotated-def :unchecked` still analyzes helper forms far enough to
resolve `clojure.string/includes?`, for which checker 1.3.0 supplies no type.
The dev-only annotation namespace therefore gives that exact two-string call
a narrow Boolean-returning signature; unannotated var uses remain errors, and
the fully annotated driver and all four mutants retain strict defaults.

## Cost

With dependencies already cached on Linux/JDK 25, three sequential local
`clojure -M:typed-check` runs took 9.31s, 9.83s, and 9.53s (median 9.53s).
This includes JVM/checker startup and all four rejected mutants. Hosted CI
cost remains to be recorded from the first pull-request run.

## Expand / constrain / reject

- **Expand** (annotate additional namespaces, or move beyond this one file)
  if: `check-ns-clj` cleanly accepts the added namespaces without resorting
  to `t/Any`-heavy escape hatches that would misrepresent what's actually
  checked; the mutation controls for those namespaces continue to catch
  injected regressions; and the measured cost stays small enough that it does
  not materially slow ordinary contribution.
- **Constrain** (keep exactly this bounded, single-file, dev-only pilot) if:
  expanding to more namespaces would require narrowing or widening real
  runtime-validated inputs to `t/Any` just to satisfy the checker, or if
  JVM-only support remains a hard blocker for namespaces that must also run
  on Jolt, Babashka, or jank.
- **Reject / remove** if: the checker cannot express a needed invariant
  without pervasive `t/Any` escape hatches that make the annotations
  misleading rather than informative; if false positives force silencing
  large sections of checked code; or if the measured cost or the ongoing
  effort of keeping annotations in sync with unannotated production code
  repeatedly outweighs what the mutation controls demonstrate it catches.
