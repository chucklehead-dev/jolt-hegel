(ns hegel.ffi
  "Low-level, ownership-aware bindings to libhegel's C ABI."
  (:require [hegel.ffi.backend :as backend]
            [hegel.internal.signed-bytes :as signed-bytes]
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

(def c-state-machine-add-rule (backend/function :state-machine-add-rule))

(def c-state-machine-add-invariant (backend/function :state-machine-add-invariant))

(def c-state-machine-start (backend/function :state-machine-start))

(def c-failure-caveat (backend/function :failure-caveat))

(def c-run-start-blob (backend/function :run-start-blob))

(def c-run-result-error-kind (backend/function :run-result-error-kind))

(def c-settings-set-nondeterminism-strictness (backend/function :settings-set-nondeterminism-strictness))

(def c-settings-set-stop-on-first-failure (backend/function :settings-set-stop-on-first-failure))

;; Public wrapper names stay stable, but every signature is constructed from
;; the canonical EDN descriptor by hegel.ffi.jolt.
(def c-context-new (backend/function :context-new))
(def c-context-free (backend/function :context-free))
(def c-context-last-error (backend/function :context-last-error))
(def c-settings-new (backend/function :settings-new))
(def c-settings-free (backend/function :settings-free))
(def c-settings-set-backend (backend/function :settings-set-backend))
(def c-settings-set-test-cases (backend/function :settings-set-test-cases))

(def c-settings-set-verbosity (backend/function :settings-set-verbosity))
(def c-settings-set-seed (backend/function :settings-set-seed))
(def c-settings-set-derandomize (backend/function :settings-set-derandomize))
(def c-settings-set-report-multiple-failures (backend/function :settings-set-report-multiple-failures))
(def c-settings-set-show-statistics (backend/function :settings-set-show-statistics))
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
(def c-test-case-wants-output (backend/function :test-case-wants-output))
(def c-test-case-wants-detail (backend/function :test-case-wants-detail))
(def c-output-stderr-callback (backend/function :output-stderr-callback))
(def c-failure-output (backend/function :failure-output))
(def c-failure-diagnostic (backend/function :failure-diagnostic))
(def c-test-case-clone (backend/function :test-case-clone))
(def c-generate-integer (backend/function :generate-integer))
(def c-generate-integer-big (backend/function :generate-integer-big))
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
(def c-generate-string-result-free (backend/function :owned-string-free))
(def c-generate-uuid (backend/function :generate-uuid))
(def c-generate-ipv4 (backend/function :generate-ipv4))
(def c-generate-ipv6 (backend/function :generate-ipv6))
(def c-date-options-new (backend/function :date-options-new))
(def c-date-options-free (backend/function :date-options-free))
(def c-date-options-set-weekdays (backend/function :date-options-set-weekdays))
(def c-date-options-set-months (backend/function :date-options-set-months))
(def c-validate-date (backend/function :validate-date))
(def c-validate-time (backend/function :validate-time))
(def c-validate-datetime (backend/function :validate-datetime))
(def c-generate-date (backend/function :generate-date))
(def c-generate-time (backend/function :generate-time))
(def c-generate-datetime (backend/function :generate-datetime))
(def c-validate-timestamp (backend/function :validate-timestamp))
(def c-generate-timestamp (backend/function :generate-timestamp))
(def c-target (backend/function :target))
(def c-event (backend/function :event))
(def c-event-value (backend/function :event-value))
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
;; These four alternates are deliberately Jolt-only selections. On every other
;; host they alias the established binding and are not selected by the current
;; sequential API; the future concurrent executor owns their use.
(def c-pool-add-collect-safe
  #?(:jolt (backend/function :pool-add :collect-safe)
     :default c-pool-add))
(def c-pool-generate-collect-safe
  #?(:jolt (backend/function :pool-generate :collect-safe)
     :default c-pool-generate))
(def c-pool-free (backend/function :pool-free))
(def c-new-state-machine (backend/function :new-state-machine))
(def c-state-machine-next-group (backend/function :state-machine-next-group))
(def c-state-machine-next-rule (backend/function :state-machine-next-rule))
(def c-state-machine-rule-rejected (backend/function :state-machine-rule-rejected))
(def c-state-machine-should-check-invariant
  (backend/function :state-machine-should-check-invariant))
(def c-state-machine-next-rule-collect-safe
  #?(:jolt (backend/function :state-machine-next-rule :collect-safe)
     :default c-state-machine-next-rule))
(def c-state-machine-rule-rejected-collect-safe
  #?(:jolt (backend/function :state-machine-rule-rejected :collect-safe)
     :default c-state-machine-rule-rejected))

(defn concurrent-state-machine-routes-supported?
  "Return whether the concurrent worker routes have distinct collect-safe ABI bindings.

  This is a native-free shape check used by the concurrent capability
  preflight.  Calling it does not allocate a context, state machine, or test
  case; the actual route wrappers remain below and are still Jolt-only at
  runtime."
  []
  #?(:jolt (and (not (identical? c-state-machine-next-rule
                                c-state-machine-next-rule-collect-safe))
                (not (identical? c-state-machine-rule-rejected
                                c-state-machine-rule-rejected-collect-safe)))
     :default false))
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
(def c-note (backend/function :note))
(def c-printer-options-new (backend/function :printer-options-new))
(def c-printer-options-free (backend/function :printer-options-free))
(def c-printer-options-set-max-width (backend/function :printer-options-set-max-width))
(def c-printer-new (backend/function :printer-new))
(def c-printer-free (backend/function :printer-free))
(def c-printer-if-break (backend/function :printer-if-break))
(def c-printer-text (backend/function :printer-text))
(def c-printer-breakable (backend/function :printer-breakable))
(def c-printer-comment (backend/function :printer-comment))
(def c-printer-hard-break (backend/function :printer-hard-break))
(def c-printer-begin-group (backend/function :printer-begin-group))
(def c-printer-end-group (backend/function :printer-end-group))
(def c-printer-shift-indent (backend/function :printer-shift-indent))
(def c-printer-deferred (backend/function :printer-deferred))
(def c-printer-begin-speculative (backend/function :printer-begin-speculative))
(def c-printer-commit-speculative (backend/function :printer-commit-speculative))
(def c-printer-abort-speculative (backend/function :printer-abort-speculative))
(def c-printer-resolve (backend/function :printer-resolve))
(def c-printer-is-live (backend/function :printer-is-live))
(def c-printer-value (backend/function :printer-value))
(def c-printer-value-result-free (backend/function :printer-value-result-free))
(def c-test-case-printer (backend/function :test-case-printer))

(def status-valid 0)
(def status-invalid 1)
(def status-overrun 2)
(def status-interesting 3)

(def run-status-passed 0)
(def run-status-failed 1)
(def run-status-error 2)


(def label-list 13119983822548632080N)
(def label-set 11358891839481870632N)
(def label-map 16070072663295028606N)
(def label-tuple 873836240999684414N)
(def label-one-of 12493077146293715814N)
(def label-optional 13104029776529188484N)
(def label-flat-map 15845607820421195832N)
(def label-filter 15157945300862412314N)
(def label-mapped 7608984536048634331N)
(def label-stateful-rule 8946221671406004969N)
(def label-recursive 11814535412921032636N)

(def state-machine-done
  "Sentinel returned at a state-machine round or machine boundary."
  -9223372036854775808N)

(def no-max-size
  "UINT64_MAX, used by libhegel for an engine-bounded collection size."
  18446744073709551615N)

(def default-max-size 18446744073709551614N)
(def filter-attempts 3)

(def string-layout (backend/layout :hegel/string))

(defn- read-string-view [ptr]
  (backend/read-utf8 (backend/read-field ptr string-layout [:data])
                     (backend/read-field ptr string-layout [:len])))

(defn- context-error [ctx]
  (when (and ctx (not (backend/null? ctx)))
    (backend/with-native-scope
     (fn []
       (let [out (backend/alloc (backend/layout-size string-layout))]
         (try
           (c-context-last-error out ctx)
           (read-string-view out)
           (finally (backend/free out))))))))

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

(defn- call-string-out! [ctx operation call]
  (backend/with-native-scope
   (fn []
     (let [out (backend/alloc (backend/layout-size string-layout))]
       (try (check! ctx operation (call out))
            (read-string-view out)
            (finally (backend/free out)))))))

(defn- call-nullable-string-out! [ctx operation call]
  (let [ptr (call-out! ctx operation :pointer call)]
    (when-not (backend/null? ptr) (read-string-view ptr))))

(defn- with-string-view [value call]
  (backend/with-native-scope
   (fn []
     (let [[data length] (backend/alloc-utf8 (str (or value "")))]
       (try
         (let [view (backend/alloc (backend/layout-size string-layout))]
           (try
             (backend/write-field view string-layout [:data] data)
             (backend/write-field view string-layout [:len] length)
             (call view)
             (finally (backend/free view))))
         (finally (backend/free data)))))))

(defn- with-c-string [value call]
  ;; Optional UTF-8 strings distinguish nil from present-but-empty.
  (if (nil? value) (call backend/null) (with-string-view value call)))

(defn- with-string-value [value call]
  (with-string-view value #(call (backend/by-value % string-layout))))

(defn- with-c-string-array
  "Pass nil as a NULL list and a collection, including empty, as a real list."
  [values call]
  (if (nil? values)
    (call backend/null 0)
    (backend/with-native-scope
     (fn []
       (let [n (count values)
             stride (backend/layout-size string-layout)
             array (backend/alloc (max 1 (* stride n)))
             buffers (volatile! [])]
         (try
           (doseq [[index text] (map-indexed vector values)]
             (let [[data length] (backend/alloc-utf8 text)
                   offset (* index stride)]
               (vswap! buffers conj data)
               (backend/write-value array :pointer offset data)
               (backend/write-value array :size_t (+ offset (backend/sizeof :pointer)) length)))
           (call array n)
           (finally
             (doseq [data @buffers] (backend/free data))
             (backend/free array))))))))

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

(def ^:dynamic *stateful-step-count*
  "Per-run state-machine round budget; libhegel now takes it at construction."
  50)

(defn settings-set-nondeterminism-strictness! [ctx settings value]
  (check! ctx :settings-set-nondeterminism-strictness
          (c-settings-set-nondeterminism-strictness ctx settings value)))

(defn settings-set-stop-on-first-failure! [ctx settings value]
  (check! ctx :settings-set-stop-on-first-failure
          (c-settings-set-stop-on-first-failure ctx settings (if value 1 0))))

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

(defn settings-set-show-statistics! [ctx settings enabled?]
  (check! ctx :settings-set-show-statistics
          (c-settings-set-show-statistics ctx settings (if enabled? 1 0))))

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

(defn- run-output-sink [stderr?]
  (if stderr? (c-output-stderr-callback) backend/null))

(defn run-start!
  ([ctx settings] (run-start! ctx settings false))
  ([ctx settings stderr?]
   (call-out! ctx :run-start :pointer
              #(c-run-start ctx settings (run-output-sink stderr?) backend/null %))))

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
  (with-string-value
    blob
    (fn [blob-ptr]
      (call-out! ctx :test-case-from-blob :pointer
                 #(c-test-case-from-blob
                   ctx settings blob-ptr backend/null backend/null %)))))

(defn test-case-free! [ctx test-case]
  (c-test-case-free ctx test-case)
  nil)

(defn test-case-wants-output? [ctx test-case]
  (not (zero? (call-out! ctx :test-case-wants-output :uint8
                         #(c-test-case-wants-output ctx test-case %)))))

(defn test-case-wants-detail? [ctx test-case]
  (not (zero? (call-out! ctx :test-case-wants-detail :uint8
                         #(c-test-case-wants-detail ctx test-case %)))))

(defn test-case-clone! [ctx test-case]
  (call-draw-out! ctx :test-case-clone :pointer
             #(c-test-case-clone ctx test-case %)))

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

(defn- with-owned-byte-buffer [size call]
  ;; Allocation failure leaves earlier enclosing buffers owned by their caller.
  ;; Preserve the primary exception if cleanup also fails; never free twice.
  (let [pointer (backend/alloc size)
        result (host/try-catch-all
                (call pointer)
                error
                (do
                  (host/try-catch-all (backend/free pointer) _cleanup nil)
                  (throw error)))]
    (backend/free pointer)
    result))

(defn generate-integer-big!
  "Draw an arbitrary-width integer from inclusive integer bounds.
  All three signed little-endian buffers and the size output are caller-owned."
  [ctx test-case min-value max-value]
  (let [minimum (signed-bytes/encode min-value)
        maximum (signed-bytes/encode max-value)
        capacity (max (count minimum) (count maximum))]
    (backend/with-native-scope
      (fn []
        (with-owned-byte-buffer (count minimum)
          (fn [min-pointer]
            (doseq [[index octet] (map-indexed vector minimum)]
              (backend/write-value min-pointer :uint8 index octet))
            (with-owned-byte-buffer (count maximum)
              (fn [max-pointer]
                (doseq [[index octet] (map-indexed vector maximum)]
                  (backend/write-value max-pointer :uint8 index octet))
                (with-owned-byte-buffer capacity
                  (fn [out-pointer]
                    (with-owned-byte-buffer (backend/sizeof :size_t)
                      (fn [length-pointer]
                        (backend/write-value length-pointer :size_t 0 0)
                        (check-draw! ctx :generate-integer-big
                          (c-generate-integer-big
                            ctx test-case min-pointer (count minimum)
                            max-pointer (count maximum) out-pointer capacity
                            length-pointer))
                        (let [length (backend/read-value length-pointer :size_t)]
                          (when-not (<= 1 length capacity)
                            (throw (ex-info "native integer length exceeds caller buffer"
                                            {:type ::error
                                             :operation :generate-integer-big
                                             :length length :capacity capacity})))
                          ;; The C contract sign-fills the whole output buffer.
                          (signed-bytes/decode
                            (mapv #(backend/read-value out-pointer :uint8 %)
                                  (range capacity))))))))))))))))

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

(def timestamp-layout
  (backend/layout :hegel/timestamp))

(defn- write-timestamp! [ptr {:keys [second nanosecond]}]
  (backend/write-field ptr timestamp-layout [:second] (long second))
  (backend/write-field ptr timestamp-layout [:nanosecond] nanosecond))

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
              (with-string-value
                include-characters
                (fn [include-ptr]
                  (with-string-value
                    exclude-characters
                    (fn [exclude-ptr]
                      (call-out!
                       ctx :string-generator-text :pointer
                       #(c-string-generator-text
                         ctx min-size max-size codec-ptr
                         min-codepoint max-codepoint
                         categories-ptr categories-count
                         exclude-categories-ptr exclude-categories-count
                         include-ptr
                         exclude-ptr %)))))))))))))

(defn string-generator-regex! [ctx pattern full-match?]
  (with-string-value
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

(defn- pool-add-with! [raw ctx test-case pool]
  (call-draw-out! ctx :pool-add :int64
                  #(raw ctx test-case pool %)))

(defn pool-add! [ctx test-case pool]
  (pool-add-with! c-pool-add ctx test-case pool))

(defn pool-add-collect-safe!
  "Concurrent-executor-only Jolt route. Existing sequential callers use
  pool-add!, which remains ordinary."
  [ctx test-case pool]
  (pool-add-with! c-pool-add-collect-safe ctx test-case pool))

(defn- pool-generate-with! [raw ctx test-case pool consume?]
  (call-draw-out! ctx :pool-generate :int64
                  #(raw
                    ctx test-case pool (if consume? 1 0) %)))

(defn pool-generate! [ctx test-case pool consume?]
  (pool-generate-with! c-pool-generate ctx test-case pool consume?))

(defn pool-generate-collect-safe!
  "Concurrent-executor-only Jolt route. Existing sequential callers use
  pool-generate!, which remains ordinary."
  [ctx test-case pool consume?]
  (pool-generate-with! c-pool-generate-collect-safe
                       ctx test-case pool consume?))

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

(defn- with-out-buffer [type call]
  (let [out (backend/alloc (backend/sizeof type))]
    (try
      (call out)
      (finally
        (backend/free out)))))

(defn state-machine-free! [ctx state-machine]
  (c-state-machine-free ctx state-machine)
  nil)

(defn new-state-machine-with-concurrency!
  "Declare and start a native machine. Rule numbering follows declaration order."
  [ctx test-case rule-names rule-groups invariant-names min-concurrency max-concurrency]
  (when-not (= (count rule-names) (count rule-groups))
    (throw (ex-info "state-machine rule groups must be parallel to rule names"
                    {:type ::invalid-argument :argument :rule-groups})))
  (let [machine (call-draw-out! ctx :new-state-machine :pointer
                 #(c-new-state-machine ctx test-case min-concurrency max-concurrency
                                       *stateful-step-count* %))]
    (host/try-catch-all
     (do
       (doseq [[rule group] (map vector rule-names rule-groups)]
         (with-string-value (if (map? rule) (:name rule) rule)
           (fn [name]
             (with-int64-array [group]
               #(check-draw! ctx :state-machine-add-rule
                  (c-state-machine-add-rule ctx test-case machine name
                                            (if (map? rule) (:weight rule) 1.0) % 1))))))
       (doseq [invariant invariant-names]
         (with-string-value invariant
           #(check-draw! ctx :state-machine-add-invariant
              ;; Preserve the public every-step invariant policy.
              (c-state-machine-add-invariant ctx test-case machine % 1))))
       {:state-machine machine
        :concurrency (call-draw-out! ctx :state-machine-start :int64
                       #(c-state-machine-start ctx test-case machine %))})
     error
     (host/try-catch-all (state-machine-free! ctx machine) _cleanup nil)
     (throw error))))

(defn new-state-machine!
  "Sequential compatibility wrapper: zero rule groups, min = max = 1
  concurrency. Returns only the owned machine handle. If libhegel selects a
  concurrency other than 1, the machine is freed before throwing."
  [ctx test-case rule-names invariant-names]
  (let [rule-names (vec rule-names)
        {:keys [state-machine concurrency]}
        (new-state-machine-with-concurrency!
         ctx test-case rule-names (repeat (count rule-names) 0)
         invariant-names 1 1)]
    (when-not (= 1 concurrency)
      (host/try-catch-all
       (state-machine-free! ctx state-machine)
       _cleanup nil)
      (throw
       (ex-info
        (str "libhegel returned unexpected sequential "
             "state-machine concurrency " concurrency)
        {:type ::invalid-state-machine-concurrency
         :concurrency concurrency})))
    state-machine))

(defn state-machine-next-group! [ctx test-case state-machine]
  (backend/with-native-scope
   (fn []
     (with-out-buffer :int64
       (fn [concurrency]
         (let [group (call-draw-out! ctx :state-machine-next-group :int64
                       #(c-state-machine-next-group ctx test-case state-machine % concurrency))]
           (when-not (= state-machine-done group) group)))))))

(defn state-machine-should-check-invariant!
  "Return libhegel's coordinator-only invariant decision for INVARIANT-INDEX.
  This is a draw operation and therefore preserves STOP_TEST control flow."
  [ctx test-case state-machine invariant-index]
  (not (zero?
        (call-draw-out! ctx :state-machine-should-check-invariant :uint8
                        #(c-state-machine-should-check-invariant
                          ctx test-case state-machine invariant-index %)))))

(defn- state-machine-next-rule-with! [raw ctx test-case state-machine worker-index]
  (let [index
        (call-draw-out!
         ctx :state-machine-next-rule :int64
         #(raw ctx test-case state-machine worker-index %))]
    (when-not (= state-machine-done index)
      index)))

(defn state-machine-next-rule!
  ([ctx test-case state-machine]
   (state-machine-next-rule! ctx test-case state-machine 0))
  ([ctx test-case state-machine worker-index]
   (state-machine-next-rule-with! c-state-machine-next-rule
                                  ctx test-case state-machine worker-index)))

(defn state-machine-next-rule-collect-safe!
  "Concurrent-executor-only Jolt route. `state-machine-next-rule!` is the
  ordinary sequential hot path."
  [ctx test-case state-machine worker-index]
  (state-machine-next-rule-with! c-state-machine-next-rule-collect-safe
                                 ctx test-case state-machine worker-index))

(defn- state-machine-rule-rejected-with! [raw ctx test-case state-machine worker-index]
  (check! ctx :state-machine-rule-rejected
          (raw ctx test-case state-machine worker-index)))

(defn state-machine-rule-rejected!
  ([ctx test-case state-machine]
   (state-machine-rule-rejected! ctx test-case state-machine 0))
  ([ctx test-case state-machine worker-index]
   (state-machine-rule-rejected-with! c-state-machine-rule-rejected
                                      ctx test-case state-machine worker-index)))

(defn state-machine-rule-rejected-collect-safe!
  "Concurrent-executor-only Jolt route. Existing sequential rejection
  reporting remains ordinary."
  [ctx test-case state-machine worker-index]
  (state-machine-rule-rejected-with!
   c-state-machine-rule-rejected-collect-safe
   ctx test-case state-machine worker-index))

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

(defn- with-date-options [ctx weekdays months call]
  (if (or weekdays months)
    (let [options (call-out! ctx :date-options-new :pointer #(c-date-options-new ctx %))]
      (try
        (when weekdays
          (check! ctx :date-options-set-weekdays (c-date-options-set-weekdays ctx options weekdays)))
        (when months
          (check! ctx :date-options-set-months (c-date-options-set-months ctx options months)))
        (call options)
        (finally
          (check! ctx :date-options-free (c-date-options-free ctx options)))))
    (call backend/null)))

(defn- with-temporal-bounds [kind minimum maximum call]
  (let [layout (case kind :date date-layout :time time-layout :datetime datetime-layout
                     :timestamp timestamp-layout)]
    (with-aggregate-buffers layout
      (fn [lo hi out]
        (case kind
          :date (do (write-date! lo layout [] minimum) (write-date! hi layout [] maximum))
          :time (do (write-time! lo layout [] minimum) (write-time! hi layout [] maximum))
          :datetime (do (write-datetime! lo minimum) (write-datetime! hi maximum))
          :timestamp (do (write-timestamp! lo minimum) (write-timestamp! hi maximum)))
        (call (backend/by-value lo layout) (backend/by-value hi layout) out)))))

(defn validate-temporal-bounds!
  "Validate a filtered date or a precision-constrained temporal range without a draw."
  [kind minimum maximum precision weekdays months]
  (let [ctx (context-new!)]
    (try
      (with-temporal-bounds kind minimum maximum
        (fn [lo hi _out]
          (case kind
            :date (with-date-options ctx weekdays months
                    (fn [options]
                      (check! ctx :validate-date (c-validate-date ctx lo hi options))))
            :time (check! ctx :validate-time (c-validate-time ctx lo hi precision))
            :datetime (check! ctx :validate-datetime (c-validate-datetime ctx lo hi precision))
            :timestamp (check! ctx :validate-timestamp (c-validate-timestamp ctx lo hi precision)))))
      (finally (context-free! ctx)))))

(defn generate-date!
  ([ctx tc minimum maximum] (generate-date! ctx tc minimum maximum nil nil))
  ([ctx tc minimum maximum weekdays months]
   (with-date-options ctx weekdays months
     (fn [options]
       (with-temporal-bounds :date minimum maximum
         (fn [lo hi out]
           (check-draw! ctx :generate-date (c-generate-date ctx tc lo hi options out))
           (read-date out date-layout [])))))))

(defn generate-time!
  ([ctx tc minimum maximum] (generate-time! ctx tc minimum maximum 0))
  ([ctx tc minimum maximum precision]
   (with-temporal-bounds :time minimum maximum
     (fn [lo hi out]
       (check-draw! ctx :generate-time (c-generate-time ctx tc lo hi precision out))
       (read-time out time-layout [])))))

(defn generate-datetime!
  ([ctx tc minimum maximum] (generate-datetime! ctx tc minimum maximum 0))
  ([ctx tc minimum maximum precision]
   (with-temporal-bounds :datetime minimum maximum
     (fn [lo hi out]
       (check-draw! ctx :generate-datetime (c-generate-datetime ctx tc lo hi precision out))
       (read-datetime out)))))

(defn generate-timestamp! [ctx tc minimum maximum precision]
  (with-temporal-bounds :timestamp minimum maximum
    (fn [lo hi out]
      (check-draw! ctx :generate-timestamp (c-generate-timestamp ctx tc lo hi precision out))
      {:second (backend/read-field out timestamp-layout [:second])
       :nanosecond (backend/read-field out timestamp-layout [:nanosecond])})))

(defn target! [ctx test-case value label]
  (with-string-value
    label
    #(check! ctx :target (c-target ctx test-case value %))))

(defn event! [ctx test-case label]
  (with-string-value
    label
    #(check! ctx :event (c-event ctx test-case %))))

(defn event-value! [ctx test-case value label]
  (with-string-value
    label
    #(check! ctx :event-value (c-event-value ctx test-case value %))))

(defn mark-complete! [ctx test-case status origin summary detail]
  (with-c-string origin
    (fn [origin-ptr]
      (with-c-string summary
        (fn [summary-ptr]
          (with-c-string detail
            #(check! ctx :mark-complete
                     (c-mark-complete ctx test-case status origin-ptr summary-ptr %))))))))

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
  (call-string-out!
   ctx :failure-origin #(c-failure-origin ctx failure %)))

(defn failure-reproduction-blob! [ctx failure]
  (call-nullable-string-out!
   ctx :failure-reproduction-blob
   #(c-failure-reproduction-blob ctx failure %)))

(defn note! [ctx test-case text]
  (with-string-value
    text
    #(check! ctx :note (c-note ctx test-case %))))

(defn- printer-options-arg [options]
  (if (nil? options) backend/null options))

(defn printer-options-new! [ctx]
  (call-out! ctx :printer-options-new :pointer
             #(c-printer-options-new ctx %)))

(defn printer-options-free! [ctx options]
  (c-printer-options-free ctx options)
  nil)

(defn printer-options-set-max-width! [ctx options value]
  (check! ctx :printer-options-set-max-width
          (c-printer-options-set-max-width ctx options value)))

(defn printer-new!
  "Create a standalone printer. `options` may be nil to use engine defaults."
  [ctx options]
  (call-out! ctx :printer-new :pointer
             #(c-printer-new ctx (printer-options-arg options) %)))

(defn printer-free! [ctx printer]
  (c-printer-free ctx printer)
  nil)

(defn printer-text! [ctx printer text]
  (with-string-value
    text
    #(check! ctx :printer-text (c-printer-text ctx printer %))))

(defn printer-breakable! [ctx printer text]
  (with-string-value
    text
    #(check! ctx :printer-breakable (c-printer-breakable ctx printer %))))

(defn printer-if-break! [ctx printer text]
  (with-string-value
    text
    #(check! ctx :printer-if-break (c-printer-if-break ctx printer %))))

(defn printer-comment! [ctx printer text]
  (with-string-value
    text
    #(check! ctx :printer-comment (c-printer-comment ctx printer %))))

(defn printer-hard-break! [ctx printer]
  (check! ctx :printer-hard-break (c-printer-hard-break ctx printer)))

(defn printer-begin-group! [ctx printer indent text]
  (with-string-value
    text
    #(check! ctx :printer-begin-group
             (c-printer-begin-group ctx printer indent %))))

(defn printer-end-group! [ctx printer text]
  (with-string-value
    text
    #(check! ctx :printer-end-group (c-printer-end-group ctx printer %))))

(defn printer-shift-indent! [ctx printer delta]
  (check! ctx :printer-shift-indent (c-printer-shift-indent ctx printer delta)))

(defn printer-deferred!
  "Open a deferred sub-printer. The returned handle is caller-owned like any
  other printer and must be freed independently; resolving the root does not
  release the deferred handle."
  [ctx printer]
  (call-out! ctx :printer-deferred :pointer
             #(c-printer-deferred ctx printer %)))

(defn printer-begin-speculative! [ctx printer]
  (check! ctx :printer-begin-speculative (c-printer-begin-speculative ctx printer)))

(defn printer-commit-speculative! [ctx printer]
  (check! ctx :printer-commit-speculative (c-printer-commit-speculative ctx printer)))

(defn printer-abort-speculative! [ctx printer]
  (check! ctx :printer-abort-speculative (c-printer-abort-speculative ctx printer)))

(defn printer-resolve! [ctx printer]
  (check! ctx :printer-resolve (c-printer-resolve ctx printer)))

(defn printer-is-live! [ctx printer]
  (not
   (zero?
    (call-out! ctx :printer-is-live :uint8
               #(c-printer-is-live ctx printer %)))))

(def printer-value-result-layout
  (backend/layout :hegel/printer-value-result))

(defn printer-value!
  "Resolve `printer` to its rendered UTF-8 text.
  The result struct starts zeroed to {NULL,0}; its data is decoded before the
  engine-owned buffer is released, the host result struct is always freed, and
  a primary call/decode error is preserved over any cleanup failure without a
  double free."
  [ctx printer]
  (backend/with-native-scope
   (fn []
     (let [result-size (backend/layout-size printer-value-result-layout)
           result (backend/alloc result-size)]
       (zero-memory! result result-size)
       (try
         (let [value (host/try-catch-all
                      (let [rc (c-printer-value ctx printer result)]
                        (check! ctx :printer-value rc)
                        (let [data (backend/read-field
                                    result printer-value-result-layout [:data])
                              length (backend/read-field
                                      result printer-value-result-layout [:len])]
                          (backend/read-utf8 data length)))
                      error
                      (do
                        (host/try-catch-all
                         (c-printer-value-result-free ctx result)
                         _cleanup nil)
                        (throw error)))]
           (c-printer-value-result-free ctx result)
           value)
         (finally
           (backend/free result)))))))

(defn test-case-printer!
  "Create a printer bound to `test-case`. `options` may be nil to use engine
  defaults."
  [ctx test-case options]
  (call-out! ctx :test-case-printer :pointer
             #(c-test-case-printer ctx test-case (printer-options-arg options) %)))

(defn version
  "Return the loaded libhegel version string."
  []
  (let [ctx (context-new!)]
    (try
      (call-string-out! ctx :version #(c-version ctx %))
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

(defn failure-caveat! [ctx failure]
  (call-nullable-string-out! ctx :failure-caveat #(c-failure-caveat ctx failure %)))

(defn failure-output! [ctx failure]
  (call-nullable-string-out! ctx :failure-output #(c-failure-output ctx failure %)))

(defn failure-diagnostic! [ctx failure]
  (call-nullable-string-out! ctx :failure-diagnostic #(c-failure-diagnostic ctx failure %)))

(defn run-start-blob!
  ([ctx settings blob] (run-start-blob! ctx settings blob false))
  ([ctx settings blob stderr?]
   (with-string-value blob
     (fn [value]
       (call-out! ctx :run-start-blob :pointer
                  #(c-run-start-blob ctx settings value (run-output-sink stderr?) backend/null %))))))

(defn run-result-error-kind! [ctx result]
  (call-out! ctx :run-result-error-kind :int32
             #(c-run-result-error-kind ctx result % backend/null)))

(def c-label-from-name (backend/function :label-from-name))

(defn label-from-name!
  "Derive a stable span label from a frontend-qualified UTF-8 name."
  [ctx name]
  (with-string-value name
    (fn [value]
      ;; Normalize the unsigned word so hosts with a signed uint64 carrier
      ;; still return the same mathematical integer.
      (backend/with-native-scope
       (fn []
         (let [out (backend/alloc 8)]
           (try
             (check! ctx :label-from-name (c-label-from-name (or ctx backend/null) value out))
             (let [value (backend/read-value out :uint64)]
               (if (neg? value) (+ 18446744073709551616N value) value))
             (finally (backend/free out)))))))))

(def c-pool-restore (backend/function :pool-restore))
(defn pool-restore! [ctx test-case pool variable-id]
  (check-draw! ctx :pool-restore (c-pool-restore ctx test-case pool variable-id)))

(defn settings-new-for-profile! [ctx profile]
  (with-string-value profile
    #(call-out! ctx :settings-new-for-profile :pointer
                (fn [out] ((backend/function :settings-new-for-profile) ctx % out)))))

(defn settings-value! [ctx settings function-id type]
  (call-out! ctx function-id type #((backend/function function-id) ctx settings %)))

(defn settings-seed! [ctx settings]
  (backend/with-native-scope
   (fn []
     (with-out-buffer :uint8
       (fn [specified]
         (let [seed (call-out! ctx :settings-get-seed :uint64
                      #((backend/function :settings-get-seed) ctx settings % specified))]
           (when-not (zero? (backend/read-value specified :uint8))
             (if (neg? seed) (+ 18446744073709551616N seed) seed))))))))

(defn settings-set-unbounded-choices! [ctx settings enabled?]
  (check! ctx :settings-set-unbounded-choices
          ((backend/function :settings-set-unbounded-choices) ctx settings (if enabled? 1 0))))

(defn settings-set-print-blob! [ctx settings enabled?]
  (check! ctx :settings-set-print-blob
          ((backend/function :settings-set-print-blob) ctx settings (if enabled? 1 0))))

(def c-label-combine (backend/function :label-combine))
(defn label-combine! [labels]
  (backend/with-native-scope
   (fn []
     (let [labels (vec labels)
           data (backend/alloc (max 1 (* 8 (count labels))))]
       (try
         (doseq [[index label] (map-indexed vector labels)]
           (backend/write-value data :int64 (* 8 index)
                                (long (if (> label 9223372036854775807N)
                                        (- label 18446744073709551616N)
                                        #?(:cljr (System.Convert/ToInt64 label)
                                           :default label)))))
         (let [value (call-out! backend/null :label-combine :uint64
                       #(c-label-combine backend/null data (count labels) %))]
           (if (neg? value) (+ 18446744073709551616N value) value))
         (finally (backend/free data)))))))

(defn settings-set-test-location! [ctx settings {:keys [file line namespace name]}]
  (with-string-value file
    (fn [file]
      (with-string-value namespace
        (fn [namespace]
          (with-string-value name
            #(check! ctx :settings-set-test-location
                     ((backend/function :settings-set-test-location)
                      ctx settings file line namespace %))))))))

(defn pause-faults! [ctx]
  (check! ctx :pause-faults ((backend/function :pause-faults) ctx)))

(defn unpause-faults! [ctx]
  (check! ctx :unpause-faults ((backend/function :unpause-faults) ctx)))

(defn grammar-new! [ctx source {:keys [start max-size max-depth imports alphabet]}]
  (let [builder (with-string-value source
                  #(call-out! ctx :grammar-builder-new :pointer
                              (fn [out] ((backend/function :grammar-builder-new) ctx % out))))]
    (try
      (loop [index 0]
        (let [path (backend/with-native-scope
                    (fn []
                      (let [out (backend/alloc (backend/layout-size string-layout))]
                        (try
                          (let [rc ((backend/function :grammar-builder-file) ctx builder index out)]
                            (when-not (= -11 rc)
                              (check! ctx :grammar-builder-file rc)
                              (read-string-view out)))
                          (finally (backend/free out))))))]
          (when path
            (with-c-string (get imports path)
              #(check! ctx :grammar-builder-provide
                       ((backend/function :grammar-builder-provide) ctx builder index %)))
            (recur (inc index)))))
      (let [alphabet-handle (when alphabet (string-generator-text! ctx alphabet))]
        (try
          (with-c-string start
            #(call-out! ctx :grammar-new :pointer
                        (fn [out] ((backend/function :grammar-new) ctx builder %
                                    max-size max-depth (or alphabet-handle backend/null)
                                    backend/null 0 out))))
          (finally (when alphabet-handle (string-generator-free! ctx alphabet-handle)))))
      (finally ((backend/function :grammar-builder-free) ctx builder)))))

(defn grammar-free! [ctx grammar]
  (check! ctx :grammar-free ((backend/function :grammar-free) ctx grammar)))

(defn generate-grammar! [ctx test-case grammar]
  (backend/with-native-scope
   (fn []
     (let [size (backend/layout-size string-layout)
           out (backend/alloc size)]
       (zero-memory! out size)
       (try
         (check-draw! ctx :generate-grammar
                      ((backend/function :generate-grammar) ctx test-case grammar 0 out))
         (read-string-view out)
         (finally
           (c-generate-string-result-free ctx out)
           (backend/free out)))))))
