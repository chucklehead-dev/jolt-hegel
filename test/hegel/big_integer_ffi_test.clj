(ns hegel.big-integer-ffi-test
  (:require [clojure.test :refer [deftest is]]
            [hegel.ffi :as hffi]
            [hegel.ffi.backend :as backend]
            [hegel.host :as host]))

(defn- exercise
  "Fake storage with exact allocation identities; no fake pointer reaches FFI."
  [{:keys [alloc-fail write-fail read-fail free-fail native-error rc length]
    :or {rc 0 length 1}}]
  (let [allocations (atom [])
        frees (atom [])
        writes (atom [])
        calls (atom [])
        primary (ex-info "injected operation failure" {})
        cleanup (ex-info "injected cleanup failure" {})]
    (with-redefs [backend/with-native-scope (fn [call] (call))
                  backend/sizeof (fn [t] (assert (= :size_t t)) 8)
                  backend/alloc
                  (fn [size]
                    (let [p (inc (count @allocations))]
                      (when (= p alloc-fail) (throw primary))
                      (swap! allocations conj [p size])
                      p))
                  backend/free
                  (fn [p]
                    (swap! frees conj p)
                    (when (contains? free-fail p) (throw cleanup)))
                  backend/write-value
                  (fn [p t offset value]
                    (when (= p write-fail) (throw primary))
                    (swap! writes conj [p t offset value]))
                  backend/read-value
                  (fn [p t & [offset]]
                    (when (= p read-fail) (throw primary))
                    (case t
                      :size_t (do (assert (= 4 p)) length)
                      :uint8 (do (assert (and (= 3 p) (#{0 1} offset))) 255)))
                  hffi/c-generate-integer-big
                  (fn [& args]
                    (swap! calls conj (vec args))
                    (when native-error (throw primary))
                    rc)]
      (let [outcome (host/try-catch-all
                      {:value (hffi/generate-integer-big! nil :case -129 128)}
                      error {:error error})]
        (merge outcome {:allocations @allocations :frees @frees
                        :writes @writes :calls @calls
                        :primary primary :cleanup cleanup})))))

(deftest canonical-call-and-sign-filled-output
  (let [r (exercise {})]
    (is (= -1 (:value r)))
    (is (= [[1 2] [2 2] [3 2] [4 8]] (:allocations r)))
    (is (= [[1 :uint8 0 127] [1 :uint8 1 255]
            [2 :uint8 0 128] [2 :uint8 1 0]
            [4 :size_t 0 0]] (:writes r)))
    (is (= [[nil :case 1 2 2 2 3 2 4]] (:calls r)))
    (is (= [4 3 2 1] (:frees r)))))

(deftest partial-allocation-and-write-failures-release-only-owned-storage
  (doseq [p (range 1 5)]
    (let [r (exercise {:alloc-fail p})]
      (is (identical? (:primary r) (:error r)))
      (is (= (vec (reverse (range 1 p))) (:frees r)))
      (is (empty? (:calls r)))))
  (doseq [p [1 2 4]]
    (let [r (exercise {:write-fail p})]
      (is (identical? (:primary r) (:error r)))
      (is (= (vec (reverse (range 1 (inc p)))) (:frees r)))
      (is (empty? (:calls r))))))

(deftest cleanup-preserves-primary-error-and-never-frees-twice
  (doseq [fault [{:native-error true} {:read-fail 3} {:read-fail 4}]]
    (let [r (exercise (assoc fault :free-fail #{1 2 3 4}))]
      (is (identical? (:primary r) (:error r)))
      (is (= [4 3 2 1] (:frees r)))))
  (doseq [p (range 1 5)]
    (let [r (exercise {:free-fail #{p}})]
      (is (identical? (:cleanup r) (:error r)))
      (is (= [4 3 2 1] (:frees r))))))

(deftest native-verdict-and-length-errors-release-all-storage
  (doseq [[rc expected] [[-1 :hegel.ffi/stop-test]
                        [-2 :hegel.ffi/assumption-rejected]
                        [7 :hegel.ffi/error]]]
    (let [r (exercise {:rc rc :free-fail #{1 2 3 4}})]
      (is (= expected (:type (ex-data (:error r)))))
      (is (= :generate-integer-big (:operation (ex-data (:error r)))))
      (is (= [4 3 2 1] (:frees r)))))
  (doseq [length [0 3]]
    (let [r (exercise {:length length})]
      (is (= {:type :hegel.ffi/error :operation :generate-integer-big
              :length length :capacity 2}
             (ex-data (:error r))))
      (is (= [4 3 2 1] (:frees r))))))
