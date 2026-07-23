(ns hegel.ffi
  "Low-level, ownership-aware bindings to libhegel's C ABI."
  (:require [hegel.native :as native]
            [hegel.version :as version]
            [jolt.ffi :as ffi]))

(def libhegel-version
  "The libhegel release whose C ABI this namespace binds."
  version/libhegel-version)

(def library-path
  "The shared object selected for this process."
  native/library-path)

(def shim-library-path
  "The date/time aggregate shim selected for this process. Loaded lazily."
  native/shim-library-path)

(try
  (ffi/load-library library-path)
  (catch Throwable cause
    (throw
     (ex-info
      (str "could not load libhegel from " (pr-str library-path)
           "; place libhegel v" libhegel-version
           " there or set HEGEL_LIBHEGEL_LIBRARY")
      {:type ::library-load-failed
       :library library-path
       :cause cause}))))

(def ^:private shim-loaded? (atom false))

(ffi/defcfn c-shim-init "jolt_hegel_shim_init" [:string] :int)
(ffi/defcfn c-shim-error "jolt_hegel_shim_error" [] :string)

(defn- ensure-shim-loaded! []
  (when-not @shim-loaded?
    (locking shim-loaded?
      (when-not @shim-loaded?
        (try
          (ffi/load-library shim-library-path)
          (catch Throwable cause
            (throw
             (ex-info
              (str "could not load the Hegel date/time shim from "
                   (pr-str shim-library-path)
                   "; run `joltc -m hegel.install` or set "
                   "HEGEL_SHIM_LIBRARY")
              {:type ::shim-load-failed
               :library shim-library-path
               :cause cause}))))
        (let [rc (c-shim-init library-path)]
          (when-not (zero? rc)
            (throw
             (ex-info
              (str "could not initialize the Hegel date/time shim: "
                   (or (c-shim-error) "no shim diagnostic"))
              {:type ::shim-initialization-failed
               :library shim-library-path
               :libhegel-library library-path
               :result rc})))
          (reset! shim-loaded? true))))))

;; Context and settings.
(ffi/defcfn c-context-new "hegel_context_new" [] :pointer)
(ffi/defcfn c-context-free "hegel_context_free" [:pointer] :int)
(ffi/defcfn c-context-last-error "hegel_context_last_error" [:pointer] :string)

(ffi/defcfn c-settings-new "hegel_settings_new" [:pointer :pointer] :int)
(ffi/defcfn c-settings-free "hegel_settings_free" [:pointer :pointer] :int)
(ffi/defcfn c-settings-set-mode "hegel_settings_set_mode"
  [:pointer :pointer :uint] :int)
(ffi/defcfn c-settings-set-backend "hegel_settings_set_backend"
  [:pointer :pointer :uint] :int)
(ffi/defcfn c-settings-set-test-cases "hegel_settings_set_test_cases"
  [:pointer :pointer :uint64] :int)
(ffi/defcfn c-settings-set-verbosity "hegel_settings_set_verbosity"
  [:pointer :pointer :uint] :int)
(ffi/defcfn c-settings-set-seed "hegel_settings_set_seed"
  [:pointer :pointer :uint64 :uint8] :int)
(ffi/defcfn c-settings-set-derandomize "hegel_settings_set_derandomize"
  [:pointer :pointer :uint8] :int)
(ffi/defcfn c-settings-set-report-multiple-failures
  "hegel_settings_set_report_multiple_failures"
  [:pointer :pointer :uint8] :int)
(ffi/defcfn c-settings-set-database "hegel_settings_set_database"
  [:pointer :pointer :pointer] :int)
(ffi/defcfn c-settings-set-database-key "hegel_settings_set_database_key"
  [:pointer :pointer :pointer] :int)
(ffi/defcfn c-settings-set-phases "hegel_settings_set_phases"
  [:pointer :pointer :uint] :int)
(ffi/defcfn c-settings-set-suppress-health-check
  "hegel_settings_set_suppress_health_check"
  [:pointer :pointer :uint] :int)

;; Run lifecycle. next-test-case blocks on libhegel's worker. run-free may drain
;; and join that worker, so it is collect-safe too.
(ffi/defcfn c-run-start "hegel_run_start"
  [:pointer :pointer :pointer :pointer :pointer] :int)
(ffi/defcfn c-next-test-case "hegel_next_test_case"
  [:pointer :pointer :pointer] :int :blocking)
(ffi/defcfn c-run-result "hegel_run_result"
  [:pointer :pointer :pointer] :int)
(ffi/defcfn c-run-free "hegel_run_free"
  [:pointer :pointer] :int :blocking)

;; Test cases and primitive draws.
(ffi/defcfn c-test-case-from-blob "hegel_test_case_from_blob"
  [:pointer :pointer :pointer :pointer :pointer :pointer] :int)
(ffi/defcfn c-test-case-free "hegel_test_case_free"
  [:pointer :pointer] :int)
(ffi/defcfn c-generate-integer "hegel_generate_integer"
  [:pointer :pointer :int64 :int64 :pointer] :int)
(ffi/defcfn c-generate-boolean "hegel_generate_boolean"
  [:pointer :pointer :double :uint8 :uint8 :pointer] :int)
(ffi/defcfn c-generate-float "hegel_generate_float"
  [:pointer :pointer :uint :double :double :uint8 :uint8 :uint8 :uint8
   :double :pointer]
  :int)
(ffi/defcfn c-generate-bytes "hegel_generate_bytes"
  [:pointer :pointer :uint64 :uint64 :pointer] :int)
(ffi/defcfn c-generate-bytes-result-free "hegel_generate_bytes_result_free"
  [:pointer :pointer] :int)
(ffi/defcfn c-string-generator-text "hegel_string_generator_text"
  [:pointer :uint64 :uint64 :pointer :uint :uint
   :pointer :size_t :pointer :size_t
   :pointer :size_t :pointer :size_t :pointer]
  :int)
(ffi/defcfn c-string-generator-regex "hegel_string_generator_regex"
  [:pointer :pointer :uint8 :pointer :pointer] :int)
(ffi/defcfn c-string-generator-email "hegel_string_generator_email"
  [:pointer :pointer] :int)
(ffi/defcfn c-string-generator-url "hegel_string_generator_url"
  [:pointer :pointer] :int)
(ffi/defcfn c-string-generator-domain "hegel_string_generator_domain"
  [:pointer :uint64 :pointer] :int)
(ffi/defcfn c-string-generator-free "hegel_string_generator_free"
  [:pointer :pointer] :int)
(ffi/defcfn c-generate-string "hegel_generate_string"
  [:pointer :pointer :pointer :pointer] :int)
(ffi/defcfn c-generate-string-result-free
  "hegel_generate_string_result_free"
  [:pointer :pointer] :int)
(ffi/defcfn c-generate-uuid "hegel_generate_uuid"
  [:pointer :pointer :uint8 :uint8 :pointer] :int)
(ffi/defcfn c-generate-ipv4 "hegel_generate_ipv4"
  [:pointer :pointer :pointer] :int)
(ffi/defcfn c-generate-ipv6 "hegel_generate_ipv6"
  [:pointer :pointer :pointer] :int)
(ffi/defcfn c-generate-date "jolt_hegel_generate_date"
  [:pointer :pointer :pointer :pointer :pointer] :int)
(ffi/defcfn c-generate-time "jolt_hegel_generate_time"
  [:pointer :pointer :pointer :pointer :pointer] :int)
(ffi/defcfn c-generate-datetime "jolt_hegel_generate_datetime"
  [:pointer :pointer :pointer :pointer :pointer] :int)
(ffi/defcfn c-target "hegel_target"
  [:pointer :pointer :double :pointer] :int)
(ffi/defcfn c-start-span "hegel_start_span"
  [:pointer :pointer :uint64] :int)
(ffi/defcfn c-stop-span "hegel_stop_span"
  [:pointer :pointer :uint8] :int)
(ffi/defcfn c-new-collection "hegel_new_collection"
  [:pointer :pointer :uint64 :uint64 :pointer] :int)
(ffi/defcfn c-collection-more "hegel_collection_more"
  [:pointer :pointer :int64 :pointer] :int)
(ffi/defcfn c-collection-reject "hegel_collection_reject"
  [:pointer :pointer :int64 :pointer] :int)
(ffi/defcfn c-new-pool "hegel_new_pool"
  [:pointer :pointer :pointer] :int)
(ffi/defcfn c-pool-add "hegel_pool_add"
  [:pointer :pointer :int64 :pointer] :int)
(ffi/defcfn c-pool-generate "hegel_pool_generate"
  [:pointer :pointer :int64 :uint8 :pointer] :int)
(ffi/defcfn c-new-state-machine "hegel_new_state_machine"
  [:pointer :pointer :pointer :size_t :pointer :size_t :pointer] :int)
(ffi/defcfn c-state-machine-next-rule "hegel_state_machine_next_rule"
  [:pointer :pointer :int64 :pointer] :int)
(ffi/defcfn c-mark-complete "hegel_mark_complete"
  [:pointer :pointer :uint :pointer] :int :blocking)

;; Result and failure snapshots.
(ffi/defcfn c-run-result-free "hegel_run_result_free"
  [:pointer :pointer] :int)
(ffi/defcfn c-run-result-status "hegel_run_result_status"
  [:pointer :pointer :pointer] :int)
(ffi/defcfn c-run-result-error "hegel_run_result_error"
  [:pointer :pointer :pointer] :int)
(ffi/defcfn c-run-result-failure-count "hegel_run_result_failure_count"
  [:pointer :pointer :pointer] :int)
(ffi/defcfn c-run-result-failure "hegel_run_result_failure"
  [:pointer :pointer :size_t :pointer] :int)
(ffi/defcfn c-failure-free "hegel_failure_free"
  [:pointer :pointer] :int)
(ffi/defcfn c-failure-origin "hegel_failure_origin"
  [:pointer :pointer :pointer] :int)
(ffi/defcfn c-failure-reproduction-blob "hegel_failure_reproduction_blob"
  [:pointer :pointer :pointer] :int)
(ffi/defcfn c-version "hegel_version" [:pointer :pointer] :int)

(def status-valid 0)
(def status-invalid 1)
(def status-overrun 2)
(def status-interesting 3)

(def run-status-passed 0)
(def run-status-failed 1)
(def run-status-error 2)

(def label-list 1)
(def label-set 3)
(def label-map 5)
(def label-tuple 7)
(def label-one-of 8)
(def label-optional 9)
(def label-flat-map 11)
(def label-filter 12)
(def label-mapped 13)

(def state-machine-done
  "Sentinel returned by state-machine-next-rule! after the final step."
  -1)

(def no-max-size
  "UINT64_MAX, used by libhegel for an engine-bounded collection size."
  18446744073709551615N)

(defn- context-error [ctx]
  (when (and ctx (not (ffi/null? ctx)))
    (c-context-last-error ctx)))

(defn check!
  "Return nil for HEGEL_OK, otherwise throw with the context diagnostic."
  [ctx operation rc]
  (when-not (zero? rc)
    (let [diagnostic (or (context-error ctx) "no libhegel diagnostic")]
      (throw
       (ex-info
        (str (name operation) " failed (libhegel rc " rc "): " diagnostic)
        {:type ::error
         :operation operation
         :result rc
         :diagnostic diagnostic}))))
  nil)

(defn check-draw!
  "Translate draw control-flow codes, and throw ordinary FFI errors otherwise."
  [ctx operation rc]
  (case rc
    0 nil
    -1 (throw (ex-info "libhegel stopped this test case"
                       {:type ::stop-test
                        :operation operation}))
    -2 (throw (ex-info "libhegel rejected this test case"
                       {:type ::assumption-rejected
                        :operation operation}))
    (check! ctx operation rc)))

(defn stop-test? [error]
  (= ::stop-test (:type (ex-data error))))

(defn assumption-rejected? [error]
  (= ::assumption-rejected (:type (ex-data error))))

(defn- call-out!
  [ctx operation type call]
  (let [out (ffi/alloc (ffi/sizeof type))]
    (try
      (let [rc (call out)]
        (check! ctx operation rc)
        (ffi/read out type))
      (finally
        (ffi/free out)))))

(defn- call-draw-out!
  [ctx operation type call]
  (let [out (ffi/alloc (ffi/sizeof type))]
    (try
      (let [rc (call out)]
        (check-draw! ctx operation rc)
        (ffi/read out type))
      (finally
        (ffi/free out)))))

(defn- with-c-string
  [value call]
  (if (nil? value)
    (call ffi/null)
    (let [ptr (ffi/string->ptr (str value))]
      (try
        (call ptr)
        (finally
          (ffi/free ptr))))))

(defn- allocate-c-strings [values]
  (let [pointers (atom [])]
    (try
      (doseq [value values]
        (swap! pointers conj (ffi/string->ptr value)))
      @pointers
      (catch Throwable error
        (doseq [ptr @pointers]
          (ffi/free ptr))
        (throw error)))))

(defn- with-c-string-array
  "Pass nil as a NULL list and a collection, including empty, as a real list."
  [values call]
  (if (nil? values)
    (call ffi/null 0)
    (let [pointers (allocate-c-strings values)]
      (try
        (let [pointer-size (ffi/sizeof :pointer)
              ;; A non-NULL pointer distinguishes [] from an unspecified list.
              array (ffi/alloc (max 1 (* pointer-size (count pointers))))]
          (try
            (doseq [[index ptr] (map-indexed vector pointers)]
              (ffi/write array :pointer (* index pointer-size) ptr))
            (call array (count pointers))
            (finally
              (ffi/free array))))
        (finally
          (doseq [ptr pointers]
            (ffi/free ptr)))))))

(defn- with-utf8-buffer
  "Pass a length-delimited UTF-8 buffer, preserving empty strings and U+0000."
  [value call]
  (if (nil? value)
    (call ffi/null 0)
    (let [ptr (ffi/string->ptr value)]
      (try
        (call ptr (ffi/write-bytes ptr value))
        (finally
          (ffi/free ptr))))))

(defn context-new! []
  (let [ctx (c-context-new)]
    (when (ffi/null? ctx)
      (throw (ex-info "hegel_context_new returned NULL"
                      {:type ::context-allocation-failed})))
    ctx))

(defn context-free! [ctx]
  ;; The context no longer exists after this call, so there is no diagnostic to
  ;; consult if a buggy native implementation ever returns nonzero.
  (c-context-free ctx)
  nil)

(defn settings-new! [ctx]
  (call-out! ctx :settings-new :pointer
             #(c-settings-new ctx %)))

(defn settings-free! [ctx settings]
  (c-settings-free ctx settings)
  nil)

(defn settings-set-mode! [ctx settings value]
  (check! ctx :settings-set-mode
          (c-settings-set-mode ctx settings value)))

(defn settings-set-backend! [ctx settings value]
  (check! ctx :settings-set-backend
          (c-settings-set-backend ctx settings value)))

(defn settings-set-test-cases! [ctx settings value]
  (check! ctx :settings-set-test-cases
          (c-settings-set-test-cases ctx settings value)))

(defn settings-set-verbosity! [ctx settings value]
  (check! ctx :settings-set-verbosity
          (c-settings-set-verbosity ctx settings value)))

(defn settings-set-seed! [ctx settings seed present?]
  (check! ctx :settings-set-seed
          (c-settings-set-seed ctx settings seed (if present? 1 0))))

(defn settings-set-derandomize! [ctx settings value]
  (check! ctx :settings-set-derandomize
          (c-settings-set-derandomize ctx settings (if value 1 0))))

(defn settings-set-report-multiple-failures! [ctx settings value]
  (check! ctx :settings-set-report-multiple-failures
          (c-settings-set-report-multiple-failures
           ctx settings (if value 1 0))))

(defn settings-set-database! [ctx settings database]
  (with-c-string
    database
    #(check! ctx :settings-set-database
             (c-settings-set-database ctx settings %))))

(defn settings-set-database-key! [ctx settings key]
  (with-c-string
    key
    #(check! ctx :settings-set-database-key
             (c-settings-set-database-key ctx settings %))))

(defn settings-set-phases! [ctx settings phases]
  (check! ctx :settings-set-phases
          (c-settings-set-phases ctx settings phases)))

(defn settings-set-suppress-health-check! [ctx settings checks]
  (check! ctx :settings-set-suppress-health-check
          (c-settings-set-suppress-health-check ctx settings checks)))

(defn run-start! [ctx settings]
  (call-out! ctx :run-start :pointer
             #(c-run-start ctx settings ffi/null ffi/null %)))

(defn next-test-case! [ctx run]
  (let [handle (call-out! ctx :next-test-case :pointer
                          #(c-next-test-case ctx run %))]
    (when-not (ffi/null? handle)
      handle)))

(defn run-result! [ctx run]
  (call-out! ctx :run-result :pointer
             #(c-run-result ctx run %)))

(defn run-free! [ctx run]
  (c-run-free ctx run)
  nil)

(defn test-case-from-blob! [ctx settings blob]
  (when (nil? blob)
    (throw (ex-info "a reproduction blob is required"
                    {:type ::invalid-argument
                     :argument :blob})))
  (with-c-string
    blob
    (fn [blob-ptr]
      (call-out! ctx :test-case-from-blob :pointer
                 #(c-test-case-from-blob
                   ctx settings blob-ptr ffi/null ffi/null %)))))

(defn test-case-free! [ctx test-case]
  (c-test-case-free ctx test-case)
  nil)

(defn generate-integer! [ctx test-case min-value max-value]
  (let [out (ffi/alloc (ffi/sizeof :int64))]
    (try
      (let [rc (c-generate-integer
                ctx test-case min-value max-value out)]
        (check-draw! ctx :generate-integer rc)
        (ffi/read out :int64))
      (finally
        (ffi/free out)))))

(defn generate-boolean! [ctx test-case probability forced forced?]
  (let [out (ffi/alloc (ffi/sizeof :uint8))]
    (try
      (let [rc (c-generate-boolean
                ctx test-case probability
                (if forced 1 0) (if forced? 1 0) out)]
        (check-draw! ctx :generate-boolean rc)
        (not (zero? (ffi/read out :uint8))))
      (finally
        (ffi/free out)))))

(defn generate-float!
  [ctx test-case width min-value max-value allow-nan? allow-infinity?
   exclude-min? exclude-max? smallest-nonzero-magnitude]
  (let [out (ffi/alloc (ffi/sizeof :double))]
    (try
      (let [rc (c-generate-float
                ctx test-case width
                (double min-value) (double max-value)
                (if allow-nan? 1 0) (if allow-infinity? 1 0)
                (if exclude-min? 1 0) (if exclude-max? 1 0)
                (double smallest-nonzero-magnitude)
                out)]
        (check-draw! ctx :generate-float rc)
        (ffi/read out :double))
      (finally
        (ffi/free out)))))

(defn- zero-memory! [ptr size]
  (dotimes [offset size]
    (ffi/write ptr :uint8 offset 0)))

(def ^:private date-size 8)
(def ^:private time-size 8)
(def ^:private datetime-size 16)

(defn- write-date! [ptr offset value]
  (ffi/write ptr :int offset (:year value))
  (ffi/write ptr :uint8 (+ offset 4) (:month value))
  (ffi/write ptr :uint8 (+ offset 5) (:day value)))

(defn- read-date [ptr offset]
  {:year (ffi/read ptr :int offset)
   :month (ffi/read ptr :uint8 (+ offset 4))
   :day (ffi/read ptr :uint8 (+ offset 5))})

(defn- write-time! [ptr offset value]
  (ffi/write ptr :uint8 offset (:hour value))
  (ffi/write ptr :uint8 (+ offset 1) (:minute value))
  (ffi/write ptr :uint8 (+ offset 2) (:second value))
  (ffi/write ptr :uint (+ offset 4) (:microsecond value)))

(defn- read-time [ptr offset]
  {:hour (ffi/read ptr :uint8 offset)
   :minute (ffi/read ptr :uint8 (+ offset 1))
   :second (ffi/read ptr :uint8 (+ offset 2))
   :microsecond (ffi/read ptr :uint (+ offset 4))})

(defn- write-datetime! [ptr value]
  (write-date! ptr 0 (:date value))
  (write-time! ptr 8 (:time value)))

(defn- read-datetime [ptr]
  {:date (read-date ptr 0)
   :time (read-time ptr 8)})

(defn- with-aggregate-buffers [size call]
  ;; One aligned allocation means there is no partially-allocated failure path.
  ;; All three struct sizes are multiples of their required four-byte alignment.
  (let [allocation-size (* 3 size)
        buffer (ffi/alloc allocation-size)
        min-value buffer
        max-value (+ buffer size)
        out-value (+ buffer (* 2 size))]
    (zero-memory! buffer allocation-size)
    (try
      (call min-value max-value out-value)
      (finally
        (ffi/free buffer)))))

(defn generate-bytes! [ctx test-case min-size max-size]
  ;; hegel_generate_bytes_result_t is {uint8_t*, size_t}. Both released
  ;; libhegel targets are 64-bit, and deriving the offsets from jolt.ffi keeps
  ;; this correct for any same-width pointer/size_t target as well.
  (let [pointer-size (ffi/sizeof :pointer)
        size-size (ffi/sizeof :size_t)
        result-size (+ pointer-size size-size)
        result (ffi/alloc result-size)]
    (zero-memory! result result-size)
    (try
      (let [rc (c-generate-bytes ctx test-case min-size max-size result)]
        (check-draw! ctx :generate-bytes rc)
        (let [data (ffi/read result :pointer 0)
              length (ffi/read result :size_t pointer-size)]
          (ffi/read-array data length)))
      (finally
        ;; Safe for a zeroed or partially populated result, per hegel.h.
        (c-generate-bytes-result-free ctx result)
        (ffi/free result)))))

(defn string-generator-text!
  [ctx {:keys [min-size max-size codec min-codepoint max-codepoint
               categories exclude-categories include-characters
               exclude-characters]}]
  (with-c-string
    codec
    (fn [codec-ptr]
      (with-c-string-array
        categories
        (fn [categories-ptr categories-count]
          (with-c-string-array
            exclude-categories
            (fn [exclude-categories-ptr exclude-categories-count]
              (with-utf8-buffer
                include-characters
                (fn [include-ptr include-length]
                  (with-utf8-buffer
                    exclude-characters
                    (fn [exclude-ptr exclude-length]
                      (call-out!
                       ctx :string-generator-text :pointer
                       #(c-string-generator-text
                         ctx min-size max-size codec-ptr
                         min-codepoint max-codepoint
                         categories-ptr categories-count
                         exclude-categories-ptr exclude-categories-count
                         include-ptr include-length
                         exclude-ptr exclude-length %)))))))))))))

(defn string-generator-regex! [ctx pattern full-match?]
  (with-c-string
    pattern
    (fn [pattern-ptr]
      (call-out! ctx :string-generator-regex :pointer
                 #(c-string-generator-regex
                   ctx pattern-ptr (if full-match? 1 0) ffi/null %)))))

(defn string-generator-email! [ctx]
  (call-out! ctx :string-generator-email :pointer
             #(c-string-generator-email ctx %)))

(defn string-generator-url! [ctx]
  (call-out! ctx :string-generator-url :pointer
             #(c-string-generator-url ctx %)))

(defn string-generator-domain! [ctx max-length]
  (call-out! ctx :string-generator-domain :pointer
             #(c-string-generator-domain ctx max-length %)))

(defn string-generator-free! [ctx generator]
  (c-string-generator-free ctx generator)
  nil)

(defn generate-string! [ctx test-case generator]
  ;; hegel_generate_string_result_t has the same pointer/size_t layout as the
  ;; bytes result, but its data is length-delimited UTF-8 rather than binary.
  (let [pointer-size (ffi/sizeof :pointer)
        size-size (ffi/sizeof :size_t)
        result-size (+ pointer-size size-size)
        result (ffi/alloc result-size)]
    (zero-memory! result result-size)
    (try
      (let [rc (c-generate-string ctx test-case generator result)]
        (check-draw! ctx :generate-string rc)
        (let [data (ffi/read result :pointer 0)
              length (ffi/read result :size_t pointer-size)]
          (ffi/read-bytes data length)))
      (finally
        (c-generate-string-result-free ctx result)
        (ffi/free result)))))

(defn start-span! [ctx test-case label]
  (check-draw! ctx :start-span (c-start-span ctx test-case label)))

(defn stop-span!
  ([ctx test-case]
   (stop-span! ctx test-case false))
  ([ctx test-case discard?]
   (check-draw! ctx :stop-span
                (c-stop-span ctx test-case (if discard? 1 0)))))

(defn new-collection! [ctx test-case min-size max-size]
  (let [out (ffi/alloc (ffi/sizeof :int64))]
    (try
      (let [rc (c-new-collection
                ctx test-case min-size max-size out)]
        (check-draw! ctx :new-collection rc)
        (ffi/read out :int64))
      (finally
        (ffi/free out)))))

(defn collection-more! [ctx test-case collection-id]
  (let [out (ffi/alloc (ffi/sizeof :uint8))]
    (try
      (let [rc (c-collection-more ctx test-case collection-id out)]
        (check-draw! ctx :collection-more rc)
        (not (zero? (ffi/read out :uint8))))
      (finally
        (ffi/free out)))))

(defn collection-reject! [ctx test-case collection-id reason]
  (with-c-string
    reason
    #(check-draw! ctx :collection-reject
                  (c-collection-reject ctx test-case collection-id %))))

(defn new-pool! [ctx test-case]
  (call-draw-out! ctx :new-pool :int64
                  #(c-new-pool ctx test-case %)))

(defn pool-add! [ctx test-case pool-id]
  (call-draw-out! ctx :pool-add :int64
                  #(c-pool-add ctx test-case pool-id %)))

(defn pool-generate! [ctx test-case pool-id consume?]
  (call-draw-out! ctx :pool-generate :int64
                  #(c-pool-generate
                    ctx test-case pool-id (if consume? 1 0) %)))

(defn new-state-machine!
  [ctx test-case rule-names invariant-names]
  (with-c-string-array
    rule-names
    (fn [rules rule-count]
      (with-c-string-array
        invariant-names
        (fn [invariants invariant-count]
          (call-draw-out!
           ctx :new-state-machine :int64
           #(c-new-state-machine
             ctx test-case rules rule-count
             invariants invariant-count %)))))))

(defn state-machine-next-rule! [ctx test-case state-machine-id]
  (let [index
        (call-draw-out!
         ctx :state-machine-next-rule :int64
         #(c-state-machine-next-rule ctx test-case state-machine-id %))]
    (when-not (= state-machine-done index)
      index)))

(defn- generate-fixed-bytes! [ctx test-case operation size draw]
  (let [out (ffi/alloc size)]
    (try
      (let [rc (draw out)]
        (check-draw! ctx operation rc)
        (ffi/read-array out size))
      (finally
        (ffi/free out)))))

(defn generate-uuid! [ctx test-case version]
  (generate-fixed-bytes!
   ctx test-case :generate-uuid 16
   #(c-generate-uuid ctx test-case (or version 0) (if (some? version) 1 0) %)))

(defn generate-ipv4! [ctx test-case]
  (generate-fixed-bytes!
   ctx test-case :generate-ipv4 4
   #(c-generate-ipv4 ctx test-case %)))

(defn generate-ipv6! [ctx test-case]
  (generate-fixed-bytes!
   ctx test-case :generate-ipv6 16
   #(c-generate-ipv6 ctx test-case %)))

(defn generate-date! [ctx test-case min-value max-value]
  (ensure-shim-loaded!)
  (with-aggregate-buffers
    date-size
    (fn [min-ptr max-ptr out-ptr]
      (write-date! min-ptr 0 min-value)
      (write-date! max-ptr 0 max-value)
      (check-draw! ctx :generate-date
                   (c-generate-date ctx test-case min-ptr max-ptr out-ptr))
      (read-date out-ptr 0))))

(defn generate-time! [ctx test-case min-value max-value]
  (ensure-shim-loaded!)
  (with-aggregate-buffers
    time-size
    (fn [min-ptr max-ptr out-ptr]
      (write-time! min-ptr 0 min-value)
      (write-time! max-ptr 0 max-value)
      (check-draw! ctx :generate-time
                   (c-generate-time ctx test-case min-ptr max-ptr out-ptr))
      (read-time out-ptr 0))))

(defn generate-datetime! [ctx test-case min-value max-value]
  (ensure-shim-loaded!)
  (with-aggregate-buffers
    datetime-size
    (fn [min-ptr max-ptr out-ptr]
      (write-datetime! min-ptr min-value)
      (write-datetime! max-ptr max-value)
      (check-draw! ctx :generate-datetime
                   (c-generate-datetime
                    ctx test-case min-ptr max-ptr out-ptr))
      (read-datetime out-ptr))))

(defn target! [ctx test-case value label]
  (with-c-string
    label
    #(check! ctx :target (c-target ctx test-case value %))))

(defn mark-complete! [ctx test-case status origin]
  (with-c-string
    origin
    #(check! ctx :mark-complete
             (c-mark-complete ctx test-case status %))))

(defn run-result-free! [ctx result]
  (c-run-result-free ctx result)
  nil)

(defn run-result-status! [ctx result]
  (call-out! ctx :run-result-status :int
             #(c-run-result-status ctx result %)))

(defn run-result-error! [ctx result]
  (let [ptr (call-out! ctx :run-result-error :pointer
                       #(c-run-result-error ctx result %))]
    (when-not (ffi/null? ptr)
      (ffi/ptr->string ptr))))

(defn run-result-failure-count! [ctx result]
  (call-out! ctx :run-result-failure-count :size_t
             #(c-run-result-failure-count ctx result %)))

(defn run-result-failure! [ctx result index]
  (call-out! ctx :run-result-failure :pointer
             #(c-run-result-failure ctx result index %)))

(defn failure-free! [ctx failure]
  (c-failure-free ctx failure)
  nil)

(defn failure-origin! [ctx failure]
  (let [ptr (call-out! ctx :failure-origin :pointer
                       #(c-failure-origin ctx failure %))]
    (when-not (ffi/null? ptr)
      (ffi/ptr->string ptr))))

(defn failure-reproduction-blob! [ctx failure]
  (let [ptr (call-out! ctx :failure-reproduction-blob :pointer
                       #(c-failure-reproduction-blob ctx failure %))]
    (when-not (ffi/null? ptr)
      (ffi/ptr->string ptr))))

(defn version
  "Return the loaded libhegel version string."
  []
  (let [ctx (context-new!)]
    (try
      (let [ptr (call-out! ctx :version :pointer
                           #(c-version ctx %))]
        (when-not (ffi/null? ptr)
          (ffi/ptr->string ptr)))
      (finally
        (context-free! ctx)))))

(def ^:private compatible-version-checked? (atom false))

(defn ensure-compatible-version!
  "Fail before a property run if the loaded libhegel ABI version is unexpected."
  []
  (when-not @compatible-version-checked?
    (locking compatible-version-checked?
      (when-not @compatible-version-checked?
        (let [actual (version)]
          (when-not (= libhegel-version actual)
            (throw
             (ex-info
              (str "jolt-hegel requires libhegel v" libhegel-version
                   ", but loaded " (pr-str actual) " from "
                   (pr-str library-path))
              {:type ::incompatible-libhegel-version
               :expected libhegel-version
               :actual actual
               :library library-path})))
          (reset! compatible-version-checked? true)))))
  true)
