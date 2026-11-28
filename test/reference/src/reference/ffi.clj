(ns reference.ffi
  (:require [hegel.core :as h]
            [hegel.generator :as g]
            [hegel.host :as host]
            [hegel.stateful :as hs]
            [hegel.version :as version]
            [hegel.replay-bundle :as bundle]
            [reference.contracts :as contracts]))

(defn- machine [initial first-step second-step check-state]
  (hs/run! {:initial-state initial
            :rules [(hs/rule :first first-step) (hs/rule :second second-step)]
            :invariants [(hs/invariant :model check-state)]}))

(defn- replay [id result property]
  (let [manifest {:hegel-sha (System/getenv "REFERENCE_HEGEL_SHA")
                  :libhegel-version version/libhegel-version
                  :runtime {:host (host/runtime) :version (clojure-version)
                            :os (host/os-name) :arch (System/getProperty "os.arch")}
                  :property-id id :generator-revision "1" :model-revision nil}
        replayed (h/replay-bundle! manifest (bundle/from-result manifest result) property)]
    (assert (= :reproduced (:status replayed)))
    (:failures replayed)))

(defn -main [& _]
  (contracts/check!
   {:integer g/integer :set-gen g/set :vector-gen g/vector :map-gen g/map :fmap g/fmap :draw h/draw! :assume h/assume!
    :machine machine :run #(h/run-test! contracts/opts %) :invalid-count :invalid-test-cases :replay replay}))
