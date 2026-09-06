# Structured counterexample diagnostics

Labelled draws and `note!` calls are assembled in libhegel's test-case
document. At normal verbosity this happens only during the final replay of a
minimal failure. Verbose and debug runs also emit one document for every
exploration case; quiet runs create no printer and return no counterexample
snapshot.

`g/let` supplies stable labels derived from the printed binding form. A direct
two-argument `draw!` supplies an explicit label; the one-argument form remains
silent. This example uses a keyword label, matching the result shape below:

```clojure
(require '[hegel.core :as h]
         '[hegel.generator :as g])

(h/run-test!
 {:test-cases 100 :database ""
  :counterexample
  {:redact-fn (fn [value]
                (if (and (map? value) (contains? value :token))
                  (assoc value :token :redacted)
                  value))
   :render-fn pr-str
   :max-output-units 65536
   :max-width 79}}
 (fn [_]
   (let [request (h/draw! (request-generator) :request)]
     (h/note! "checking decoded request")
     (assert (= request (decode (encode request)))))))
```

The `:counterexample` option is a closed map:

- `:redact-fn` is a unary function applied to each labelled drawn value before
  rendering. It defaults to `identity`.
- `:render-fn` is a unary function applied to the redacted value and must
  return a string. It defaults to `pr-str`, producing Clojure/EDN-style text.
- `:max-output-units` is the total per-case output budget, measured portably in
  UTF-16 code units. It defaults to 65,536 and cannot exceed 1,048,576.
- `:max-width` configures libhegel's document width and defaults to 79. Current
  note and rendered-value strings are indivisible fragments; the engine does
  not tokenize arbitrary Clojure text for wrapping.

Invalid configuration is a usage error before native setup. Rendering,
redaction, or native-printer failures are different: they are bounded
diagnostics and never become shrinkable property failures. A safe fixed
placeholder is used when possible, and the original property exception and
origin remain authoritative. If a native append fails, bounded `:entries`
continue recording while native `:text` may be incomplete; consult `:errors`
before treating the two representations as equivalent.

## Result shape and bounds

Each reproduced failure, and its corresponding entry in `:final`, has a
`:counterexample` snapshot when verbosity enabled printing:

```clojure
{:text ":request {:method :get}\nchecking decoded request\n"
 :entries [{:kind :draw
            :label ":request"
            :text ":request {:method :get}\n"}
           {:kind :note
            :text "checking decoded request\n"}]
 :truncated? false
 :errors []}
```

Only rendered strings are retained; raw drawn values are not copied into this
snapshot. The exact entry is formed and measured before native append. If it
does not fit, the whole entry is rejected, at most one fixed truncation marker
is appended when that marker fits, and later entries are skipped. Strings are
never cut at an arbitrary Unicode boundary.

Labels and notes are caller-authored diagnostic text. Do not derive labels
from generated data or secrets. In particular, `:redact-fn` applies to drawn
values; it cannot sanitize a secret already interpolated into `note!`. Redact
such notes before calling `note!`.

## Rejection and replay semantics

Native speculative regions are paired with the frontend's retry boundaries.
Output produced inside rejected filter attempts, duplicate vector/set/map
candidates, recursive leaf-budget retries, and recursive finish retries is
aborted together with the rejected draw. Accepted attempts commit. Nested
regions remain nested, so an inner rejection cannot discard an accepted outer
document.

The document is sealed and copied only after `mark-complete!`, then its
caller-owned printer handle is freed before the test-case handle. Reading or
rendering never changes generation choices, shrinking, origins, or verdicts.

Counterexample settings and functions are deliberately omitted from portable
replay bundles. They are presentation policy, not reproduction input. Apply
the desired redaction policy again when running a property directly.

This API is sequential. Native printer regions do not imply support for
concurrent property replay or the separate concurrent state-machine design.
