(ns hegel.header-check
  "Offline comparison of canonical ABI signatures with the pinned C header.
  This does not establish target compiler layout or native calling conventions."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [hegel.header-snapshot :as header]))

(defn- require-equal! [path expected actual]
  (when-not (= expected actual)
    (throw (ex-info "Canonical ABI differs from pinned C header"
                    {:path path :header expected :descriptor actual}))))

(defn type-id [c-name]
  (case c-name
    "HegelRecursion" :hegel/recursion
    "hegel_generate_bytes_result_t" :hegel/bytes-result
    "hegel_generate_string_result_t" :hegel/string-result
    (if-let [[_ name] (re-matches #"hegel_(.+)_t" c-name)]
      (keyword "hegel" (str/replace name "_" "-"))
      (throw (ex-info "Unknown header type name" {:name c-name})))))

(def scalar-types
  {"void" :c/void "bool" :c/bool
   "int8" :c/int8 "uint8" :c/uint8
   "int16" :c/int16 "uint16" :c/uint16
   "int32" :c/int32 "uint32" :c/uint32
   "int64" :c/int64 "uint64" :c/uint64
   "size" :c/size "float" :c/float "double" :c/double
   ;; Result codes are the signed C enum whose negative values are essential.
   "hegel_result_t" :c/int32
   ;; The binding stores this nonnegative enum in an int32 out-cell. Compiler
   ;; width/range checks are a separate gate; C does not fix enum signedness.
   "hegel_run_status_t" :c/int32
   "output-callback" :hegel/output-callback
   "const-char*" :c/string "const-char**" [:pointer :c/string]
   ;; A returned string buffer is length-delimited, not the :c/string ABI.
   "char*" [:pointer :c/int8]})

(defn canonical-type [snapshot c-type argument?]
  (or (get scalar-types c-type)
      (let [clean (-> c-type (str/replace #"\bconst\b" "") str/trim)
            [_ base stars] (re-matches #"(.+?)\s*(\*+)" clean)]
        (if stars
          (let [base (-> base str/trim
                         (str/replace #"^(u?int(?:8|16|32|64))_t$" "$1")
                         (str/replace #"^size_t$" "size"))]
            (reduce (fn [t _] [:pointer t])
                    (canonical-type snapshot base false) stars))
          (let [id (type-id clean)]
            (when-not (or (contains? (:structs snapshot) clean)
                          (some #{clean} (:opaque-handles snapshot)))
              (throw (ex-info "Unresolved header type" {:type c-type})))
            (if (and argument? (contains? (:structs snapshot) clean))
              [:by-value id]
              id))))))

(defn check! [snapshot descriptor]
  (require-equal! [:library :header :commit]
                  (get-in snapshot [:provenance :commit])
                  (get-in descriptor [:library :header :commit]))
  (let [header-functions (into {} (map (juxt :name identity) (:functions snapshot)))
        descriptor-functions (into {} (map (fn [[id f]] [(:symbol f) [id f]])
                                          (:functions descriptor)))]
    (require-equal! [:functions :symbols] (set (keys header-functions))
                    (set (keys descriptor-functions)))
    (require-equal! [:functions :unique-symbols] (count (:functions descriptor))
                    (count descriptor-functions))
    (doseq [[symbol {:keys [args return]}] header-functions
            :let [[id f] (get descriptor-functions symbol)]]
      (require-equal! [:functions id :args]
                      (mapv #(canonical-type snapshot (:type %) true) args)
                      (:args f))
      (require-equal! [:functions id :return]
                      (canonical-type snapshot return true) (:return f))))
  (require-equal! [:types :structs]
                  (set (map type-id (keys (:structs snapshot))))
                  (set (keep (fn [[id t]] (when (= :struct (:kind t)) id))
                             (:types descriptor))))
  (doseq [[name fields] (:structs snapshot)]
    (require-equal! [:types (type-id name) :fields]
                    (mapv (fn [f] {:name (keyword (:name f))
                                   :type (canonical-type snapshot (:type f) false)}) fields)
                    (mapv #(select-keys % [:name :type])
                          (get-in descriptor [:types (type-id name) :fields]))))
  (require-equal! [:types :opaque-handles]
                  (set (map type-id (:opaque-handles snapshot)))
                  (set (keep (fn [[id t]] (when (= :opaque (:kind t)) id))
                             (:types descriptor))))
  (require-equal! [:types :callbacks]
                  (set (map type-id (keys (:callbacks snapshot))))
                  (set (keep (fn [[id t]] (when (= :function-pointer (:kind t)) id))
                             (:types descriptor))))
  (doseq [[name callback] (:callbacks snapshot)]
    (require-equal! [:types (type-id name) :args]
                    (mapv #(canonical-type snapshot (:type %) true) (:args callback))
                    (get-in descriptor [:types (type-id name) :args]))
    (require-equal! [:types (type-id name) :return]
                    (canonical-type snapshot (:return callback) true)
                    (get-in descriptor [:types (type-id name) :return])))
  {:functions (count (:functions snapshot))
   :structs (count (:structs snapshot))
   :opaque-handles (count (:opaque-handles snapshot))
   :callbacks (count (:callbacks snapshot))})

(defn -main [& _]
  (prn (check! (header/snapshot)
               (edn/read-string (slurp "resources/hegel/abi.edn")))))
