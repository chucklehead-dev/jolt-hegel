(ns hegel.validation
  "Small portable mechanics for public usage validation.

  Callers deliberately retain their namespace-specific error types, option
  names, messages, and semantic domains. This namespace only ensures that an
  invalid public configuration cannot become a shrinkable property failure."
  (:refer-clojure :exclude [boolean]))

(defn usage-error!
  [type message data]
  (throw (ex-info message
                  (assoc data :type type :hegel/usage-error? true))))

(defn require-map!
  ([type label value]
   (require-map! type label value {}))
  ([type label value data]
   (when-not (map? value)
     (usage-error! type (str label " must be a map")
                   (assoc data :value value)))
   value))

(defn reject-unknown-keys!
  ([type label allowed opts]
   (reject-unknown-keys! type label allowed opts {}))
  ([type label allowed opts data]
   (require-map! type label opts data)
   (let [unknown (seq (remove allowed (keys opts)))]
     (when unknown
       (usage-error! type (str label " received unknown options")
                     (assoc data :unknown-keys (vec unknown)))))
   opts))

(defn require-boolean!
  [type option value]
  (when-not (or (= true value) (= false value))
    (usage-error! type (str (name option) " must be a boolean")
                  {option value}))
  value)

(defn require-integer-range!
  [type option value minimum maximum]
  (when-not (and (integer? value) (<= minimum value maximum))
    (usage-error! type
                  (str (name option) " must be an integer from " minimum
                       " through " maximum)
                  {option value :minimum minimum :maximum maximum}))
  value)

(defn require-callable!
  [type option value]
  (when-not (ifn? value)
    (usage-error! type (str (name option) " must be callable")
                  {option value}))
  value)

(defn require-callable-or-nil!
  [type option value]
  (when-not (or (nil? value) (ifn? value))
    (usage-error! type (str (name option) " must be nil or callable")
                  {option value}))
  value)
