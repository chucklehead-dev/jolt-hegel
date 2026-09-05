(ns fixture.stale-provider
  (:require [fixture.provider :as provider]))

;; The adjacent valid provider supplies all other fields unchanged.
(def aspect-provider
  (assoc-in provider/aspect-provider
            [:libraries 'chucklehead-dev/jolt-hegel] "deliberately-stale"))
