(ns hegel.abi
  "Canonical, native-code-free model and validation of the libhegel C ABI."
  (:refer-clojure :exclude [descriptor])
  (:require #?(:jank [hegel.abi-data :as abi-data]
               :default [clojure.edn :as edn])
            [clojure.set :as set]
            [hegel.host :as host]))

(def ^:private descriptor-resource "hegel/abi.edn")

(defonce ^:private descriptor*
  (delay #?(:jank abi-data/descriptor
            :default (edn/read-string (host/resource-text descriptor-resource)))))

(defonce ^:private selected-backend-report* (atom nil))

(defn descriptor [] @descriptor*)
(defn types [] (:types (descriptor)))
(defn functions [] (:functions (descriptor)))

(defn- type-reference? [types value]
  (cond
    (keyword? value) (contains? types value)
    (vector? value)
    (let [[kind target] value]
      (and (contains? #{:pointer :by-value} kind)
           (= 2 (count value))
           (type-reference? types target)))
    :else false))

(defn- referenced-types [value]
  (cond
    (keyword? value) #{value}
    (vector? value) (referenced-types (second value))
    :else #{}))

(defn- validate-type! [all-types type-id descriptor]
  (let [{:keys [kind bits signed? fields args return]} descriptor]
    (case kind
      :void nil
      :boolean
      (when-not (and (pos-int? bits) (= false signed?))
        (throw (ex-info "invalid boolean descriptor"
                        {:type-id type-id :descriptor descriptor})))
      :integer
      (when-not (and (or (pos-int? bits) (= :pointer-width bits))
                     (boolean? signed?))
        (throw (ex-info "invalid integer descriptor"
                        {:type-id type-id :descriptor descriptor})))
      :float
      (when-not (contains? #{32 64} bits)
        (throw (ex-info "invalid floating-point descriptor"
                        {:type-id type-id :descriptor descriptor})))
      :string nil
      :opaque nil
      :struct
      (do
        (when-not (and (vector? fields) (seq fields))
          (throw (ex-info "a struct requires ordered fields"
                          {:type-id type-id :descriptor descriptor})))
        (let [names (mapv :name fields)]
          (when-not (= (count names) (count (set names)))
            (throw (ex-info "struct field names must be unique"
                            {:type-id type-id :fields names}))))
        (doseq [{field-name :name field-type :type} fields]
          (when-not (and (keyword? field-name)
                         (type-reference? all-types field-type))
            (throw (ex-info "invalid struct field"
                            {:type-id type-id
                             :field field-name
                             :field-type field-type})))))
      :function-pointer
      (do
        (when-not (and (vector? args) (every? #(type-reference? all-types %) args))
          (throw (ex-info "invalid function-pointer arguments"
                          {:type-id type-id :args args})))
        (when-not (type-reference? all-types return)
          (throw (ex-info "invalid function-pointer return"
                          {:type-id type-id :return return}))))
      (throw (ex-info "unknown ABI type kind"
                      {:type-id type-id :kind kind})))))

(defn- struct-dependencies [all-types type-id]
  (let [descriptor (get all-types type-id)]
    (if (= :struct (:kind descriptor))
      (set/intersection
       (set (keys all-types))
       (into #{} (mapcat #(referenced-types (:type %))) (:fields descriptor)))
      #{})))

(defn- validate-acyclic-structs! [all-types]
  (letfn [(visit [type-id visiting visited]
            (cond
              (contains? visiting type-id)
              (throw (ex-info "recursive structs are not supported"
                              {:type-id type-id :visiting visiting}))
              (contains? visited type-id) visited
              :else
              (reduce (fn [seen dependency]
                        (visit dependency (conj visiting type-id) seen))
                      (conj visited type-id)
                      (struct-dependencies all-types type-id))))]
    (reduce (fn [visited type-id] (visit type-id #{} visited))
            #{}
            (keys all-types))))

(defn- validate-ownership!
  [all-functions function-id ownership]
  (when ownership
    (when-not (and (map? ownership)
                   (seq ownership)
                   (every? #{:return :out} (keys ownership)))
      (throw (ex-info "invalid function ownership metadata"
                      {:function function-id :ownership ownership})))
    (doseq [[position rule] ownership]
      (let [{:keys [kind release owner]} rule]
        (when-not (map? rule)
          (throw (ex-info "invalid function ownership rule"
                          {:function function-id
                           :position position
                           :rule rule})))
        (case kind
          :owned
          (when-not (and (keyword? release)
                         (contains? all-functions release)
                         (nil? owner))
            (throw (ex-info "owned native value requires a release function"
                            {:function function-id
                             :position position
                             :rule rule})))

          :borrowed
          (when-not (and (keyword? owner) (nil? release))
            (throw (ex-info "borrowed native value requires an owner"
                            {:function function-id
                             :position position
                             :rule rule})))

          (throw (ex-info "unknown native ownership kind"
                          {:function function-id
                           :position position
                           :rule rule})))))))

(defn validate!
  "Validate an ABI descriptor and return it. Does not load native code."
  ([] (validate! (descriptor)))
  ([abi]
   (when-not (= 1 (:schema-version abi))
     (throw (ex-info "unsupported ABI schema version"
                     {:schema-version (:schema-version abi)})))
   (let [all-types (:types abi)
         all-functions (:functions abi)]
     (when-not (and (map? all-types) (seq all-types))
       (throw (ex-info "ABI descriptor requires :types" {})))
     (when-not (and (map? all-functions) (seq all-functions))
       (throw (ex-info "ABI descriptor requires :functions" {})))
     (doseq [[type-id type-descriptor] all-types]
       (validate-type! all-types type-id type-descriptor))
     (validate-acyclic-structs! all-types)
     (let [symbols (mapv :symbol (vals all-functions))]
       (when-not (= (count symbols) (count (set symbols)))
         (throw (ex-info "function symbols must be unique" {:symbols symbols}))))
     (doseq [[function-id {:keys [symbol args return blocking? ownership]}]
             all-functions]
       (when-not (and (keyword? function-id)
                      (string? symbol)
                      (not (empty? symbol)))
         (throw (ex-info "invalid ABI function identity"
                         {:function function-id :symbol symbol})))
       (when-not (and (vector? args)
                      (every? #(type-reference? all-types %) args))
         (throw (ex-info "invalid ABI function arguments"
                         {:function function-id :args args})))
       (when-not (type-reference? all-types return)
         (throw (ex-info "invalid ABI function return"
                         {:function function-id :return return})))
       (when-not (or (nil? blocking?) (boolean? blocking?))
         (throw (ex-info "invalid :blocking? metadata"
                         {:function function-id :blocking? blocking?})))
       (validate-ownership! all-functions function-id ownership))
     abi)))

(defn check-backend
  "Return structured per-function coverage. BACKEND is a data map containing
  :id and a :check-signature function of [function-id function descriptor]."
  ([backend] (check-backend backend (descriptor)))
  ([backend abi]
   (validate! abi)
   (let [check-signature (:check-signature backend)
         reports
         (into {}
               (map (fn [[function-id function]]
                      (let [result (check-signature function-id function abi)]
                        [function-id
                         (merge {:status :unsupported}
                                (if (map? result) result {:status result}))])))
               (:functions abi))
         supported (count (filter #(= :supported (:status %)) (vals reports)))
         total (count reports)]
     {:backend (:id backend)
      :supported? (= supported total)
      :summary {:supported supported
                :unsupported (- total supported)
                :total total}
      :functions reports})))

(defn register-backend-report!
  "Called once by the selected runtime backend after binding construction."
  [report]
  (reset! selected-backend-report* report)
  report)

(defn backend-report
  "Return diagnostics for the selected runtime backend, or nil before it is
  initialized."
  []
  @selected-backend-report*)
