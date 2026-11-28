(ns hegel.internal.stateful-id
  (:require [clojure.string :as str]
            [hegel.validation :as validation]))

(defn canonical [kind value]
  (let [value (cond
                (string? value) value
                (keyword? value) (subs (str value) 1)
                (symbol? value) (str value)
                :else nil)]
    (when (or (nil? value)
              (str/blank? value)
              (str/includes? value "\u0000"))
      (validation/usage-error! :hegel.stateful/invalid-argument
       (str (name kind) " name must be a nonblank string, keyword, or symbol")
       {:kind kind :name value}))
    value))
