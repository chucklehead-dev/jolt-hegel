(ns hegel.typed.portable-data
  "Dev-only Typed Clojure external annotations for the unchanged
  `hegel.internal.portable-data` body (`src/hegel/internal/portable_data.cljc`,
  `:clj` reader branch only).

  This namespace registers annotations against fully-qualified var symbols in
  another namespace; it never redefines or requires changes to that body. See
  docs/TYPED_CLOJURE.md for the checked/trusted boundary, why these particular
  types were chosen, and the pilot's expand/constrain/reject criteria."
  (:require [typed.clojure :as t]
            [hegel.internal.portable-data]))

;; Path segments are either a stage keyword (:entry, :key, :value) or a
;; numeric map/vector index; see the (conj path ...) call sites in
;; portable_data.cljc.
(t/defalias PathSegment (t/U t/Keyword t/AnyInteger))
(t/defalias Path (t/Vec PathSegment))

;; Closed over exactly the reasons portable_data.cljc's invalid! call sites
;; pass today: :max-depth and :max-nodes from consume-node!; :max-string-chars,
;; :nul-string and :max-text-chars from consume-text!; :opaque-value from
;; validate! itself. Adding a new call site with an unlisted reason is a type
;; error against this alias, by design.
(t/defalias Reason
  (t/U ':max-depth ':max-nodes ':max-string-chars ':max-text-chars
       ':nul-string ':opaque-value))

;; Trusted caller configuration; :complete? true rejects both missing and
;; extra keys, matching every real caller's literal limits map.
(t/defalias Limits
  (t/HMap :mandatory {:max-depth t/AnyInteger
                       :max-nodes t/AnyInteger
                       :max-string-chars t/AnyInteger
                       :max-text-chars t/AnyInteger}
          :complete? true))

;; The failure callback must throw (see portable_data.cljc's docstring); it
;; never returns a usable value, so its range is the bottom type rather than
;; t/Any. That keeps callers who branch on invalid!'s call from acquiring a
;; spurious return type.
(t/defalias InvalidFn [Path Reason -> t/Nothing])

;; Typed Clojure 1.3.0 does not ship an annotation for this clojure.string
;; helper. The checked production call has already narrowed both arguments to
;; strings, so this deliberately narrow dev-only signature covers that seam
;; without widening other unannotated vars to Any.
(t/ann clojure.string/includes? [t/Str t/Str -> t/Bool])

(t/ann hegel.internal.portable-data/text-size [t/Str -> t/AnyInteger])

;; validate! returns exactly the value it was given (or invalid! throws); the
;; unconstrained type variable x threads that identity through the checker
;; instead of widening the result to t/Any, which would misrepresent
;; validate!'s actual job of accepting arbitrary untrusted EDN-shaped input.
;;
;; ^:no-check: an observed clojure -M:typed-check run (checker 1.3.0, JDK 25)
;; reported "validate! loop requires inline annotations" when the checker
;; attempted to type-check validate!'s own iterative loop body against this
;; signature. That loop lives in the unchanged production source
;; (portable_data.cljc) and cannot be given inline type hints without editing
;; src, which this pilot does not do. ^:no-check tells the checker to trust
;; this declared type at every call site (typed/hegel/typed/driver.clj and
;; the four control namespaces under typed/hegel/typed/controls/ still have
;; the polymorphic Limits/InvalidFn/input-result contract enforced against
;; them) without attempting to verify that
;; validate!'s own implementation satisfies it. validate!'s production body
;; is therefore explicitly trusted, not checked; runtime enforcement and the
;; existing behavioral tests remain authoritative, exactly as they were before
;; this pilot existed.
(t/ann ^:no-check hegel.internal.portable-data/validate!
  (t/All [x] [x Limits InvalidFn -> x]))
