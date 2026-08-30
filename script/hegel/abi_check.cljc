(ns hegel.abi-check
  (:require [clojure.string :as str]
            [hegel.abi :as abi]
            #?(:jolt [hegel.ffi.jolt :as selected-backend]
               :bb [hegel.ffi.babashka :as selected-backend]
               :jank [hegel.ffi.jank-backend :as selected-backend]
               :clj [hegel.ffi.babashka :as selected-backend])))

(defn- require! [description condition]
  (when-not condition
    (throw (ex-info (str "ABI check failed: " description)
                    {:description description})))
  (println "PASS" description))

(defn- rejected? [f]
  (try
    (f)
    false
    (catch Throwable _
      true)))

(defn -main [& _]
  (let [descriptor (abi/validate!)
        functions (abi/functions)
        descriptor-text (pr-str descriptor)]
    (require! "descriptor identifies libhegel 0.33.3"
              (= "0.33.3" (get-in descriptor [:library :version])))
    (require! "descriptor covers all 77 header functions"
              (= 77 (count functions)))
    (require! "descriptor includes formerly unbound header calls"
              (every? (fn [function-id] (contains? functions function-id))
                      [:test-case-is-nondeterministic
                       :test-case-clone
                       :generate-integer-big]))
    (require! "descriptor represents wide and aggregate signatures"
              (and (= 15 (count (get-in functions [:string-generator-text :args])))
                   (= [:by-value :hegel/datetime]
                      (nth (get-in functions [:generate-datetime :args]) 2))))
    (require! "descriptor contains no host-specific vocabulary"
              (not-any? (fn [word] (str/includes? descriptor-text word))
                        ["jolt.ffi" "babashka.ffi" "java.lang.foreign"]))
    (require! "descriptor ownership release functions resolve"
              (every?
               (fn [[_ function]]
                 (every?
                  (fn [[_ rule]]
                    (or (not= :owned (:kind rule))
                        (contains? functions (:release rule))))
                  (:ownership function)))
               functions))
    (require! "descriptor rejects an unknown ownership release function"
              (rejected?
               #(abi/validate!
                 (assoc-in descriptor
                           [:functions :context-new :ownership :return :release]
                           :missing-release-function))))
    (require! "descriptor rejects borrowed values without an owner"
              (rejected?
               #(abi/validate!
                 (update-in descriptor
                            [:functions :context-last-error :ownership :return]
                            dissoc :owner))))
    #?(:jolt
       (let [report (abi/check-backend selected-backend/backend descriptor)]
         (require! "Jolt backend supports every descriptor signature"
                   (= {:supported 77 :unsupported 0 :total 77}
                      (:summary report)))
         (require! "Jolt backend selects direct FFI for every signature"
                   (every? (fn [entry] (= :jolt/direct (:route entry)))
                           (vals (:functions report)))))
       :bb
       (let [report (abi/check-backend selected-backend/backend descriptor)
             routes (map :route (vals (:functions report)))]
         (require! "Babashka backend supports every descriptor signature"
                   (= {:supported 77 :unsupported 0 :total 77}
                      (:summary report)))
         (require! "Babashka defers exact call routes until bindings exist"
                   (every? #{:bb/runtime-selected} routes))
         (require! "Babashka layouts preserve C aggregate size and nesting"
                   (= [8 8 16]
                      (mapv (comp selected-backend/layout-size
                                  selected-backend/layout)
                            [:hegel/date :hegel/time :hegel/datetime]))))
       :jank
       (let [report (abi/check-backend selected-backend/backend descriptor)]
         (require! "jank generator covers every descriptor signature"
                   (= {:supported 77 :unsupported 0 :total 77}
                      (:summary report)))
         (require! "jank reports generated C++ downcalls as native-ready"
                   (every? (fn [entry]
                             (and (= :jank/cpp-dlsym (:route entry))
                                  (true? (:native-ready? entry))))
                           (vals (:functions report))))
         (require! "jank generated layouts preserve C aggregate size and nesting"
                   (= [8 8 16]
                      (mapv (comp selected-backend/layout-size
                                  selected-backend/layout)
                            [:hegel/date :hegel/time :hegel/datetime]))))
       :clj
       (let [report (abi/check-backend selected-backend/backend descriptor)]
         (require! "JVM backend supports every descriptor signature"
                   (= {:supported 77 :unsupported 0 :total 77}
                      (:summary report)))
         (require! "JVM backend selects direct FFM for every signature"
                   (every? (fn [entry] (= :jvm/ffm (:route entry)))
                           (vals (:functions report))))
         (require! "JVM layouts preserve C aggregate size and nesting"
                   (= [8 8 16]
                      (mapv (comp selected-backend/layout-size
                                  selected-backend/layout)
                            [:hegel/date :hegel/time :hegel/datetime]))))))
  #?(:jank nil
     :default (flush)))
