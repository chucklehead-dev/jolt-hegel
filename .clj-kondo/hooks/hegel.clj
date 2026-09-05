(ns hooks.hegel
  (:require [clj-kondo.hooks-api :as api]))

(defn clojure-test-with
  "Expose hegel.clojure-test/with bindings to clj-kondo as an ordinary let."
  [{:keys [node]}]
  (let [[_ _options bindings & body] (:children node)]
    {:node (api/list-node
            (list* (api/token-node 'let) bindings body))}))

(defn try-catch-all
  "Expose the portable catch binding to clj-kondo as an ordinary catch."
  [{:keys [node]}]
  (let [[_ try-form binding & catch-forms] (:children node)]
    {:node
     (api/list-node
      [(api/token-node 'try)
       try-form
       (api/list-node
        (list* (api/token-node 'catch)
               (api/token-node 'Throwable)
               binding
               catch-forms))])}))

(defn typed-declaration
  "Treat dev-only Typed Clojure declarations as data for lint purposes.
  Typed Clojure itself validates their syntax and meaning in the typed-check
  job; ignoring their children here lets clj-kondo analyze the surrounding
  ordinary Clojure definitions without interpreting type syntax as code."
  [_]
  {:node (api/list-node [(api/token-node 'comment)])})
