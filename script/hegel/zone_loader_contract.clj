(require '[hegel.ffi.jolt :as backend] '[jolt.ffi :as ffi])

(backend/load! (first *command-line-args*))

(defn call [id & args]
  (apply (backend/function id) args))

;; Use a source owned by this probe. Its last reference is freed before
;; the callback arena closes, and the source is never made process-global.
(with-open [arena (ffi/confined-arena)]
  (let [ctx (call :context-new)
        source-out (ffi/alloc arena :pointer)
        requested "Test/Fractional"
        string-layout (backend/layout :hegel/string)
        zone-layout (backend/layout :hegel/zone-data)
        offset-layout (backend/layout :hegel/timezone-offset)
        name-view (ffi/alloc arena string-layout)
        ;; Zeroed zone data uses HEGEL_ZONE_AFTER_HOLD.
        zone (ffi/alloc arena zone-layout)
        instant (ffi/alloc arena (backend/layout :hegel/timestamp))
        offset (ffi/alloc arena offset-layout)
        names (atom [])
        callback-error (atom nil)
        releases (atom [])
        loader (ffi/callback arena
                 (fn [data callback-ctx source name]
                   (try
                     (let [text (ffi/read-bytes (ffi/read-field name string-layout :data)
                                               (ffi/read-field name string-layout :len))]
                       (swap! names conj text)
                       (call :zone-source-add-zone callback-ctx source name data))
                     (catch Throwable error
                       (reset! callback-error error)
                       -1)))
                 [:pointer :pointer :pointer
                  [:by-value [:struct [[:data :pointer] [:len :size_t]]]]] :int32)
        release (ffi/callback arena #(swap! releases conj %) [:pointer] :void)]
    (try
      (ffi/write-field name-view string-layout :data (ffi/string->ptr arena requested))
      (ffi/write-field name-view string-layout :len (count requested))
      (ffi/write-field zone zone-layout [:initial :utoff] 20700)
      (assert (zero? (call :zone-source-new ctx source-out)))
      (let [source (ffi/read source-out :pointer)]
        (try
          (assert (zero? (call :zone-source-set-loader ctx source loader zone release)))
          (dotimes [_ 2]
            (let [rc (call :timezone-offset-at-in ctx source name-view instant offset)]
              (when-let [error @callback-error] (throw error))
              (assert (zero? rc)))
            (assert (= 20700 (ffi/read-field offset offset-layout :offset_seconds))))
          (assert (= [requested] @names))
          (assert (empty? @releases))
          (finally (assert (zero? (call :zone-source-free ctx source))))))
      (assert (= [zone] @releases))
      (println "PASS by-value timezone loader, cached lookup and callback release")
      (finally (assert (zero? (call :context-free ctx)))))))
