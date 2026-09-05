(ns hegel.ffi
  "Low-level, ownership-aware bindings to libhegel's C ABI."
  (:require [hegel.ffi.backend :as backend]
            [hegel.host :as host]
            [hegel.native :as native]
            [hegel.version :as version]))

(def libhegel-version
  "The libhegel release whose C ABI this namespace binds."
  version/libhegel-version)

(def library-path
  "The shared object selected for this process."
  native/library-path)

(host/try-catch-all
 (backend/load! library-path)
 cause
 (if (= ::unsupported-runtime-build (:type (ex-data cause)))
   (throw cause)
   (throw
    (ex-info
     (str "could not load libhegel from " (pr-str library-path)
          "; place libhegel v" libhegel-version
          " there or set HEGEL_LIBHEGEL_LIBRARY")
     {:type ::library-load-failed
      :library library-path
      :cause cause}))))

;; Public wrapper names stay stable, but every signature is constructed from
;; the canonical EDN descriptor by hegel.ffi.jolt.
(def c-context-new (backend/function :context-new))
(def c-context-free (backend/function :context-free))
(def c-context-last-error (backend/function :context-last-error))
(def c-settings-new (backend/function :settings-new))
(def c-settings-free (backend/function :settings-free))
(def c-settings-set-backend (backend/function :settings-set-backend))
(def c-settings-set-test-cases (backend/function :settings-set-test-cases))
(def c-settings-set-stateful-step-count (backend/function :settings-set-stateful-step-count))
(def c-settings-set-verbosity (backend/function :settings-set-verbosity))
(def c-settings-set-seed (backend/function :settings-set-seed))
(def c-settings-set-derandomize (backend/function :settings-set-derandomize))
(def c-settings-set-report-multiple-failures (backend/function :settings-set-report-multiple-failures))
(def c-settings-set-database (backend/function :settings-set-database))
(def c-settings-set-database-key (backend/function :settings-set-database-key))
(def c-settings-set-phases (backend/function :settings-set-phases))
(def c-settings-set-suppress-health-check (backend/function :settings-set-suppress-health-check))
(def c-run-start (backend/function :run-start))
(def c-next-test-case (backend/function :next-test-case))
(def c-run-result (backend/function :run-result))
(def c-run-result-free (backend/function :run-result-free))
(def c-run-free (backend/function :run-free))
(def c-test-case-from-blob (backend/function :test-case-from-blob))
(def c-test-case-free (backend/function :test-case-free))
(def c-generate-integer (backend/function :generate-integer))
(def c-generate-boolean (backend/function :generate-boolean))
(def c-generate-float (backend/function :generate-float))
(def c-generate-bytes (backend/function :generate-bytes))
(def c-generate-bytes-result-free (backend/function :generate-bytes-result-free))
(def c-string-generator-text (backend/function :string-generator-text))
(def c-string-generator-regex (backend/function :string-generator-regex))
(def c-string-generator-email (backend/function :string-generator-email))
(def c-string-generator-url (backend/function :string-generator-url))
(def c-string-generator-domain (backend/function :string-generator-domain))
(def c-string-generator-free (backend/function :string-generator-free))
(def c-generate-string (backend/function :generate-string))
(def c-generate-string-result-free (backend/function :generate-string-result-free))
(def c-generate-uuid (backend/function :generate-uuid))
(def c-generate-ipv4 (backend/function :generate-ipv4))
(def c-generate-ipv6 (backend/function :generate-ipv6))
(def c-generate-date (backend/function :generate-date))
(def c-generate-time (backend/function :generate-time))
(def c-generate-datetime (backend/function :generate-datetime))
(def c-target (backend/function :target))
(def c-start-span (backend/function :start-span))
(def c-stop-span (backend/function :stop-span))
(def c-new-collection (backend/function :new-collection))
(def c-collection-more (backend/function :collection-more))
(def c-collection-reject (backend/function :collection-reject))
(def c-collection-free (backend/function :collection-free))
(def c-new-recursion (backend/function :new-recursion))
(def c-recursion-branch (backend/function :recursion-branch))
(def c-recursion-leaf (backend/function :recursion-leaf))
(def c-recursion-retry (backend/function :recursion-retry))
(def c-recursion-finish (backend/function :recursion-finish))
(def c-recursion-free (backend/function :recursion-free))
(def c-new-pool (backend/function :new-pool))
(def c-pool-add (backend/function :pool-add))
(def c-pool-generate (backend/function :pool-generate))
(def c-pool-free (backend/function :pool-free))
(def c-new-state-machine (backend/function :new-state-machine))
(def c-state-machine-next-group (backend/function :state-machine-next-group))
(def c-state-machine-next-rule (backend/function :state-machine-next-rule))
(def c-state-machine-rule-rejected (backend/function :state-machine-rule-rejected))
(def c-state-machine-free (backend/function :state-machine-free))
(def c-mark-complete (backend/function :mark-complete))
(def c-run-result-status (backend/function :run-result-status))
(def c-run-result-error (backend/function :run-result-error))
(def c-run-result-failure-count (backend/function :run-result-failure-count))
(def c-run-result-failure (backend/function :run-result-failure))
(def c-failure-free (backend/function :failure-free))
(def c-failure-origin (backend/function :failure-origin))
(def c-failure-reproduction-blob (backend/function :failure-reproduction-blob))
(def c-version (backend/function :version))

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
(def label-stateful-rule 31)
(def label-recursive 35)

(def state-machine-done
  "Sentinel returned at a state-machine round or machine boundary."
  -9223372036854775808N)

(def no-max-size
  "UINT64_MAX, used by libhegel for an engine-bounded collection size."
  18446744073709551615N)

(defn- context-error [ctx]
  (when (and ctx (not (backend/null? ctx)))
    (let [ptr (c-context-last-error ctx)]
      (when-not (backend/null? ptr)
        (backend/native->string ptr)))))

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

(defn- check-recursion-control!
  "Return :retry for HEGEL_E_RETRY and translate all other draw results."
  [ctx operation rc]
  (if (= -10 rc)
    :retry
    (do
      (check-draw! ctx operation rc)
      :ok)))

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
  (backend/with-native-scope
   (fn []
     (let [out (backend/alloc (backend/sizeof type))]
       (try
         (let [rc (call out)]
           (check! ctx operation rc)
           (backend/read-value out type))
         (finally
           (backend/free out)))))))

(defn- call-draw-out!
  [ctx operation type call]
  (backend/with-native-scope
   (fn []
     (let [out (backend/alloc (backend/sizeof type))]
       (try
         (let [rc (call out)]
           (check-draw! ctx operation rc)
           (backend/read-value out type))
         (finally
           (backend/free out)))))))

(defn- call-nullable-string-out!
  [ctx operation call]
  (let [ptr (call-out! ctx operation :pointer call)]
    (when-not (backend/null? ptr)
      (backend/native->string ptr))))

(defn- with-c-string
  [value call]
  (if (nil? value)
    (call backend/null)
    (backend/with-native-scope
     (fn []
       (let [ptr (backend/string->native (str value))]
         (try
           (call ptr)
           (finally
             (backend/free ptr))))))))

(defn- allocate-c-strings [values]
  (let [pointers (atom [])]
    (host/try-catch-all
     (do
       (doseq [value values]
         (swap! pointers conj (backend/string->native value)))
       @pointers)
     error
     (doseq [ptr @pointers]
       (backend/free ptr))
     (throw error))))

(defn- with-c-string-array
  "Pass nil as a NULL list and a collection, including empty, as a real list."
  [values call]
  (if (nil? values)
    (call backend/null 0)
    (backend/with-native-scope
     (fn []
       (let [pointers (allocate-c-strings values)]
         (try
           (let [pointer-size (backend/sizeof :pointer)
                 ;; A non-NULL pointer distinguishes [] from an unspecified list.
                 array (backend/alloc (max 1 (* pointer-size (count pointers))))]
             (try
               (doseq [[index ptr] (map-indexed vector pointers)]
                 (backend/write-value array :pointer (* index pointer-size) ptr))
               (call array (count pointers))
               (finally
                 (backend/free array))))
           (finally
             (doseq [ptr pointers]
               (backend/free ptr)))))))))

(defn- with-utf8-buffer
  "Pass a length-delimited UTF-8 buffer, preserving empty strings and U+0000."
  [value call]
  (if (nil? value)
    (call backend/null 0)
    (backend/with-native-scope
     (fn []
       (let [ptr (backend/string->native value)]
         (try
           (call ptr (backend/write-utf8 ptr value))
           (finally
             (backend/free ptr))))))))

(defn context-new! []
  (let [ctx (c-context-new)]
    (when (backend/null? ctx)
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
             #(c-run-start ctx settings backend/null backend/null %)))

(defn next-test-case! [ctx run]
  (let [handle (call-out! ctx :next-test-case :pointer
                          #(c-next-test-case ctx run %))]
    (when-not (backend/null? handle)
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
                   ctx settings blob-ptr backend/null backend/null %)))))

(defn test-case-free! [ctx test-case]
  (c-test-case-free ctx test-case)
  nil)

(defn generate-integer! [ctx test-case min-value max-value]
  (backend/with-native-scope
   (fn []
     (let [out (backend/alloc (backend/sizeof :int64))]
       (try
         (let [rc (c-generate-integer
                   ctx test-case min-value max-value out)]
           (check-draw! ctx :generate-integer rc)
           (backend/read-value out :int64))
         (finally
           (backend/free out)))))))

(defn generate-boolean! [ctx test-case probability forced forced?]
  (backend/with-native-scope
   (fn []
     (let [out (backend/alloc (backend/sizeof :uint8))]
       (try
         (let [rc (c-generate-boolean
                   ctx test-case probability
                   (if forced 1 0) (if forced? 1 0) out)]
           (check-draw! ctx :generate-boolean rc)
           (not (zero? (backend/read-value out :uint8))))
         (finally
           (backend/free out)))))))

(defn generate-float!
  [ctx test-case width min-value max-value allow-nan? allow-infinity?
   exclude-min? exclude-max? smallest-nonzero-magnitude]
  (backend/with-native-scope
   (fn []
     (let [out (backend/alloc (backend/sizeof :double))]
       (try
         (let [rc (c-generate-float
                   ctx test-case width
                   (double min-value) (double max-value)
                   (if allow-nan? 1 0) (if allow-infinity? 1 0)
                   (if exclude-min? 1 0) (if exclude-max? 1 0)
                   (double smallest-nonzero-magnitude)
                   out)]
           (check-draw! ctx :generate-float rc)
           (backend/read-value out :double))
         (finally
           (backend/free out)))))))

(defn- zero-memory! [ptr size]
  (dotimes [offset size]
    (backend/write-value ptr :uint8 offset 0)))

(def date-layout
  (backend/layout :hegel/date))

(def time-layout
  (backend/layout :hegel/time))

(def datetime-layout
  (backend/layout :hegel/datetime))

(defn- write-date! [ptr layout prefix value]
  (doseq [field [:year :month :day]]
    (backend/write-field ptr layout (conj prefix field) (get value field))))

(defn- read-date [ptr layout prefix]
  (into {}
        (map (fn [field]
               [field (backend/read-field ptr layout (conj prefix field))])
             [:year :month :day])))

(defn- write-time! [ptr layout prefix value]
  (doseq [field [:hour :minute :second :nanosecond]]
    (backend/write-field ptr layout (conj prefix field) (get value field))))

(defn- read-time [ptr layout prefix]
  (into {}
        (map (fn [field]
               [field (backend/read-field ptr layout (conj prefix field))])
             [:hour :minute :second :nanosecond])))

(defn- write-datetime! [ptr value]
  (write-date! ptr datetime-layout [:date] (:date value))
  (write-time! ptr datetime-layout [:time] (:time value)))

(defn- read-datetime [ptr]
  {:date (read-date ptr datetime-layout [:date])
   :time (read-time ptr datetime-layout [:time])})

(defn- with-aggregate-buffers [layout call]
  (backend/with-native-scope
   (fn []
     (let [size (backend/layout-size layout)
           min-value (backend/alloc size)
           max-value (backend/alloc size)
           out-value (backend/alloc size)]
       (try
        (zero-memory! min-value (backend/layout-size layout))
        (zero-memory! max-value (backend/layout-size layout))
        (zero-memory! out-value (backend/layout-size layout))
        (call min-value max-value out-value)
        (finally
          (backend/free out-value)
          (backend/free max-value)
          (backend/free min-value)))))))

(defn generate-bytes! [ctx test-case min-size max-size]
  ;; hegel_generate_bytes_result_t is {uint8_t*, size_t}. Both released
  ;; libhegel targets are 64-bit, and deriving the offsets from jolt.ffi keeps
  ;; this correct for any same-width pointer/size_t target as well.
  (backend/with-native-scope
   (fn []
     (let [pointer-size (backend/sizeof :pointer)
           size-size (backend/sizeof :size_t)
           result-size (+ pointer-size size-size)
           result (backend/alloc result-size)]
       (zero-memory! result result-size)
       (try
         (let [rc (c-generate-bytes ctx test-case min-size max-size result)]
           (check-draw! ctx :generate-bytes rc)
           (let [data (backend/read-value result :pointer 0)
                 length (backend/read-value result :size_t pointer-size)]
             (backend/read-array data length)))
         (finally
           ;; Safe for a zeroed or partially populated result, per hegel.h.
           (c-generate-bytes-result-free ctx result)
           (backend/free result)))))))

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
                   ctx pattern-ptr (if full-match? 1 0) backend/null %)))))

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
  (backend/with-native-scope
   (fn []
     (let [pointer-size (backend/sizeof :pointer)
           size-size (backend/sizeof :size_t)
           result-size (+ pointer-size size-size)
           result (backend/alloc result-size)]
       (zero-memory! result result-size)
       (try
         (let [rc (c-generate-string ctx test-case generator result)]
           (check-draw! ctx :generate-string rc)
           (let [data (backend/read-value result :pointer 0)
                 length (backend/read-value result :size_t pointer-size)]
             (backend/read-utf8 data length)))
         (finally
           (c-generate-string-result-free ctx result)
           (backend/free result)))))))

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

(defn new-recursion! [ctx test-case max-depth max-leaves]
  (call-draw-out! ctx :new-recursion :pointer
                  #(c-new-recursion
                    ctx test-case max-depth max-leaves %)))

(defn recursion-branch! [ctx test-case recursion depth]
  (not
   (zero?
    (call-draw-out! ctx :recursion-branch :uint8
                    #(c-recursion-branch
                      ctx test-case recursion depth %)))))

(defn recursion-leaf! [ctx test-case recursion]
  (check-recursion-control!
   ctx :recursion-leaf
   (c-recursion-leaf ctx test-case recursion)))

(defn recursion-retry! [ctx test-case recursion]
  (check-draw! ctx :recursion-retry
               (c-recursion-retry ctx test-case recursion)))

(defn recursion-finish! [ctx test-case recursion]
  (check-recursion-control!
   ctx :recursion-finish
   (c-recursion-finish ctx test-case recursion)))

(defn recursion-free! [ctx recursion]
  (c-recursion-free ctx recursion)
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
  (backend/with-native-scope
   (fn []
     (let [values (vec values)
           pointer (backend/alloc
                    (max 1 (* (count values) (backend/sizeof :int64))))]
       (try
         (doseq [[index value] (map-indexed vector values)]
           (backend/write-value pointer :int64 (* index (backend/sizeof :int64)) value))
         (call pointer)
         (finally
           (backend/free pointer)))))))

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
              (backend/with-native-scope
               (fn []
                 (let [machine-out (backend/alloc (backend/sizeof :pointer))
                       concurrency-out (backend/alloc (backend/sizeof :int64))]
                   (try
                     (check-draw!
                      ctx :new-state-machine
                      (c-new-state-machine
                       ctx test-case rules rule-groups rule-count
                       invariants invariant-count 1 1
                       machine-out concurrency-out))
                     (let [concurrency (backend/read-value concurrency-out :int64)]
                       (when-not (= 1 concurrency)
                         (throw
                          (ex-info
                           (str "libhegel returned unexpected sequential "
                                "state-machine concurrency " concurrency)
                           {:type ::invalid-state-machine-concurrency
                            :concurrency concurrency})))
                       (backend/read-value machine-out :pointer))
                     (finally
                       (backend/free concurrency-out)
                       (backend/free machine-out)))))))))))))

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

(defn- generate-fixed-bytes! [ctx operation size draw]
  (backend/with-native-scope
   (fn []
     (let [out (backend/alloc size)]
       (try
         (let [rc (draw out)]
           (check-draw! ctx operation rc)
           (backend/read-array out size))
         (finally
           (backend/free out)))))))

(defn generate-uuid! [ctx test-case version]
  (generate-fixed-bytes!
   ctx :generate-uuid 16
   #(c-generate-uuid ctx test-case (or version 0) (if (some? version) 1 0) %)))

(defn generate-ipv4! [ctx test-case]
  (generate-fixed-bytes!
   ctx :generate-ipv4 4
   #(c-generate-ipv4 ctx test-case %)))

(defn generate-ipv6! [ctx test-case]
  (generate-fixed-bytes!
   ctx :generate-ipv6 16
   #(c-generate-ipv6 ctx test-case %)))

(defn generate-date! [ctx test-case min-value max-value]
  (with-aggregate-buffers
    date-layout
    (fn [min-ptr max-ptr out-ptr]
      (write-date! min-ptr date-layout [] min-value)
      (write-date! max-ptr date-layout [] max-value)
      (check-draw! ctx :generate-date
                   (c-generate-date
                    ctx test-case
                    (backend/by-value min-ptr date-layout)
                    (backend/by-value max-ptr date-layout)
                    out-ptr))
      (read-date out-ptr date-layout []))))

(defn generate-time! [ctx test-case min-value max-value]
  (with-aggregate-buffers
    time-layout
    (fn [min-ptr max-ptr out-ptr]
      (write-time! min-ptr time-layout [] min-value)
      (write-time! max-ptr time-layout [] max-value)
      (check-draw! ctx :generate-time
                   (c-generate-time
                    ctx test-case
                    (backend/by-value min-ptr time-layout)
                    (backend/by-value max-ptr time-layout)
                    out-ptr))
      (read-time out-ptr time-layout []))))

(defn generate-datetime! [ctx test-case min-value max-value]
  (with-aggregate-buffers
    datetime-layout
    (fn [min-ptr max-ptr out-ptr]
      (write-datetime! min-ptr min-value)
      (write-datetime! max-ptr max-value)
      (check-draw! ctx :generate-datetime
                   (c-generate-datetime
                    ctx test-case
                    (backend/by-value min-ptr datetime-layout)
                    (backend/by-value max-ptr datetime-layout)
                    out-ptr))
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
  (call-nullable-string-out!
   ctx :run-result-error #(c-run-result-error ctx result %)))

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
  (call-nullable-string-out!
   ctx :failure-origin #(c-failure-origin ctx failure %)))

(defn failure-reproduction-blob! [ctx failure]
  (call-nullable-string-out!
   ctx :failure-reproduction-blob
   #(c-failure-reproduction-blob ctx failure %)))

(defn version
  "Return the loaded libhegel version string."
  []
  (let [ctx (context-new!)]
    (try
      (call-nullable-string-out! ctx :version #(c-version ctx %))
      (finally
        (context-free! ctx)))))

(def ^:private compatible-version-checked? (atom false))

(defn- check-compatible-version! []
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
      (reset! compatible-version-checked? true))))

(defn ensure-compatible-version!
  "Fail before a property run if the loaded libhegel ABI version is unexpected."
  []
  (when-not @compatible-version-checked?
    #?(:jank (check-compatible-version!)
       :default (locking compatible-version-checked?
                  (check-compatible-version!))))
  true)
