(ns hegel.internal.runner-signal
  "Control and infrastructure exceptions that user callbacks must propagate.")

(defn propagate? [error]
  ;; Trace checking also works without loading the native engine.
  (let [data (ex-data error)]
    (or (:hegel/usage-error? data)
        (:hegel/inconclusive? data)
        (contains? #{:hegel.core/assumption-rejected :hegel.ffi/assumption-rejected
                     :hegel.ffi/stop-test :hegel.ffi/error
                     :hegel.generator/leaf-budget-retry :hegel.generator/finish-retry}
                   (:type data)))))
