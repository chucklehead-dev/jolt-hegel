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
   _error
   (do (record-error! state :render-error) placeholder-text)))

(defn- redact [state value]
  (host/try-catch-all
   {:ok? true :value ((:redact-fn state) value)}
   _error
   (do (record-error! state :redact-error) {:ok? false})))

(defn- render-redacted [state value]
  (host/try-catch-all
   (let [rendered ((:render-fn state) value)]
     (if (string? rendered)
       rendered
       (do (record-error! state :invalid-render-output)
           placeholder-text)))
   _error
   (do (record-error! state :render-error) placeholder-text)))

(defn- render-value
  "Redact `value`, then render the redacted result. The raw value is never
  passed to render-fn when redaction itself fails, and is never stored."
  [state value]
  (let [redacted (redact state value)]
    (if (:ok? redacted)
      (render-redacted state (:value redacted))
      placeholder-text)))

(defn- configure-width! [ctx handle width]
  (let [options (hffi/printer-options-new! ctx)]
    (try
      (hffi/printer-options-set-max-width! ctx options width)
      ;; The case owns the document and retains its width after this handle
      ;; is freed. Subsequent notes go directly to the case.
      (hffi/printer-free! ctx (hffi/test-case-printer! ctx handle options))
      (finally (hffi/printer-options-free! ctx options)))))

(defn- native-write! [state line]
  (when-not @(:native-disabled? state)
    (host/try-catch-all
     (do (when-let [width (:configure-width state)] @width)
         (hffi/note! (:ctx state) (:handle state) line))
     _error
     (do (record-error! state :native-error)
         (reset! (:native-disabled? state) true)))))

(defn- append-line! [state entry line size]
  (swap! (:entries state) conj entry)
  (swap! (:units state) + size)
  ;; Engine notes inside a speculative region survive even when the region
  ;; aborts. Submit them only after the outermost frontend attempt commits.
  (when (empty? @(:checkpoints state))
    (native-write! state line)))

(defn- append!
  "Form-then-decide: `text` is already fully composed. Reject the whole entry
  when it would exceed the output budget; append at most one fixed
  truncation marker if it still fits. Never slices a string, so truncation
  cannot cut through a multi-byte/multi-unit codepoint."
  [state kind line label]
  (when (and state (not @(:truncated? state)))
    (let [size (inc (portable-data/text-size line))
          used @(:units state)
          budget (:max-output-units state)]
      (if (<= (+ used size) budget)
        (append-line! state (cond-> {:kind kind :text (str line "\n")}
                              label (assoc :label label))
                      line size)
        (let [marker-size (inc (portable-data/text-size truncation-marker))]
          (reset! (:truncated? state) true)
          (when (<= (+ used marker-size) budget)
            (append-line! state {:kind :truncated :text (str truncation-marker "\n")}
                          truncation-marker marker-size)))))))

(defn record-note!
  "Record a note! call's diagnostic text. Preserves the historical joined
  string; note arguments are caller-authored diagnostics, not drawn values,
  so no redaction pass runs over them."
  [state message more]
  (when (and state (not @(:truncated? state)))
    (let [text (host/try-catch-all
                (str/join " " (map str (cons message more)))
                _error
                (do (record-error! state :render-error) placeholder-text))]
      (append! state :note text nil))))

(defn record-draw!
  "Record a labelled draw! call's diagnostic text: pr-str(label), one space,
  render-fn(redact-fn(value)). The raw value is never stored; only the
  rendered label and value text are kept."
  [state label value]
  (when (and state (not @(:truncated? state)))
    (let [label-text (safe-pr-str state label)
          value-text (render-value state value)
          text (str label-text " " value-text)]
      (append! state :draw text label-text))))

(defn start!
  "Create bounded frontend state for a case with diagnostics enabled.
  Configure a custom width on the first committed native write."
  [ctx handle resolved-opts native-output?]
  (let [width (:max-width resolved-opts)]
    {:ctx ctx
     :handle handle
     :configure-width (when (and native-output? (not= default-max-width width))
                        (delay (configure-width! ctx handle width)))
     :native-disabled? (atom (not native-output?))
     :render-fn (:render-fn resolved-opts)
     :redact-fn (:redact-fn resolved-opts)
     :max-output-units (:max-output-units resolved-opts)
     :units (atom 0)
     :entries (atom [])
     :truncated? (atom false)
     :errors (atom [])
     :checkpoints (atom [])}))

(defn finish!
  "Keep bounded entries from an interesting or debug case. Construct local
  text only when the frontend needs it; captured failures get native text."
  [state local-text?]
  (when state
    (let [entries @(:entries state)]
      {:text (when local-text? (str/join (map :text entries)))
       :entries entries
       :truncated? @(:truncated? state)
       :errors @(:errors state)})))

(defn attach-failure-output [snapshot output read-error?]
  (when snapshot
    (cond-> (assoc snapshot :text (if (seq output) (str output "\n") output))
      (and read-error? (< (count (:errors snapshot)) max-errors))
      (update :errors conj {:kind :native-error}))))

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
  "Save bounded entries before a generator tries a speculative draw."
  [state]
  (when state
    (swap! (:checkpoints state) conj
           {:units @(:units state)
            :entries @(:entries state)
            :truncated? @(:truncated? state)
            :errors @(:errors state)}))
  nil)

(defn- pop-checkpoint! [state operation]
  (when (empty? @(:checkpoints state))
    (unbalanced-checkpoint! operation))
  (let [checkpoint (peek @(:checkpoints state))]
    (swap! (:checkpoints state) pop)
    checkpoint))

(defn commit-attempt!
  "Keep the attempt's entries. Flush newly accepted notes to the case printer
  after the outermost attempt commits."
  [state]
  (when state
    (let [checkpoint (pop-checkpoint! state 'commit-attempt!)]
      (when (empty? @(:checkpoints state))
        (doseq [{:keys [text]} (subvec @(:entries state) (count (:entries checkpoint)))]
          ;; Each entry includes one frontend line terminator; hegel_note
          ;; adds its own, preserving newlines inside the user's note.
          (native-write! state (subs text 0 (dec (count text))))))))
  nil)

(defn abort-attempt!
  "Discard the innermost attempt's entries before they reach native output."
  [state]
  (when state
    (let [checkpoint (pop-checkpoint! state 'abort-attempt!)]
      (reset! (:units state) (:units checkpoint))
      (reset! (:entries state) (:entries checkpoint))
      (reset! (:truncated? state) (:truncated? checkpoint))
      (reset! (:errors state) (:errors checkpoint))))
  nil)
