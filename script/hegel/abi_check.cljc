(ns hegel.abi-check
  (:require [clojure.string :as str]
            [hegel.abi :as abi]
            #?(:jolt [hegel.ffi.jolt :as jolt-backend])))

(defn- require! [description condition]
  (when-not condition
    (throw (ex-info (str "ABI check failed: " description)
                    {:description description})))
  (println "PASS" description))

(defn -main [& _]
  (let [descriptor (abi/validate!)
        functions (abi/functions)
        descriptor-text (pr-str descriptor)]
    (require! "descriptor identifies libhegel 0.33.0"
              (= "0.33.0" (get-in descriptor [:library :version])))
    (require! "descriptor covers all 71 header functions"
              (= 71 (count functions)))
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
    #?(:jolt
       (let [report (abi/check-backend jolt-backend/backend descriptor)]
         (require! "Jolt backend supports every descriptor signature"
                   (= {:supported 71 :unsupported 0 :total 71}
                      (:summary report)))
         (require! "Jolt backend selects direct FFI for every signature"
                   (every? (fn [entry] (= :jolt/direct (:route entry)))
                           (vals (:functions report)))))))
  (flush))
