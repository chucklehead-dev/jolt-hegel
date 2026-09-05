(ns hegel.typed.controls.validate-result-identity-mismatch
  "Intentionally invalid control: `validate!` is annotated to return exactly
  the type of its input (an unconstrained polymorphic identity, not `t/Any`).
  Calling it with a Long and binding the result to a `t/Str`-annotated var
  must be rejected by `script/hegel/typed_check.clj`. This specifically
  guards against a regression where the polymorphic result type variable
  becomes disconnected from the input type variable (e.g. an accidental
  `(t/All [x] [t/Any Limits InvalidFn -> x])`), which would let this control
  wrongly pass. See docs/TYPED_CLOJURE.md."
  (:require [typed.clojure :as t]
            [hegel.typed.portable-data]
            [hegel.internal.portable-data :as portable-data]))

(t/ann invalid! hegel.typed.portable-data/InvalidFn)
(defn invalid! [path reason]
  (throw (ex-info "validate! result-identity mismatch"
                  {:path path :reason reason})))

(t/ann limits hegel.typed.portable-data/Limits)
(def limits
  {:max-depth 1 :max-nodes 1 :max-string-chars 1 :max-text-chars 1})

(t/ann mismatched-result t/Str)
(def mismatched-result
  (portable-data/validate! (long 5) limits invalid!))
