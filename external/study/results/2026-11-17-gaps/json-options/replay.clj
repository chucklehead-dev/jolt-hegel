(require '[study :as s] '[hegel.core :as h] '[hegel.replay-bundle.codec :as codec])
(let [expected (assoc s/provenance-base :property-id "control/first-wins")
      b (codec/decode (slurp "control-bundle.edn"))
      mismatch (h/replay-bundle! (assoc expected :generator-revision "wrong-revision") b
                 (fn [_] (throw (ex-info "Mismatch executed property" {}))))
      r (h/replay-bundle! expected b s/control-property)]
  (prn "mismatch-gate" (select-keys mismatch [:status :reproduced?]))
  (prn "semantic-control-replay" (select-keys r [:status :reproduced? :flaky?]))
  (assert (= :incompatible (:status mismatch)))
  (assert (and (= :reproduced (:status r)) (:reproduced? r) (not (:flaky? r)))))
