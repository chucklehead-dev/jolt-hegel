(ns hooks.hegel
  (:require [clj-kondo.hooks-api :as api]))

(defn clojure-test-with
  "Expose hegel.clojure-test/with bindings to clj-kondo as an ordinary let."
  [{:keys [node]}]
  (let [[_ _options bindings & body] (:children node)]
    {:node (api/list-node
            (list* (api/token-node 'let) bindings body))}))
