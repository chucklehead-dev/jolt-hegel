(ns hegel.typed.controls.wrong-callback-arity
  "Intentionally invalid control: passes a single-arity failure callback where
  `hegel.internal.portable-data/validate!` requires the two-argument
  `hegel.typed.portable-data/InvalidFn` shape (path, reason). Must be rejected
  by `script/hegel/typed_check.clj`; see docs/TYPED_CLOJURE.md."
  (:require [typed.clojure :as t]
            [hegel.typed.portable-data]
            [hegel.internal.portable-data :as portable-data]))

(t/ann bad-invalid! [t/Any -> t/Nothing])
(defn bad-invalid! [reason]
  (throw (ex-info "wrong callback arity" {:reason reason})))

(t/ann call-with-bad-arity [t/Any -> t/Any])
(defn call-with-bad-arity [value]
  (portable-data/validate!
   value
   {:max-depth 1 :max-nodes 1 :max-string-chars 1 :max-text-chars 1}
   bad-invalid!))
