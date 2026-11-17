(ns probe (:require [glitter.core :as c] [glitter.test-renderer :as tr]))
(let [r (tr/renderer) root (atom {:tag-name "body" :children []})
      view (fn [ks] (into [:ul] (map (fn [k] [:li {:glitter/key k} (str k)]) ks)))
      v (c/reconcile r root (view [0 1 2]))]
  (tr/reset-events! r)
  (c/reconcile r root (view [1 2 0]) (:vdom v) v)
  (prn :actual (mapv #(-> % deref :children first deref :text) (:children @(first (:children @root)))))
  (prn :events (tr/events r)))
