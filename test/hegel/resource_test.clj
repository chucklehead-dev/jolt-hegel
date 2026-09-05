(ns hegel.resource-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [hegel.host :as host]))

(defn- resource-text* []
  (or (ns-resolve 'hegel.host 'resource-text*)
      (throw (ex-info "resource seam is missing" {}))))

(deftest resource-resolver-precedes-source-root-fallback
  (let [fallback-called (atom false)
        read-resource (resource-text*)]
    (is (= "embedded"
           (@read-resource "hegel.edn"
                           (fn [_] (java.io.StringReader. "embedded"))
                           (fn [_]
                             (reset! fallback-called true)
                             (java.io.StringReader. "source-root")))))
    (is (false? @fallback-called))))

(deftest resource-source-root-fallback-remains-available
  (let [read-resource (resource-text*)]
    (is (= "source-root"
           (@read-resource "hegel.edn"
                           (constantly nil)
                           (constantly (java.io.StringReader. "source-root")))))))

(deftest missing-resource-keeps-the-resource-not-found-contract
  (let [read-resource (resource-text*)
        error (try
                (@read-resource "missing.edn" (constantly nil) (constantly nil))
                nil
                (catch Throwable error error))]
    (is (= ::host/resource-not-found (-> error ex-data :type)))
    (is (= "missing.edn" (-> error ex-data :resource)))))

(deftest resource-seam-is-native-free
  (testing "mocked resolver and fallback exercise the source API only"
    (is (= "mocked"
           (@(resource-text*) "resource.edn"
             (constantly (java.io.StringReader. "mocked"))
             (fn [_] (throw (ex-info "fallback must not run" {}))))))))

(deftest resource-text-reads-the-classpath-abi
  (let [descriptor (edn/read-string (host/resource-text "hegel/abi.edn"))]
    (is (= 1 (:schema-version descriptor)))
    (is (= "libhegel" (get-in descriptor [:library :name])))
    (is (= "0.36.3" (get-in descriptor [:library :version])))))

(deftest resource-text-missing-classpath-resource-keeps-contract
  (let [error (try
                (host/resource-text "hegel/does-not-exist.edn")
                nil
                (catch Throwable error error))]
    (is (= ::host/resource-not-found (-> error ex-data :type)))
    (is (= "hegel/does-not-exist.edn" (-> error ex-data :resource)))))

(deftest jolt-windows-file-resource-avoids-drive-url-rebasing
  (when (= :jolt (host/runtime))
    (let [expected (host/resource-text "hegel/abi.edn")
          roots (ns-resolve 'jolt.host 'source-roots)]
      (with-redefs-fn
        {#'host/os-name (constantly "Windows")
         #'io/resource (constantly (java.net.URL. "file:/intentionally-unreadable"))
         roots (constantly ["resources"])}
        #(is (= expected (host/resource-text "hegel/abi.edn")))))))

(deftest jolt-windows-embedded-resource-keeps-precedence-and-read-errors
  (when (= :jolt (host/runtime))
    (let [resource (java.net.URL. "jar:file:/embedded!/hegel/abi.edn")
          marker (ex-info "embedded read failed" {:marker :embedded-read})
          roots (ns-resolve 'jolt.host 'source-roots)]
      (with-redefs-fn
        {#'host/os-name (constantly "Windows")
         #'io/resource (constantly resource)
         roots (fn [] (throw (ex-info "embedded hit must not scan roots" {})))}
        (fn []
          (with-redefs [slurp (fn [actual]
                               (is (identical? resource actual))
                               "embedded")]
            (is (= "embedded" (host/resource-text "hegel/abi.edn"))))
          (with-redefs [slurp (fn [_] (throw marker))]
            (is (identical? marker
                            (try
                              (host/resource-text "hegel/abi.edn")
                              nil
                              (catch Throwable error error))))))))))

(deftest jolt-windows-file-hit-preserves-normal-root-order
  (when (= :jolt (host/runtime))
    (let [roots (ns-resolve 'jolt.host 'source-roots)]
      (with-redefs-fn
        {#'host/os-name (constantly "Windows")
         #'io/resource (constantly (java.net.URL. "file:/normal-direct-hit"))
         roots (constantly ["test/resource-lookup/early"
                            "test/resource-lookup/later"])}
        (fn []
          ;; A normal direct hit wins even if an earlier root has a nested
          ;; legacy resource. The nested scan is only for a resolver miss.
          (is (= :direct (edn/read-string
                          (host/resource-text "hegel/precedence.edn"))))
          (with-redefs [io/resource (constantly nil)]
            (is (= :legacy-nested (edn/read-string
                                  (host/resource-text "hegel/precedence.edn"))))))))))
