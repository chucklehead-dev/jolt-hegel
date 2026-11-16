(ns hegel.internal.draw-attempt
  "Rollback journal for values consumed inside speculative generator draws."
  (:require [hegel.internal.render :as render]
            [hegel.host :as host]))

(defn begin! [test-case]
  (when-let [stack (:draw-attempts test-case)] (swap! stack conj []))
  (render/begin-attempt! (:render test-case)))

(defn on-discard! [test-case undo]
  (when-let [stack (:draw-attempts test-case)]
    (when (seq @stack)
      (swap! stack update (dec (count @stack)) conj undo))))

(defn commit! [test-case]
  (when-let [stack (:draw-attempts test-case)]
    (let [undos (peek @stack)]
      (swap! stack pop)
      ;; An accepted inner draw still belongs to its speculative parent.
      (when (seq @stack)
        (swap! stack update (dec (count @stack)) into undos))))
  (render/commit-attempt! (:render test-case)))

(defn abort! [test-case]
  (when-let [stack (:draw-attempts test-case)]
    (let [undos (peek @stack)]
      (swap! stack pop)
      (doseq [undo (reverse undos)] (undo))))
  (render/abort-attempt! (:render test-case)))

(defn abort-with-error! [test-case error]
  (host/try-catch-all (abort! test-case) _cleanup-error nil)
  (throw error))
