(ns hegel.typed.controls.wrong-limit-value
  "Intentionally invalid control: supplies a non-numeric `:max-depth` in the
  limits map, violating `hegel.typed.portable-data/Limits`. Must be rejected
  by `script/hegel/typed_check.clj`; see docs/TYPED_CLOJURE.md."
  (:require [typed.clojure :as t]
            [hegel.typed.portable-data]
            [hegel.internal.portable-data :as portable-data]))

(t/ann invalid! hegel.typed.portable-data/InvalidFn)
(defn invalid! [path reason]
  (throw (ex-info "wrong limit value" {:path path :reason reason})))

(t/ann call-with-bad-limits [t/Any -> t/Any])
(defn call-with-bad-limits [value]
  (portable-data/validate!
   value
   {:max-depth "not-a-number"
    :max-nodes 1
    :max-string-chars 1
    :max-text-chars 1}
   invalid!))
