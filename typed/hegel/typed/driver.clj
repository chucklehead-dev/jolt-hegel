(ns hegel.typed.driver
  "A minimal, fully-annotated valid consumer of
  `hegel.internal.portable-data`, checked alongside `text-size`'s body and
  `validate!`'s externally declared call-site contract by
  `script/hegel/typed_check.clj`. Its only purpose is to give the checker a
  realistic passing call site; it is not used by production code or shipped
  to consumers.

  See docs/TYPED_CLOJURE.md for the checked/trusted boundary."
  (:require [typed.clojure :as t]
            [hegel.typed.portable-data]
            [hegel.internal.portable-data :as portable-data]))

(t/ann limits hegel.typed.portable-data/Limits)
(def limits
  {:max-depth 32
   :max-nodes 65536
   :max-string-chars 8192
   :max-text-chars 262144})

(t/ann invalid! hegel.typed.portable-data/InvalidFn)
(defn invalid! [path reason]
  (throw (ex-info "hegel.typed.driver rejected value"
                  {:path path :reason reason})))

(t/ann check-value [t/Any -> t/Any])
(defn check-value
  "Validate an arbitrary value against the driver's own bounded limits,
  returning it unchanged on success."
  [value]
  (portable-data/validate! value limits invalid!))

(t/ann sample-text-size [t/Str -> t/AnyInteger])
(defn sample-text-size [text]
  (portable-data/text-size text))
