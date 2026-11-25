;; Executed by Jolt against the candidate engine, independently of hegel.ffi.
(require '[clojure.string :as str] '[jolt.ffi :as ffi])
(let [[engine driver] *command-line-args*]
  (ffi/load-library engine)
  (ffi/load-library driver))

(let [run-verbose (ffi/foreign-fn "candidate_verbose_failure" [:pointer :pointer] :int)
      stderr-sink (ffi/foreign-fn "hegel_output_stderr_callback" [] :pointer)
      write-line (ffi/pointer-fn [:pointer :pointer :size_t] :void)
      lines (atom [])]
  (with-open [a (ffi/confined-arena)]
    (let [token (ffi/alloc a 1)
          sink (ffi/callback a
                 (fn [user-data line n]
                   (swap! lines conj [(= token user-data) (ffi/read-bytes line n)]))
                 [:pointer :pointer :size_t] :void)]
      (assert (zero? (run-verbose sink token)))
      (assert (and (seq @lines) (every? first @lines)
                   (some #(str/includes? (second %) "mismatch: λ") @lines)))
      ;; Exercise a native-owned function pointer in the other direction.
      ;; Sixteen UTF-8 bytes include the lambda and exclude the exclamation mark.
      (write-line (stderr-sink) ffi/null (ffi/string->ptr a "pointer-sink: λ!") 16))))
