(ns hegel.corpus-digest-jolt-test
  "Jolt-only ownership controls; platform hashing has separate golden vectors."
  (:require [clojure.test :refer [deftest is]]
            [hegel.corpus.digest.jolt]))

(defn- use-provider [open! close! body]
  ((ns-resolve 'hegel.corpus.digest.jolt 'with-provider) open! close! body))

(defn- caught [thunk]
  (try (thunk) nil (catch Throwable error error)))

(deftest provider-is-closed-exactly-once-after-body
  (let [events (atom [])
        result (use-provider
                #(do (swap! events conj :open) :handle)
                #(swap! events conj [:close %])
                #(do (swap! events conj [:body %]) :owned-output))]
    (is (= :owned-output result))
    (is (= [:open [:body :handle] [:close :handle]] @events))))

(deftest open-failure-does-not-close-an-unowned-handle
  (let [error (ex-info "open failed" {})
        events (atom [])]
    (is (identical? error
                    (caught #(use-provider
                               (fn [] (throw error))
                               (fn [_] (swap! events conj :close))
                               (fn [_] (swap! events conj :body))))))
    (is (= [] @events))))

(deftest body-failure-still-closes-and-retains-primary-error
  (doseq [close-fails? [false true]]
    (let [primary (ex-info "hash or read failed" {})
          secondary (ex-info "close failed" {})
          closes (atom [])]
      (is (identical? primary
                      (caught #(use-provider
                                 (fn [] :handle)
                                 (fn [h]
                                   (swap! closes conj h)
                                   (when close-fails? (throw secondary)))
                                 (fn [_] (throw primary))))))
      (is (= [:handle] @closes)))))

(deftest close-failure-cannot-be-returned-as-a-successful-digest
  (let [error (ex-info "close failed" {})]
    (is (identical? error
                    (caught #(use-provider
                               (fn [] :handle)
                               (fn [_] (throw error))
                               (fn [_] :owned-output)))))))

(defn -main [& _]
  (let [{:keys [fail error]}
        (clojure.test/run-tests 'hegel.corpus-digest-jolt-test)]
    (when (pos? (+ fail error))
      (throw (ex-info "corpus digest ownership tests failed"
                      {:fail fail :error error})))))
