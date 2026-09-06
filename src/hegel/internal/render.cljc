(ns hegel.internal.render
  "Bounded, native-backed structured counterexample printing.

  A render state tracks one test case's diagnostic output. Labelled draw!
  values and note! text are redacted, rendered, size-bounded, and appended to
  a native test-case printer so libhegel can lay them out at read time. All
  bookkeeping here (units, entries, truncation, errors) is plain bounded
  Clojure data; the native printer only ever receives text that already
  passed those bounds. Renderer, redactor, and native-printer failures are
  contained here and must never surface as a property failure or hide the
  original property exception."
  (:require [clojure.string :as str]
            [hegel.ffi :as hffi]
            [hegel.host :as host]
            [hegel.internal.portable-data :as portable-data]
            [hegel.validation :as validation]))

(def default-max-output-units 65536)
(def max-output-units-hard-max 1048576)
(def default-max-width 79)
(def ^:private max-width-hard-max 18446744073709551615N)

(def default-options
  {:render-fn pr-str
   :redact-fn identity
   :max-output-units default-max-output-units
   :max-width default-max-width})

(def ^:private option-keys (set (keys default-options)))

(def ^:private placeholder-text "<counterexample unavailable: renderer failed>")
(def ^:private truncation-marker "<counterexample output truncated>")
(def ^:private max-errors 16)

(defn resolve-options
  "Validate a :counterexample run option (or nil) and return it merged over
  the documented defaults. Throws a usage error for any invalid shape. Safe
  and idempotent to call more than once, including once purely to validate
  before native setup."
  [opts]
  (if (nil? opts)
    default-options
    (do
      (validation/reject-unknown-keys!
       :hegel.core/invalid-option "counterexample" option-keys opts)
      (when (contains? opts :render-fn)
        (validation/require-callable!
         :hegel.core/invalid-option :render-fn (:render-fn opts)))
      (when (contains? opts :redact-fn)
        (validation/require-callable!
         :hegel.core/invalid-option :redact-fn (:redact-fn opts)))
      (when (contains? opts :max-output-units)
        (validation/require-integer-range!
         :hegel.core/invalid-option :max-output-units
         (:max-output-units opts) 1 max-output-units-hard-max))
      (when (contains? opts :max-width)
        (validation/require-integer-range!
         :hegel.core/invalid-option :max-width
         (:max-width opts) 1 max-width-hard-max))
      (merge default-options opts))))

(defn- record-error! [state kind]
  (swap! (:errors state)
         (fn [errors]
           (if (< (count errors) max-errors)
             (conj errors {:kind kind})
             errors))))

(defn- safe-pr-str [state value]
  (host/try-catch-all
   (pr-str value)
   error
   (do (record-error! state :render-error) placeholder-text)))

(defn- redact [state value]
  (host/try-catch-all
   {:ok? true :value ((:redact-fn state) value)}
   error
   (do (record-error! state :redact-error) {:ok? false})))

(defn- render-redacted [state value]
  (host/try-catch-all
   (let [rendered ((:render-fn state) value)]
     (if (string? rendered)
       rendered
       (do (record-error! state :invalid-render-output)
           placeholder-text)))
   error
   (do (record-error! state :render-error) placeholder-text)))

(defn- render-value
  "Redact `value`, then render the redacted result. The raw value is never
  passed to render-fn when redaction itself fails, and is never stored."
  [state value]
  (let [redacted (redact state value)]
    (if (:ok? redacted)
      (render-redacted state (:value redacted))
      placeholder-text)))

(defn- native-write! [state line output-size]
  (swap! (:units state) + output-size)
  (when-not @(:native-disabled? state)
    (host/try-catch-all
     (hffi/note! (:ctx state) (:handle state) line)
     error
     (do (record-error! state :native-error)
         (reset! (:native-disabled? state) true)))))

(defn- append!
  "Form-then-decide: `text` is already fully composed. Reject the whole entry
  when it would exceed the output budget; append at most one fixed
  truncation marker if it still fits. Never slices a string, so truncation
  cannot cut through a multi-byte/multi-unit codepoint."
  [state kind line label]
  (when (and (:enabled? state) (not @(:truncated? state)))
    (let [size (inc (portable-data/text-size line))
          used @(:units state)
          budget (:max-output-units state)]
      (if (<= (+ used size) budget)
        (do (swap! (:entries state) conj
                   (cond-> {:kind kind :text (str line "\n")}
                     label (assoc :label label)))
            (native-write! state line size))
        (let [marker-size (inc (portable-data/text-size truncation-marker))]
          (reset! (:truncated? state) true)
          (when (<= (+ used marker-size) budget)
            (swap! (:entries state) conj
                   {:kind :truncated :text (str truncation-marker "\n")})
            (native-write! state truncation-marker marker-size)))))))

(defn record-note!
  "Record a note! call's diagnostic text. Preserves the historical joined
  string; note arguments are caller-authored diagnostics, not drawn values,
  so no redaction pass runs over them."
  [state message more]
  (when (and (:enabled? state) (not @(:truncated? state)))
    (let [text (host/try-catch-all
                (str/join " " (map str (cons message more)))
                error
                (do (record-error! state :render-error) placeholder-text))]
      (append! state :note text nil))))

(defn record-draw!
  "Record a labelled draw! call's diagnostic text: pr-str(label), one space,
  render-fn(redact-fn(value)). The raw value is never stored; only the
  rendered label and value text are kept."
  [state label value]
  (when (and (:enabled? state) (not @(:truncated? state)))
    (let [label-text (safe-pr-str state label)
          value-text (render-value state value)
          text (str label-text " " value-text)]
      (append! state :draw text label-text))))

(defn start!
  "Create a render state for one test case. `enabled?` reflects the active
  verbosity/final? policy; when false, no native printer is created and every
  record-*! call is a silent no-op. `resolved-opts` is the map returned by
  resolve-options. Printer-options are always freed before this returns,
  even when construction fails."
  [ctx handle enabled? resolved-opts]
  (let [errors (atom [])
        printer
        (when enabled?
          (host/try-catch-all
           (let [printer-options (hffi/printer-options-new! ctx)]
             (try
               (hffi/printer-options-set-max-width!
                ctx printer-options (:max-width resolved-opts))
               (hffi/test-case-printer! ctx handle printer-options)
               (finally
                 (hffi/printer-options-free! ctx printer-options))))
           error
           (do (swap! errors conj {:kind :native-error}) nil)))]
    {:ctx ctx
     :handle handle
     :printer printer
     :enabled? (boolean enabled?)
     :native-disabled? (atom (nil? printer))
     :render-fn (:render-fn resolved-opts)
     :redact-fn (:redact-fn resolved-opts)
     :max-output-units (:max-output-units resolved-opts)
     :units (atom 0)
     :entries (atom [])
     :truncated? (atom false)
     :errors errors
     :checkpoints (atom [])}))

(defn printer-handle
  "The native printer handle owned by `state`, or nil. Exposed so the caller
  can register its cleanup (free before test-case-free) with its own
  test-case lifecycle mechanics."
  [state]
  (:printer state))

(defn finish!
  "Seal and read the native printer, returning a bounded snapshot map with
  :text, :entries, :truncated?, and :errors; or nil when this test case's
  render state was never enabled. Never throws: a printer-value! failure is
  recorded as a bounded diagnostic with :text nil rather than propagating."
  [state]
  (when (:enabled? state)
    (let [printer (:printer state)]
      (if-not printer
        {:text nil
         :entries @(:entries state)
         :truncated? @(:truncated? state)
         :errors @(:errors state)}
        (host/try-catch-all
         {:text (hffi/printer-value! (:ctx state) printer)
          :entries @(:entries state)
          :truncated? @(:truncated? state)
          :errors @(:errors state)}
         error
         (do
           (record-error! state :native-error)
           {:text nil
            :entries @(:entries state)
            :truncated? @(:truncated? state)
            :errors @(:errors state)}))))))

(defn emit!
  "Print a snapshot's rendered text to *err* once. `snapshot` may be nil."
  [snapshot]
  (let [text (:text snapshot)]
    (when (and text (pos? (count text)))
      (binding [*out* *err*]
        (print text))))
  nil)

(defn- unbalanced-checkpoint! [operation]
  (throw
   (ex-info (str operation " has no matching begin-attempt! checkpoint")
            {:type ::unbalanced-checkpoint :operation operation})))

(defn begin-attempt!
  "Push a checkpoint of this render state's bounded bookkeeping (units,
  entries, truncation, errors) so a later abort-attempt! can roll it back.
  Checkpoints and native speculative regions nest. Generator code must call
  exactly one matching commit-attempt! or abort-attempt!."
  [state]
  (when (and state (:enabled? state))
    (let [native? (and (:enabled? state)
                     (:printer state)
                     (not @(:native-disabled? state))
                     (host/try-catch-all
                      (do (hffi/printer-begin-speculative!
                           (:ctx state) (:printer state))
                          true)
                      error
                      (do (record-error! state :native-error)
                          (reset! (:native-disabled? state) true)
                          false)))]
      (swap! (:checkpoints state) conj
             {:units @(:units state)
              :entries @(:entries state)
              :truncated? @(:truncated? state)
              :errors @(:errors state)
              :native-disabled? @(:native-disabled? state)
              :native? native?})))
  nil)

(defn- pop-checkpoint! [state operation]
  (when (empty? @(:checkpoints state))
    (unbalanced-checkpoint! operation))
  (let [checkpoint (peek @(:checkpoints state))]
    (swap! (:checkpoints state) pop)
    checkpoint))

(defn commit-attempt!
  "Discard the innermost checkpoint, keeping every change recorded since the
  matching begin-attempt!."
  [state]
  (when (and state (:enabled? state))
    (let [checkpoint (pop-checkpoint! state 'commit-attempt!)]
      (when (:native? checkpoint)
        (host/try-catch-all
         (hffi/printer-commit-speculative! (:ctx state) (:printer state))
         error
         (do (record-error! state :native-error)
             (reset! (:native-disabled? state) true))))))
  nil)

(defn abort-attempt!
  "Roll units, entries, truncation, and errors back to the innermost
  begin-attempt! checkpoint."
  [state]
  (when (and state (:enabled? state))
    (let [checkpoint (pop-checkpoint! state 'abort-attempt!)
          native-error? (atom false)]
      (when (:native? checkpoint)
        (host/try-catch-all
         (hffi/printer-abort-speculative! (:ctx state) (:printer state))
         error
         (do (reset! native-error? true)
             (reset! (:native-disabled? state) true))))
      (when (and (not (:native-disabled? checkpoint))
                 @(:native-disabled? state))
        (reset! native-error? true))
      (reset! (:units state) (:units checkpoint))
      (reset! (:entries state) (:entries checkpoint))
      (reset! (:truncated? state) (:truncated? checkpoint))
      (reset! (:errors state) (:errors checkpoint))
      (when @native-error?
        (record-error! state :native-error))))
  nil)
