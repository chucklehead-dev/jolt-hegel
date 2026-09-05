(ns hegel.suites.malli
  "Malli adapter contract scenarios, loaded only when selected."
  (:require [hegel.core :as h]
            [hegel.malli :as hm]
            [hegel.test-support :as support]
            [malli.core :as m]))

(defn caught-error [f]
  (try
    (f)
    nil
    (catch Throwable error
      error)))

(defn malli-adapter-construction [context]
  (let [cases
        [{:description "rejects intersection schemas"
          :form [:and :int [:> 0]]
          :type :hegel.malli/unsupported-schema
          :path []}
         {:description "rejects regex schemas"
          :form [:* :int]
          :type :hegel.malli/unsupported-schema
         :path []}
         {:description "rejects predicate schemas"
          :form 'string?
          :type :hegel.malli/unsupported-schema
          :path []}
         {:description "rejects nonrecursive references"
          :form [:ref {:registry {::node :int}} ::node]
          :type :hegel.malli/unsupported-schema
          :path [:registry ::node]}
         {:description "rejects properties on a direct recursive reference"
          :form [:ref
                 {:registry {::node [:or :nil [:vector [:ref ::node]]]}
                  :title "node"}
                 ::node]
          :type :hegel.malli/unsupported-property
          :path [:properties :title]}
         {:description "rejects properties on an outer recursive schema"
          :form [:schema
                 {:registry {::node [:or :nil [:vector [:ref ::node]]]}
                  :title "node"}
                 [:ref ::node]]
          :type :hegel.malli/unsupported-property
          :path [:properties :title]}
         {:description "rejects properties on a recursive schema's root reference"
          :form [:schema
                 {:registry {::node [:or :nil [:vector [:ref ::node]]]}}
                 [:ref {:title "node"} ::node]]
          :type :hegel.malli/unsupported-property
          :path [:child :properties :title]}
         {:description "rejects properties on a recursive definition root"
          :form [:schema
                 {:registry
                  {::node
                   [:or {:title "node"} :nil [:vector [:ref ::node]]]}}
                 [:ref ::node]]
          :type :hegel.malli/unsupported-property
          :path [:registry ::node :properties :title]}
         {:description "rejects mutually recursive registries"
          :form [:schema
                 {:registry
                  {::left [:or :nil [:ref ::right]]
                   ::right [:or :nil [:ref ::left]]}}
                 [:ref ::left]]
          :type :hegel.malli/unsupported-schema
          :path [:registry]}
         {:description "rejects recursive definitions without a base branch"
          :form [:schema
                 {:registry {::node [:or [:vector [:ref ::node]]]}}
                 [:ref ::node]]
          :type :hegel.malli/unsupported-schema
          :path [:registry ::node]}
         {:description "rejects recursive declarations without a recursive branch"
          :form [:schema
                 {:registry {::node [:or :nil :int]}}
                 [:ref ::node]]
          :type :hegel.malli/unsupported-schema
          :path [:registry ::node]}
         {:description "rejects recursive definitions whose root is not :or"
          :form [:schema
                 {:registry {::node [:vector [:ref ::node]]}}
                 [:ref ::node]]
          :type :hegel.malli/unsupported-schema
          :path [:registry ::node]}
         {:description "rejects references outside the active recursive definition"
          :form [:schema
                 {:registry
                  {::node
                   [:or
                    :nil
                    [:ref {:registry {::foreign :int}} ::foreign]
                    [:vector [:ref ::node]]]}}
                 [:ref ::node]]
          :type :hegel.malli/unsupported-schema
          :path [:registry ::node :children 1]}
         {:description "rejects custom generator properties"
          :form [:vector [:string {:gen/elements ["x"]}]]
          :type :hegel.malli/unsupported-property
          :path [:child :properties :gen/elements]}
         {:description "rejects registries on otherwise supported schemas"
          :form [:int {:registry {::value :int}}]
          :type :hegel.malli/unsupported-property
          :path [:properties :registry]}
         {:description "rejects open maps"
          :form [:map [:x :int]]
          :type :hegel.malli/unsupported-schema
          :path []}
         {:description "rejects default map entries"
          :form [:map {:closed true} [::m/default :int]]
          :type :hegel.malli/unsupported-schema
          :path [:keys ::m/default]}]]
    (doseq [{:keys [description form type path]} cases]
      (let [error (caught-error #(hm/generator form))
            data (ex-data error)]
        (support/check! context description
               (and error
                    (= type (:type data))
                    (= path (:path data))
                    (= form (:form data)))))))
  (let [form [:vector :int]
        error (caught-error #(hm/generator form {:size 4}))]
    (support/check! context "rejects unknown adapter config"
           (= {:type :hegel.malli/invalid-config
               :path [:config :size]
               :form form}
              (select-keys (ex-data error) [:type :path :form]))))
  (let [generator (hm/generator [:vector :boolean]
                                {:default-max-size 3})
        result
        (h/run-test!
         {:test-cases 25 :seed 20260804 :database "" :verbosity :quiet}
         (fn [_]
           (let [value (h/draw! generator)]
             (when (> (count value) 3)
               (throw (ex-info "adapter fallback bound was violated"
                               {:hegel/origin
                                "hegel.test-runner:malli-config-bound"}))))))]
    (support/check! context "applies the configured fallback collection bound"
           (:passed? result)))
  (let [form [:int {:min 0 :max 9223372036854775808N}]
        error (caught-error #(hm/generator form))]
    (support/check! context "rejects integer bounds outside Hegel's int64 domain"
           (= {:type :hegel.malli/invalid-property
               :path []
               :form form}
              (select-keys (ex-data error) [:type :path :form]))))
  (let [forms [[:string {:max 18446744073709551616N}]
               [:vector {:min 18446744073709551616N} :boolean]]]
    (doseq [form forms]
      (let [error (caught-error #(hm/generator form))]
        (support/check! context "rejects collection bounds outside Hegel's uint64 domain"
               (= {:type :hegel.malli/invalid-property
                   :path []
                   :form form}
                  (select-keys (ex-data error) [:type :path :form]))))))
  (let [form [:vector :boolean]
        uint64-max 18446744073709551615N
        error (caught-error
               #(hm/generator form {:default-max-size (inc uint64-max)}))]
    (support/check! context "rejects adapter fallback outside Hegel's uint64 domain"
           (= {:type :hegel.malli/invalid-config
               :path [:config :default-max-size]
               :form form}
              (select-keys (ex-data error) [:type :path :form]))))
  (let [form [:schema
              {:registry {::node [:or :nil [:vector [:ref ::node]]]}}
              [:ref ::node]]
        error (caught-error #(hm/generator form {:max-depth -1}))]
    (support/check! context "rejects recursive bounds outside Hegel's uint64 domain"
           (= {:type :hegel.malli/invalid-config
               :path [:config :max-depth]
               :form form}
              (select-keys (ex-data error) [:type :path :form])))))

(defn malli-adapter-generation [context]
  (let [schema
        [:map {:closed true}
         [:id [:int {:min 1 :max 9}]]
         [:payload
          [:tuple
           [:enum :left :right]
           [:vector {:min 1 :max 4}
            [:or :nil [:string {:min 1 :max 5}]]]]]
         [:flags [:set {:min 1 :max 2} :boolean]]
         [:attributes {:optional true}
          [:map-of {:max 2}
           [:enum :x :y]
           [:double {:min -1.0 :max 1.0}]]]]
        valid? (m/validator schema)
        generator (hm/generator schema)
        seen (atom 0)
        result
        (h/run-test!
         {:test-cases 100 :seed 20260805 :database "" :verbosity :quiet}
         (fn [_]
           (let [value (h/draw! generator)]
             (swap! seen inc)
             (when-not (valid? value)
               (throw (ex-info "nested Malli value was invalid"
                               {:hegel/origin
                                "hegel.test-runner:malli-nested-validity"}))))))]
    (support/check! context "generates valid nested values from the supported Malli subset"
           (and (:passed? result) (pos? @seen))))
  (let [schema
        [:tuple
         [:int {:min -3 :max 7}]
         [:double {:min -2.5 :max 4.5}]
         [:string {:min 2 :max 6}]
         [:sequential {:min 1 :max 3} :boolean]
         [:map-of {:min 1 :max 2} [:enum :a :b] :nil]]
        generator (hm/generator schema)
        result
        (h/run-test!
         {:test-cases 100 :seed 20260806 :database "" :verbosity :quiet}
         (fn [_]
           (let [[integer double string sequential map]
                 (h/draw! generator)]
             (when-not (and (<= -3 integer 7)
                            (<= -2.5 double 4.5)
                            (<= 2 (support/codepoint-count string) 6)
                            (<= 1 (count sequential) 3)
                            (<= 1 (count map) 2))
               (throw (ex-info "Malli bounds were violated"
                               {:hegel/origin
                                "hegel.test-runner:malli-bounds"}))))))]
    (support/check! context "honors numeric, string, and collection bounds"
           (:passed? result)))
  (let [seen (atom #{})
        schema [:map {:closed true}
                [:value {:optional true} [:maybe [:= :present]]]]
        generator (hm/generator schema)
        result
        (h/run-test!
         {:test-cases 100 :seed 20260807 :database "" :verbosity :quiet}
         (fn [_]
           (let [value (h/draw! generator)]
             (swap! seen conj
                    (cond
                      (not (contains? value :value)) :absent
                      (nil? (:value value)) :present-nil
                      :else :present-value)))))]
    (support/check! context "distinguishes an absent optional key from a present nil"
           (and (:passed? result)
                (= #{:absent :present-nil :present-value} @seen))))
  (let [final-values (atom [])
        generator (hm/generator [:int {:min 0 :max 100}])
        result
        (h/run-test!
         {:test-cases 200
          :seed 1777986545686
          :database ""
          :report-multiple-failures? false
          :verbosity :quiet}
         (fn [_]
           (let [value (h/draw! generator)]
             (when (h/final?)
               (swap! final-values conj value))
             (when (>= value 10)
               (throw (ex-info "Malli shrink threshold violated"
                               {:hegel/origin
                                "hegel.test-runner:malli-native-shrink"
                                :value value}))))))]
    (support/check! context "retains native Hegel shrinking through the Malli adapter"
           (and (not (:passed? result))
                (= [10] @final-values)
                (= 10 (-> result :final first :exception ex-data :value)))))
  (let [schema
        [:schema
         {:registry
          {::tree
           [:or
            [:= :leaf]
            [:tuple [:= :node] [:ref ::tree] [:ref ::tree]]]}}
         [:ref ::tree]]
        valid? (m/validator schema)
        leaf-only (hm/generator schema {:max-depth 0 :max-leaves 1})
        leaf-budgeted (hm/generator schema {:max-depth 5 :max-leaves 1})
        recursive (hm/generator schema {:max-depth 5 :max-leaves 64})
        nested? (atom false)
        leaf-result
        (h/run-test!
         {:test-cases 25 :seed 20260831 :database "" :verbosity :quiet}
         (fn [_]
           (let [value (h/draw! leaf-only)]
             (when-not (= :leaf value)
               (throw (ex-info "zero-depth recursive Malli value was not a leaf"
                               {:hegel/origin
                                "hegel.test-runner:malli-recursive-depth"}))))))
        leaf-budget-result
        (h/run-test!
         {:test-cases 100 :seed 20260833 :database "" :verbosity :quiet}
         (fn [_]
           (let [value (h/draw! leaf-budgeted)]
             (when-not (= :leaf value)
               (throw (ex-info "recursive Malli leaf budget was exceeded"
                               {:hegel/origin
                                "hegel.test-runner:malli-recursive-leaves"}))))))
        recursive-result
        (h/run-test!
         {:test-cases 200 :seed 20260832 :database "" :verbosity :quiet}
         (fn [_]
           (let [value (h/draw! recursive)]
             (when (vector? value)
               (reset! nested? true))
             (when-not (valid? value)
               (throw (ex-info "recursive Malli value was invalid"
                               {:hegel/origin
                                "hegel.test-runner:malli-recursive-validity"}))))))]
    (support/check! context "maps a recursive Malli registry to native recursive generation"
           (and (:passed? leaf-result)
                (:passed? recursive-result)
                @nested?))
    (support/check! context "passes Malli :max-leaves through to native recursive generation"
           (:passed? leaf-budget-result))))
