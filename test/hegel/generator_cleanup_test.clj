(ns hegel.generator-cleanup-test
  (:require [clojure.test :refer [deftest is]]
            [hegel.generator :as g]
            [hegel.ffi :as hffi]))

(def ^:private test-case {:context :context :handle :handle})

(defn- caught-error [thunk]
  (try
    (thunk)
    nil
    (catch Throwable error error)))

(defn- in-span [label body]
  ((ns-resolve 'hegel.generator 'in-span) test-case label body))

(defn- native-string-generator [builder]
  ((ns-resolve 'hegel.generator 'native-string-generator) builder))

(deftest span-cleanup-preserves-the-original-body-error
  (let [events (atom [])
        body-error (ex-info "body" {:marker :body})
        close-error (ex-info "close" {:marker :close})]
    ;; Pure/mock controls: ordinary success and a body-only failure keep their
    ;; existing result and error behavior when closing succeeds.
    (with-redefs [hffi/start-span! (fn [_ _ label] (swap! events conj [:start label]))
                  hffi/stop-span! (fn [& _] (swap! events conj :stop))]
      (is (= :ok (in-span hffi/label-mapped (constantly :ok))))
      (is (identical? body-error
                      (caught-error #(in-span hffi/label-mapped
                                               (fn [] (throw body-error))))))
      (is (= [[:start hffi/label-mapped] :stop
              [:start hffi/label-mapped] :stop]
             @events)))
    (reset! events [])
    (with-redefs [hffi/start-span! (fn [_ _ label] (swap! events conj [:start label]))
                  hffi/stop-span! (fn [& _]
                                    (swap! events conj :stop)
                                    (throw close-error))]
      ;; This is the issue-78 mutant: the old catch path let close-error
      ;; replace body-error. The exact body object is now retained.
      (is (identical? body-error
                      (caught-error #(in-span hffi/label-mapped
                                               (fn [] (throw body-error))))))
      (is (= [[:start hffi/label-mapped] :stop] @events)))
    (reset! events [])
    (with-redefs [hffi/start-span! (fn [_ _ label] (swap! events conj [:start label]))
                  hffi/stop-span! (fn [& _]
                                    (swap! events conj :stop)
                                    (throw close-error))]
      ;; A close failure after a successful body remains observable.
      (is (identical? close-error
                      (caught-error #(in-span hffi/label-mapped (constantly :ok)))))
      (is (= [[:start hffi/label-mapped] :stop] @events)))
    (let [body-called? (atom false)
          start-error (ex-info "start" {:marker :start})]
      (reset! events [])
      (with-redefs [hffi/start-span! (fn [& _] (throw start-error))
                    hffi/stop-span! (fn [& _] (swap! events conj :stop))]
        (is (identical? start-error
                        (caught-error #(in-span hffi/label-mapped
                                                 (fn []
                                                   (reset! body-called? true)
                                                   :unreachable)))))
        (is (false? @body-called?))
        (is (empty? @events))))))

(deftest filter-and-collection-cleanup-retain-generator-errors
  (let [events (atom [])
        collection-frees (atom 0)
        body-error (ex-info "body" {:marker :body})
        close-error (ex-info "close" {:marker :close})]
    (with-redefs [hffi/start-span! (fn [& _] (swap! events conj :start))
                  hffi/stop-span! (fn [& _]
                                    (swap! events conj :stop)
                                    (throw close-error))]
      (is (identical? body-error
                      (caught-error #((g/filter (constantly true)
                                                (fn [_] (throw body-error)))
                                       test-case))))
      (is (= [:start :stop] @events)))
    (reset! events [])
    (with-redefs [hffi/start-span! (fn [& _] (swap! events conj :start))
                  hffi/stop-span! (fn [& _] (swap! events conj :stop))
                  hffi/new-collection! (fn [& _] :collection)
                  hffi/collection-more! (fn [& _] (throw body-error))
                  hffi/collection-free! (fn [& _]
                                          (swap! collection-frees inc)
                                          (throw close-error))]
      (is (identical? body-error
                      (caught-error #((g/vector (g/just :element)) test-case))))
      ;; The inner collection free and outer span close both run once, but
      ;; neither is allowed to replace the body marker.
      (is (= [:start :stop] @events))
      (is (= 1 @collection-frees)))
    (with-redefs [hffi/start-span! (fn [& _] nil)
                  hffi/stop-span! (fn [& _] nil)
                  hffi/new-collection! (fn [& _] :collection)
                  hffi/collection-more! (fn [& _] false)
                  hffi/collection-free! (fn [& _] (throw close-error))]
      (is (identical? close-error
                      (caught-error #((g/vector (g/just :element)) test-case)))))))

(deftest string-generator-cleanup-retains-builder-and-draw-errors
  (let [events (atom [])
        builder-error (ex-info "builder" {:marker :builder})
        draw-error (ex-info "draw" {:marker :draw})
        close-error (ex-info "close" {:marker :close})]
    (with-redefs [hffi/context-new! (fn [] (swap! events conj :context-new) :context)
                  hffi/context-free! (fn [& _]
                                       (swap! events conj :context-free)
                                       (throw close-error))]
      ;; Construction previously used a finally: a context-free failure hid a
      ;; builder failure. The exact builder object must win.
      (is (identical? builder-error
                      (caught-error #(native-string-generator
                                      (fn [_] (throw builder-error))))))
      (is (= [:context-new :context-free] @events)))
    (reset! events [])
    (let [generator
          (with-redefs [hffi/context-new! (fn [] :validation-context)
                        hffi/context-free! (fn [& _] nil)
                        hffi/string-generator-free! (fn [& _]
                                                      (swap! events conj :validation-free))]
            (native-string-generator (fn [_] :string-generator)))]
      (reset! events [])
      (with-redefs [hffi/generate-string! (fn [& _] (throw draw-error))
                    hffi/string-generator-free! (fn [& _]
                                                  (swap! events conj :draw-free)
                                                  (throw close-error))]
        ;; The draw error survives its owned string-handle cleanup failure.
        (is (identical? draw-error
                        (caught-error #(generator test-case))))
        (is (= [:draw-free] @events)))
      (reset! events [])
      (with-redefs [hffi/generate-string! (fn [& _] "value")
                    hffi/string-generator-free! (fn [& _]
                                                  (swap! events conj :draw-free)
                                                  (throw close-error))]
        ;; A close-only failure stays visible rather than being suppressed.
        (is (identical? close-error
                        (caught-error #(generator test-case))))
        (is (= [:draw-free] @events))))))

(deftest recursive-retry-leaves-the-native-discarded-span-alone
  (let [events (atom [])
        leaf-results (atom [:retry :ok])
        retries (atom 0)
        frees (atom 0)]
    (with-redefs [hffi/new-recursion! (fn [& _] :recursion)
                  hffi/start-span! (fn [& _] (swap! events conj :start))
                  hffi/stop-span! (fn [& _] (swap! events conj :stop))
                  hffi/recursion-branch! (fn [& _] false)
                  hffi/recursion-leaf! (fn [& _]
                                         (let [result (first @leaf-results)]
                                           (swap! leaf-results next)
                                           result))
                  hffi/recursion-finish! (fn [& _] :ok)
                  hffi/recursion-retry! (fn [& _] (swap! retries inc))
                  hffi/recursion-free! (fn [& _] (swap! frees inc))]
      (is (= :leaf
             ((g/recursive {:max-depth 1 :max-leaves 1}
                           (g/just :leaf)
                           (fn [_] (g/just :branch)))
              test-case)))
      ;; First attempt is native-discarded, so only the second span closes.
      (is (= [:start :start :stop] @events))
      (is (= 1 @retries))
      (is (= 1 @frees)))))

(deftest recursive-cleanup-retains-body-errors
  (let [body-error (ex-info "recursive body" {:marker :body})
        close-error (ex-info "recursive close" {:marker :close})
        events (atom [])
        frees (atom 0)
        generator (g/recursive {:max-depth 1 :max-leaves 1}
                               (fn [_] (throw body-error))
                               (fn [_] (g/just :branch)))]
    ;; Pure/mock control: a recursive leaf failure wins over its span close.
    (with-redefs [hffi/new-recursion! (fn [& _] :recursion)
                  hffi/start-span! (fn [& _] (swap! events conj :start))
                  hffi/stop-span! (fn [& _]
                                    (swap! events conj :stop)
                                    (throw close-error))
                  hffi/recursion-branch! (fn [& _] false)
                  hffi/recursion-leaf! (fn [& _] :ok)
                  hffi/recursion-free! (fn [& _] (swap! frees inc))]
      (is (identical? body-error (caught-error #(generator test-case))))
      (is (= [:start :stop] @events))
      (is (= 1 @frees)))
    (reset! events [])
    (reset! frees 0)
    ;; The outer recursion resource has the same precedence rule.
    (with-redefs [hffi/new-recursion! (fn [& _] :recursion)
                  hffi/start-span! (fn [& _] (swap! events conj :start))
                  hffi/stop-span! (fn [& _] (swap! events conj :stop))
                  hffi/recursion-branch! (fn [& _] false)
                  hffi/recursion-leaf! (fn [& _] :ok)
                  hffi/recursion-free! (fn [& _]
                                         (swap! frees inc)
                                         (throw close-error))]
      (is (identical? body-error (caught-error #(generator test-case))))
      (is (= [:start :stop] @events))
      (is (= 1 @frees)))))

(defn -main [& _]
  (let [{:keys [fail error]} (clojure.test/run-tests 'hegel.generator-cleanup-test)]
    (when (pos? (+ fail error))
      (throw (ex-info "generator cleanup tests failed" {:fail fail :error error})))))
