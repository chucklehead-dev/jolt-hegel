(ns hegel.internal.event-id
  "Canonical scalar ordering shared by event-profile validation and fan-in rules."
  (:refer-clojure :exclude [key]))

(defn key [operation-id]
  ;; Preserve the existing tagged scalar spelling order, not numeric order.
  ;; Composite EDN and host objects have no portable identity/printing order.
  (cond
    (integer? operation-id) (str "0:" operation-id)
    (string? operation-id) (str "1:" (pr-str operation-id))
    (keyword? operation-id) (str "2:" (pr-str operation-id))
    (symbol? operation-id) (str "3:" (pr-str operation-id))
    :else nil))
