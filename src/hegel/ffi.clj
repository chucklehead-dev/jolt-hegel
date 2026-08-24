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
(ffi/defcfn c-settings-set-stateful-step-count
  "hegel_settings_set_stateful_step_count"
  [:pointer :pointer :int64] :int)
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

;; Run lifecycle. Since libhegel 0.30.1, next-test-case performs generation and
;; shrinking on the calling thread and may block until it offers the next case.
;; run-free drops any remaining exploration. Keep both calls collect-safe.
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
(ffi/defcfn c-generate-date "hegel_generate_date"
  [:pointer :pointer
   [:by-value [:struct [[:year :int32] [:month :uint8] [:day :uint8]]]]
   [:by-value [:struct [[:year :int32] [:month :uint8] [:day :uint8]]]]
   :pointer]
  :int)
(ffi/defcfn c-generate-time "hegel_generate_time"
  [:pointer :pointer
   [:by-value [:struct [[:hour :uint8] [:minute :uint8] [:second :uint8]
                        [:microsecond :uint32]]]]
   [:by-value [:struct [[:hour :uint8] [:minute :uint8] [:second :uint8]
                        [:microsecond :uint32]]]]
   :pointer]
  :int)
(ffi/defcfn c-generate-datetime "hegel_generate_datetime"
  [:pointer :pointer
   [:by-value
    [:struct
     [[:date [:struct [[:year :int32] [:month :uint8] [:day :uint8]]]]
      [:time [:struct [[:hour :uint8] [:minute :uint8] [:second :uint8]
                       [:microsecond :uint32]]]]]]]
   [:by-value
    [:struct
     [[:date [:struct [[:year :int32] [:month :uint8] [:day :uint8]]]]
      [:time [:struct [[:hour :uint8] [:minute :uint8] [:second :uint8]
                       [:microsecond :uint32]]]]]]]
   :pointer]
  :int)
(ffi/defcfn c-target "hegel_target"
  [:pointer :pointer :double :pointer] :int)
(ffi/defcfn c-start-span "hegel_start_span"
  [:pointer :pointer :uint64] :int)
(ffi/defcfn c-stop-span "hegel_stop_span"
  [:pointer :pointer :uint8] :int)
(ffi/defcfn c-new-collection "hegel_new_collection"
  [:pointer :pointer :uint64 :uint64 :pointer] :int)
(ffi/defcfn c-collection-more "hegel_collection_more"
  [:pointer :pointer :pointer :pointer] :int)
(ffi/defcfn c-collection-reject "hegel_collection_reject"
  [:pointer :pointer :pointer :pointer] :int)
(ffi/defcfn c-collection-free "hegel_collection_free"
  [:pointer :pointer] :int)
(ffi/defcfn c-new-pool "hegel_new_pool"
  [:pointer :pointer :pointer] :int)
(ffi/defcfn c-pool-add "hegel_pool_add"
  [:pointer :pointer :pointer :pointer] :int)
(ffi/defcfn c-pool-generate "hegel_pool_generate"
  [:pointer :pointer :pointer :uint8 :pointer] :int)
(ffi/defcfn c-pool-free "hegel_pool_free"
  [:pointer :pointer] :int)
(ffi/defcfn c-new-state-machine "hegel_new_state_machine"
  [:pointer :pointer :pointer :pointer :size_t :pointer :size_t
   :int64 :int64 :pointer :pointer]
  :int)
(ffi/defcfn c-state-machine-next-group "hegel_state_machine_next_group"
  [:pointer :pointer :pointer :pointer] :int)
(ffi/defcfn c-state-machine-next-rule "hegel_state_machine_next_rule"
  [:pointer :pointer :pointer :int64 :pointer] :int)
(ffi/defcfn c-state-machine-rule-rejected "hegel_state_machine_rule_rejected"
  [:pointer :pointer :pointer :int64] :int)
(ffi/defcfn c-state-machine-free "hegel_state_machine_free"
  [:pointer :pointer] :int)
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
(def run-status-failed-nondeterministic 3)

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
  "Sentinel returned at a state-machine round or machine boundary."
  -9223372036854775808N)

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

(defn error?
  "True when error reports a libhegel harness/ABI failure rather than generated
  property behavior."
  [error]
  (= ::error (:type (ex-data error))))

(defn- call-out!
  [ctx operation type call]
  (ffi/with-out [out type]
    (let [rc (call out)]
      (check! ctx operation rc)
      (ffi/read out type))))

(defn- call-draw-out!
  [ctx operation type call]
  (ffi/with-out [out type]
    (let [rc (call out)]
      (check-draw! ctx operation rc)
      (ffi/read out type))))

(defn- with-c-string
  [value call]
  (if (nil? value)
    (call ffi/null)
    (ffi/with-c-string [ptr (str value)]
      (call ptr))))

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

(defn settings-set-stateful-step-count! [ctx settings value]
  (check! ctx :settings-set-stateful-step-count
          (c-settings-set-stateful-step-count ctx settings value)))

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

(def date-layout
  (ffi/layout
   [:struct [[:year :int32]
             [:month :uint8]
             [:day :uint8]]]))

(def time-layout
  (ffi/layout
   [:struct [[:hour :uint8]
             [:minute :uint8]
             [:second :uint8]
             [:microsecond :uint32]]]))

(def datetime-layout
  (ffi/layout
   [:struct
    [[:date [:struct [[:year :int32]
                      [:month :uint8]
                      [:day :uint8]]]]
     [:time [:struct [[:hour :uint8]
                      [:minute :uint8]
                      [:second :uint8]
                      [:microsecond :uint32]]]]]]))

(defn- write-date! [ptr layout prefix value]
  (doseq [field [:year :month :day]]
    (ffi/write-field ptr layout (conj prefix field) (get value field))))

(defn- read-date [ptr layout prefix]
  (into {}
        (map (fn [field]
               [field (ffi/read-field ptr layout (conj prefix field))])
             [:year :month :day])))

(defn- write-time! [ptr layout prefix value]
  (doseq [field [:hour :minute :second :microsecond]]
    (ffi/write-field ptr layout (conj prefix field) (get value field))))

(defn- read-time [ptr layout prefix]
  (into {}
        (map (fn [field]
               [field (ffi/read-field ptr layout (conj prefix field))])
             [:hour :minute :second :microsecond])))

(defn- write-datetime! [ptr value]
  (write-date! ptr datetime-layout [:date] (:date value))
  (write-time! ptr datetime-layout [:time] (:time value)))

(defn- read-datetime [ptr]
  {:date (read-date ptr datetime-layout [:date])
   :time (read-time ptr datetime-layout [:time])})

(defn- with-aggregate-buffers [layout call]
  (ffi/with-layout [min-value layout]
    (ffi/with-layout [max-value layout]
      (ffi/with-layout [out-value layout]
        (zero-memory! min-value (ffi/layout-size layout))
        (zero-memory! max-value (ffi/layout-size layout))
        (zero-memory! out-value (ffi/layout-size layout))
        (call min-value max-value out-value)))))

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
  (call-draw-out! ctx :new-collection :pointer
                  #(c-new-collection ctx test-case min-size max-size %)))

(defn collection-more! [ctx test-case collection]
  (not
   (zero?
    (call-draw-out! ctx :collection-more :uint8
                    #(c-collection-more ctx test-case collection %)))))

(defn collection-reject! [ctx test-case collection reason]
  (with-c-string
    reason
    #(check-draw! ctx :collection-reject
                  (c-collection-reject ctx test-case collection %))))

(defn collection-free! [ctx collection]
  (c-collection-free ctx collection)
  nil)

(defn new-pool! [ctx test-case]
  (call-draw-out! ctx :new-pool :pointer
                  #(c-new-pool ctx test-case %)))

(defn pool-add! [ctx test-case pool]
  (call-draw-out! ctx :pool-add :int64
                  #(c-pool-add ctx test-case pool %)))

(defn pool-generate! [ctx test-case pool consume?]
  (call-draw-out! ctx :pool-generate :int64
                  #(c-pool-generate
                    ctx test-case pool (if consume? 1 0) %)))

(defn pool-free! [ctx pool]
  (c-pool-free ctx pool)
  nil)

(defn- with-int64-array [values call]
  (let [values (vec values)]
    (ffi/with-alloc [pointer (max 1 (* (count values) (ffi/sizeof :int64)))]
      (doseq [[index value] (map-indexed vector values)]
        (ffi/write pointer :int64 (* index (ffi/sizeof :int64)) value))
      (call pointer))))

(defn new-state-machine!
  [ctx test-case rule-names invariant-names]
  (with-c-string-array
    rule-names
    (fn [rules rule-count]
      (with-int64-array
        (repeat rule-count 0)
        (fn [rule-groups]
          (with-c-string-array
            invariant-names
            (fn [invariants invariant-count]
              (ffi/with-out [machine-out :pointer]
                (ffi/with-out [concurrency-out :int64]
                  (check-draw!
                   ctx :new-state-machine
                   (c-new-state-machine
                    ctx test-case rules rule-groups rule-count
                    invariants invariant-count 1 1
                    machine-out concurrency-out))
                  (let [concurrency (ffi/read concurrency-out :int64)]
                    (when-not (= 1 concurrency)
                      (throw
                       (ex-info
                        (str "libhegel returned unexpected sequential "
                             "state-machine concurrency " concurrency)
                        {:type ::invalid-state-machine-concurrency
                         :concurrency concurrency})))
                    (ffi/read machine-out :pointer)))))))))))

(defn state-machine-next-group! [ctx test-case state-machine]
  (let [group
        (call-draw-out!
         ctx :state-machine-next-group :int64
         #(c-state-machine-next-group ctx test-case state-machine %))]
    (when-not (= state-machine-done group)
      group)))

(defn state-machine-next-rule! [ctx test-case state-machine]
  (let [index
        (call-draw-out!
         ctx :state-machine-next-rule :int64
         #(c-state-machine-next-rule ctx test-case state-machine 0 %))]
    (when-not (= state-machine-done index)
      index)))

(defn state-machine-rule-rejected! [ctx test-case state-machine]
  (check! ctx :state-machine-rule-rejected
          (c-state-machine-rule-rejected ctx test-case state-machine 0)))

(defn state-machine-free! [ctx state-machine]
  (c-state-machine-free ctx state-machine)
  nil)

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
  (with-aggregate-buffers
    date-layout
    (fn [min-ptr max-ptr out-ptr]
      (write-date! min-ptr date-layout [] min-value)
      (write-date! max-ptr date-layout [] max-value)
      (check-draw! ctx :generate-date
                   (c-generate-date ctx test-case min-ptr max-ptr out-ptr))
      (read-date out-ptr date-layout []))))

(defn generate-time! [ctx test-case min-value max-value]
  (with-aggregate-buffers
    time-layout
    (fn [min-ptr max-ptr out-ptr]
      (write-time! min-ptr time-layout [] min-value)
      (write-time! max-ptr time-layout [] max-value)
      (check-draw! ctx :generate-time
                   (c-generate-time ctx test-case min-ptr max-ptr out-ptr))
      (read-time out-ptr time-layout []))))

(defn generate-datetime! [ctx test-case min-value max-value]
  (with-aggregate-buffers
    datetime-layout
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
