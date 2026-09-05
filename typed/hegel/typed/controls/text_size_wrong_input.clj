(ns hegel.typed.controls.text-size-wrong-input
  "Intentionally invalid control: calls
  `hegel.internal.portable-data/text-size` with a number instead of the
  annotated `t/Str` input. Must be rejected by
  `script/hegel/typed_check.clj`; see docs/TYPED_CLOJURE.md."
  (:require [typed.clojure :as t]
            [hegel.typed.portable-data]
            [hegel.internal.portable-data :as portable-data]))

(t/ann call-text-size-with-number [-> t/AnyInteger])
(defn call-text-size-with-number []
  (portable-data/text-size 42))
