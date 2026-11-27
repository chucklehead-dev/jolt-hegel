(ns reference.ffi
  (:require [hegel.core :as h]
            [hegel.generator :as g]
            [hegel.host :as host]
            [hegel.version :as version]
            [hegel.replay-bundle :as bundle]
            [reference.contracts :as contracts]))

(defn- replay [result property]
  (let [manifest {:hegel-sha (System/getenv "REFERENCE_HEGEL_SHA")
                  :libhegel-version version/libhegel-version
                  :runtime {:host (host/runtime) :version (clojure-version)
                            :os (host/os-name) :arch (System/getProperty "os.arch")}
                  :property-id "reference/threshold" :generator-revision "1" :model-revision nil}
        replayed (h/replay-bundle! manifest (bundle/from-result manifest result) property)]
    (assert (= :reproduced (:status replayed)))
    (:failures replayed)))

(defn -main [& _]
  (contracts/check!
   {:integer g/integer :set-gen g/set :draw h/draw! :assume h/assume!
    :run #(h/run-test! contracts/opts %) :invalid-count :invalid-test-cases :replay replay}))
