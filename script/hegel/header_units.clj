(ns hegel.header-units
  "Fail-closed temporal unit declarations for the pinned header contract.")

(defn check! [raw-header descriptor]
  ;; The field's spelling and its documented domain both carry semantics.
  ;; This deliberately requires review if a future header changes either;
  ;; uint32 width and aggregate size alone would not detect a unit migration.
  (let [[_ minimum maximum]
        (re-find #"`microsecond` in `\[([0-9]+), ([0-9]+)\]`" raw-header)
        [_ body] (re-find #"(?s)typedef struct \{([^{}]*uint32_t microsecond;[^{}]*)\} hegel_time_t;"
                          raw-header)
        field (some #(when (= :microsecond (:name %)) %)
                    (get-in descriptor [:types :hegel/time :fields]))]
    (when-not (and minimum maximum body)
      (throw (ex-info "Pinned temporal unit declaration changed"
                      {:type ::header-unit-drift})))
    (let [expected {:unit :microsecond
                    :range [(Long/parseLong minimum) (Long/parseLong maximum)]}
          actual (select-keys field [:unit :range])]
      (when-not (= expected actual)
        (throw (ex-info "Canonical temporal unit differs from header"
                        {:type ::descriptor-unit-drift
                         :header expected :descriptor actual})))
      expected)))
