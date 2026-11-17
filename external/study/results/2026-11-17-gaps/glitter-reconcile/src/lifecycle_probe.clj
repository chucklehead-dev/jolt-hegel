(require (quote [glitter.core :as c]) (quote [glitter.test-renderer :as tr]))
(doseq [nested? [false true]]
 (let [r (tr/renderer) root (atom {:tag-name "body" :children []}) calls (atom [])
       hook (fn [e] (swap! calls conj (:glitter/life-cycle e)))
       child [:li {:glitter/on-unmount hook} "hi"]
       v (c/reconcile r root (if nested? [:ul child] child))]
  (c/reconcile r root nil (:vdom v) v)
  (prn :nested? nested? :calls @calls :events (tr/events r))))
