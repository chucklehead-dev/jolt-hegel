(ns hegel.header-layout-test
  (:require [clojure.test :refer [deftest is testing]]
            [hegel.header-layout :as layout]
            [hegel.header-snapshot :as header]))

(defn- field-names [fields] (set (map (comp keyword :name) fields)))

(deftest quoted-include-paths-are-portable
  (let [include-name (ns-resolve 'hegel.header-layout 'include-name)]
    (is (= "\"D:/a/project with spaces/hegel.h\""
           (include-name "D:\\a\\project with spaces\\hegel.h")))
    (is (= "\"/tmp/project/hegel.h\"" (include-name "/tmp/project/hegel.h")))
    (is (thrown? clojure.lang.ExceptionInfo (include-name "a\"b.h")))
    (is (thrown? clojure.lang.ExceptionInfo (include-name "a\nb.h")))))

(deftest compiled-probe-measures-the-pinned-header
  (let [snapshot (header/snapshot)
        result (layout/measure! snapshot)]
    (testing "every parsed concrete struct and field is measured"
      (is (= (set (map keyword (keys (:structs snapshot))))
             (set (keys (:structs result)))))
      (doseq [[struct-name fields] (:structs snapshot)
              :let [actual (get-in result [:structs (keyword struct-name)])]]
        (is (pos? (:size actual)) struct-name)
        (is (pos? (:align actual)) struct-name)
        (is (= (field-names fields) (set (keys (:fields actual)))) struct-name)))
    (testing "the probe reports every named C constant, without asserting a host layout"
      (is (= (set (map keyword (keys (:defines snapshot))))
             (set (keys (:defines result)))))
      (doseq [[enum-name members] (:enums snapshot)]
        (is (= (set (map (comp keyword first) members))
               (set (keys (get-in result [:enums (keyword enum-name)])))) enum-name)))
    (is (every? pos? (vals (:primitives result))))
    (is (= (set (keys (:primitives result)))
           (set (keys (:primitive-alignments result)))))
    (is (every? pos? (vals (:primitive-alignments result))))
    (is (pos? (:char-bit result)))
    (is (= (set (map keyword (keys (:enums snapshot))))
           (set (keys (:enum-layouts result)))))
    (doseq [[enum-name _] (:enums snapshot)]
      (let [actual (get-in result [:enum-layouts (keyword enum-name)])]
        (is (pos? (:size actual)) enum-name)
        (is (pos? (:align actual)) enum-name)))
    ;; The canonical ABI declares result codes in an int32 storage cell. This
    ;; target-specific compiler fact must be measured, not inferred from C.
    (is (= 4 (get-in result [:enum-layouts :hegel_result_t :size])))
    (is (.exists (java.io.File. (get-in result [:probe :compile-log]))))
    (is (.exists (java.io.File. (get-in result [:probe :run-log]))))))

(deftest compiler-failure-is-not-treated-as-a-layout
  (let [started (System/nanoTime)
        failure (try
                  (layout/measure! (header/snapshot)
                                   {:header-path "test/fixtures/missing-header-negative-control.h"})
                  nil
                  (catch clojure.lang.ExceptionInfo e (ex-data e)))]
    (is (= :compile (:phase failure)))
    (is (and (integer? (:exit failure)) (not (zero? (:exit failure)))))
    (is (string? (:output failure)))
    (is (.exists (java.io.File. (:log failure))))
    (is (< (/ (- (System/nanoTime) started) 1000000.0) 5000.0))))
