(ns hegel.header-layout-check
  "Compare compiler measurements with layouts implied by canonical EDN."
  (:require [hegel.header-check :as signatures]
            [hegel.header-constants :as constants]))

(def primitive-names
  {:c/bool :bool :c/int8 :int8_t :c/uint8 :uint8_t
   :c/int16 :int16_t :c/uint16 :uint16_t
   :c/int32 :int32_t :c/uint32 :uint32_t
   :c/int64 :int64_t :c/uint64 :uint64_t
   :c/size :size_t :c/float :float :c/double :double})

(defn- equal! [path expected actual]
  (when-not (= expected actual)
    (throw (ex-info "Compiler layout differs from canonical ABI"
                    {:path path :compiler expected :descriptor actual}))))

(defn- measured-primitive [measurement key]
  (let [size (get-in measurement [:primitives key])
        align (get-in measurement [:primitive-alignments key])]
    (when-not (and (pos-int? size) (pos-int? align))
      (throw (ex-info "Missing compiler primitive measurement" {:primitive key})))
    {:size size :align align}))

(defn- align-up [offset alignment]
  (* alignment (quot (+ offset (dec alignment)) alignment)))

(defn canonical-layout
  ([descriptor measurement type] (canonical-layout descriptor measurement type #{}))
  ([descriptor measurement type visiting]
   (when (contains? visiting type)
     (throw (ex-info "Recursive by-value descriptor" {:type type})))
   (let [entry (get-in descriptor [:types type])
         visiting (conj visiting type)]
     (cond
       (and (vector? type) (= :pointer (first type)))
       (measured-primitive measurement :pointer)

       (contains? primitive-names type)
       (measured-primitive measurement (get primitive-names type))

       (contains? #{:string :function-pointer} (:kind entry))
       (measured-primitive measurement :pointer)

       (= :struct (:kind entry))
       (let [result
             (reduce (fn [{:keys [end align fields]} field]
                       (let [layout (canonical-layout descriptor measurement (:type field) visiting)
                             offset (align-up end (:align layout))]
                         {:end (+ offset (:size layout))
                          :align (max align (:align layout))
                          :fields (assoc fields (:name field) offset)}))
                     {:end 0 :align 1 :fields {}} (:fields entry))]
         {:size (align-up (:end result) (:align result))
          :align (:align result) :fields (:fields result)})

       :else (throw (ex-info "Unsupported canonical layout type" {:type type}))))))

(defn check! [snapshot descriptor measurement]
  (equal! [:char-bit] 8 (:char-bit measurement))
  (equal! [:enums :values]
          (:enums measurement)
          (into {} (map (fn [[name members]]
                          [(keyword name)
                           (into {} (map (fn [[k v]] [(keyword k) (constants/symbolic-value v)])
                                         members))])
                        (:enums snapshot))))
  (equal! [:defines :values]
          (:defines measurement)
          (into {} (map (fn [[k v]] [(keyword k) (constants/symbolic-value v)])
                        (:defines snapshot))))
  (doseq [[type c-name] primitive-names
          :let [entry (get-in descriptor [:types type])
                size (:size (measured-primitive measurement c-name))
                bits (:bits entry)
                expected (if (= :pointer-width bits)
                           (* 8 (get-in measurement [:primitives :pointer]))
                           bits)]]
    (equal! [:types type :bits] (* 8 size) expected)
    (let [kind (cond (= type :c/bool) :boolean
                     (contains? #{:c/float :c/double} type) :float
                     :else :integer)]
      (equal! [:types type :kind] kind (:kind entry))
      (when (contains? #{:integer :boolean} kind)
        (equal! [:types type :signed?]
                (contains? #{:c/int8 :c/int16 :c/int32 :c/int64} type)
                (:signed? entry)))))
  (equal! [:structs :names] (set (map keyword (keys (:structs snapshot))))
          (set (keys (:structs measurement))))
  (doseq [[c-name _] (:structs snapshot)]
    (equal! [:structs c-name]
            (get-in measurement [:structs (keyword c-name)])
            (canonical-layout descriptor measurement (signatures/type-id c-name))))
  ;; C enum signedness is implementation-defined. These two enums cross the
  ;; ABI through int32 cells/returns; compare storage width/alignment and range.
  (doseq [name ["hegel_result_t" "hegel_run_status_t"]
          :let [values (vals (get-in measurement [:enums (keyword name)]))]]
    (equal! [:enums name :layout]
            (get-in measurement [:enum-layouts (keyword name)])
            (measured-primitive measurement :int32_t))
    (when-not (and (seq values) (every? #(<= -2147483648 % 2147483647) values))
      (throw (ex-info "Enum values do not fit binding int32 storage" {:enum name}))))
  {:scalars (count primitive-names) :structs (count (:structs snapshot)) :enums 2})
