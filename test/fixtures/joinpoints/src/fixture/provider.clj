(ns fixture.provider)

;; A consumer-owned literal pin: never derive it from the supplied manifest.
(def aspect-provider
  {:schema 1
   :libraries {'chucklehead-dev/jolt-hegel
               "86a70acf7880184707a77069b60b2e8fd4acbbbb"}
   :roles {:test/property-run {:fn 'fixture.provider/around :contract :args-v1}
           :test/state-machine-run {:fn 'fixture.provider/around :contract :args-v1}}})

(def events (atom []))

(defn around [join-point _args proceed]
  (swap! events conj [:enter (:id join-point)])
  (try
    (proceed)
    (finally
      (swap! events conj [:exit (:id join-point)]))))
