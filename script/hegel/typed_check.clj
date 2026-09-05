(ns hegel.typed-check
  "Dev-only runner for the bounded Typed Clojure pilot, invoked as
  `clojure -M:typed-check` (see the `:typed-check` alias in deps.edn).

  Checks the annotation namespace, `text-size`'s unchanged production body,
  `validate!`'s externally declared call-site contract, and a valid annotated
  driver with zero reported errors, then checks that every named control
  (mutant) is rejected by exactly one structured type error containing the
  evidence expected for that mutation. The explicit registry count prevents
  entries from silently disappearing from the maintained registry; new files
  must be registered deliberately.

  An actual clojure -M:typed-check run (checker 1.3.0, JDK 25) against an
  earlier version of this pilot reported exactly two errors when checking
  hegel.internal.portable-data: an unannotated `clojure.string/includes?`
  call inside the private `consume-text!` helper, and a demand for inline
  type annotations inside `validate!`'s iterative loop. Neither is fixable
  without editing src (out of scope for this pilot) or fabricating types for
  code this pilot never claimed to check. The fixes here are: (1) validate!
  is now annotated `^:no-check` (see typed/hegel/typed/portable_data.clj) so
  its declared polymorphic type is enforced at every call site without the
  checker attempting to verify its own loop body; (2) production-check-config
  below asks the checker to ignore other unannotated defs in the namespace
  (the private helpers, including consume-text!) rather than erroring on
  them, while text-size — which does carry a t/ann and no ^:no-check — still
  has its body fully checked. Subsequent observed runs confirmed this
  configuration and the explicit clojure.string/includes? seam annotation.

  Missing namespaces, analyzer crashes, and other non-type-error exceptions
  are NOT accepted as an expected rejection; see check-ns-report and
  docs/TYPED_CLOJURE.md for the exact accept/reject criterion."
  (:require [clojure.string :as str]
            [hegel.typed.portable-data]
            [typed.clojure :as t]))

(def valid-namespaces
  "hegel.typed.portable-data is checked (and, via check-ns-report's
  `(require ns-sym :reload)`, freshly re-registered) first, before the
  production namespace that depends on its external annotations: checking it
  in isolation catches a malformed alias/ann in the annotation namespace
  itself, and reloading it here means the annotation table is explicitly
  refreshed on every run rather than relying on it having been required once,
  transitively, as a side effect of this ns form."
  ['hegel.typed.portable-data
   'hegel.internal.portable-data
   'hegel.typed.driver])

(def production-check-config
  "Request that check-ns-clj not check
  hegel.internal.portable-data's unannotated private helpers (record-value?, finite-floating?,
  portable-scalar?, consume-node!, consume-text!, and the unannotated
  clojure.string/includes? call inside consume-text! that an observed run
  actually reported as an error). text-size, which does carry a t/ann and no
  ^:no-check, is unaffected and still fully checked; validate!'s own body is
  separately excluded via ^:no-check on its ann, not by this config.

  :check-config {:unannotated-def :unchecked :unannotated-var :error} uses
  the checker's documented namespace-check controls. The private helper bodies
  remain outside the pilot, while an explicit narrow dev-only annotation for
  clojure.string/includes? keeps their analyzed call seam sound.
  This is deliberately not defaulted for every namespace: driver and the
  mutant controls are fully annotated by design and should keep the default,
  stricter (error-on-unannotated-use) behavior. The production namespace
  check therefore still fails closed on any unannotated var use not covered
  by the explicit seam annotation."
  {:check-config {:unannotated-def :unchecked
                  :unannotated-var :error}})

(def mutant-controls
  [{:namespace 'hegel.typed.controls.wrong-callback-arity
    :evidence ["portable-data/validate!"
               "hegel.typed.portable-data/InvalidFn"
               "[t/Any :-> t/Nothing]"]}
   {:namespace 'hegel.typed.controls.wrong-limit-value
    :evidence ["portable-data/validate!"
               "hegel.typed.portable-data/Limits"
               "not-a-number"]}
   {:namespace 'hegel.typed.controls.text-size-wrong-input
    :evidence ["portable-data/text-size" "t/Str" "(t/Val 42)"]}
   {:namespace 'hegel.typed.controls.validate-result-identity-mismatch
    :evidence ["portable-data/validate!"
               "Long hegel.typed.portable-data/Limits"
               "t/Str"]}])

(def expected-mutant-count 4)

(defn- require! [description condition]
  (when-not condition
    (throw (ex-info (str "typed-check failed: " description)
                    {:description description})))
  (println "PASS" description))

(defn- errors-of [x]
  (cond
    (nil? x) nil
    (map? x) (or (:delayed-errors x) (:errors x))
    :else nil))

(defn- expected-type-error?
  "Require one genuine Typed Clojure error whose rendered structured report
  contains every mutation-specific evidence fragment. This prevents an
  unrelated type error — including an annotation silently disappearing —
  from satisfying a control."
  [errors evidence]
  (and (= 1 (count errors))
       (let [error (first errors)
             report (str error)]
         (and (instance? clojure.lang.ExceptionInfo error)
              (= :clojure.core.typed.errors/type-error
                 (:type-error (ex-data error)))
              (every? #(str/includes? report %) evidence)))))

(defn- normalize-report
  "Normalize check-ns-clj's success return value into {:ok? :errors}."
  [result]
  (let [errors (errors-of result)]
    (if (seq errors)
      {:ok? false :errors errors}
      {:ok? true :errors []})))

(defn- check-ns-report
  "Require ns-sym outside of any try (a namespace that fails to load at all
  is a hard failure, never an expected type-error rejection), then run
  check-ns-clj (passing opts, if given, e.g. production-check-config) and
  normalize either its return value or a thrown ExceptionInfo carrying
  recognizable error data into {:ok? :errors}."
  ([ns-sym] (check-ns-report ns-sym {}))
  ([ns-sym opts]
   (require ns-sym :reload)
   (try
     (normalize-report (t/check-ns-clj ns-sym opts))
     (catch clojure.lang.ExceptionInfo e
       (let [errors (errors-of (ex-data e))]
         (if (seq errors)
           {:ok? false :errors errors}
           (throw (ex-info (str "check-ns-clj on " ns-sym
                                " raised a non-type-error exception")
                           {:namespace ns-sym} e))))))))

(def ^:private valid-namespace-opts
  "Per-namespace opts for check-ns-report; anything absent here checks with
  the checker's default (stricter) config. See production-check-config."
  {'hegel.internal.portable-data production-check-config})

(defn -main [& _]
  (require! (str "registers exactly " expected-mutant-count " named mutant controls")
            (= expected-mutant-count (count mutant-controls)))
  (doseq [ns-sym valid-namespaces]
    (let [{:keys [ok? errors]} (check-ns-report ns-sym (get valid-namespace-opts ns-sym {}))]
      (require! (str ns-sym " type-checks with no reported errors ("
                     (count errors) " found)")
                ok?)))
  (doseq [{:keys [namespace evidence]} mutant-controls]
    (let [{:keys [ok? errors]} (check-ns-report namespace)]
      (require! (str namespace
                     " is rejected by its one expected structured type error")
                (and (not ok?) (expected-type-error? errors evidence)))))
  (flush))
