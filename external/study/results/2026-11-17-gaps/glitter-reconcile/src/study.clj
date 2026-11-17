(ns study
 (:require [clojure.edn :as edn] [clojure.test :as t]
           [glitter.core :as c] [glitter.test-renderer :as tr]
           [glitter.core-test]
           [hegel.core :as h] [hegel.generator :as g]
           [hegel.replay-bundle :as bundle] [hegel.replay-bundle.codec :as codec]))
(def base-provenance
 {:hegel-sha "6321d5ab485db656a48823030453bc4b2d0af1cc"
  :libhegel-version "0.51.6"
  :runtime {:host :jolt :version "0.8.27" :os "Linux" :arch "x86_64"}
  :generator-revision "1" :model-revision "1"})
(defn provenance [id] (assoc base-provenance :property-id id))
(defn ensure! [ok origin input expected actual]
 (when-not ok (throw (ex-info origin {:hegel/origin origin :input input :expected expected :actual actual}))))
(defn view [ks] (into [:ul] (map (fn [k] [:li {:glitter/key k :data-id (str k)} (str k)]) ks)))
(defn nodes [root] (:children @(first (:children @root))))
(defn snapshot [root] (mapv (fn [node] [(:tag-name @node) (get @node "data-id") (mapv #(deref %) (:children @node))]) (nodes root)))
(defn expected [ks] (mapv (fn [k] ["li" (str k) [{:text (str k)}]]) ks))
(defn reconcile-case! [frames]
 (let [r (tr/renderer) root (atom {:tag-name "body" :children []})]
  (loop [remaining frames old {} prior {}]
   (when-let [ks (first remaining)]
    (let [res (c/reconcile r root (view ks) (:vdom old) old)
          actual (snapshot root)]
     (ensure! (= (expected ks) actual) "keyed/snapshot" frames (expected ks) actual)
     (let [current (zipmap ks (nodes root))]
      (doseq [k ks :when (contains? prior k)]
       (ensure! (identical? (get prior k) (get current k)) "keyed/identity" frames k :identity-lost))
      (recur (next remaining) res current)))))))
(def keys-gen (g/vector {:min-size 0 :max-size 6 :unique? true} (g/integer 0 7)))
(defn keyed-property [_]
 (reconcile-case! (h/draw! (g/vector {:min-size 2 :max-size 5} keys-gen) "frames")))
(defn permutation-property [_]
 (let [n (h/draw! (g/integer 0 7) "size")
       before (vec (range n)) after (h/draw! (g/permutations before) "order")]
  (reconcile-case! [before after])))
(defn lifecycle-case! [{:keys [depth remove?] :as input}]
 (let [r (tr/renderer) root (atom {:tag-name "body" :children []}) calls (atom [])
       record (fn [e] (swap! calls conj (:glitter/life-cycle e)))
       leaf [:li {:glitter/on-mount record :glitter/on-unmount record} "leaf"]
       tree (nth (iterate (fn [child] [:ul child]) leaf) depth)
       res (c/reconcile r root tree)
       _ (c/reconcile r root (if remove? nil tree) (:vdom res) res)
       want (if remove? [:glitter.life-cycle/mount :glitter.life-cycle/unmount] [:glitter.life-cycle/mount])]
  (ensure! (= want @calls) "lifecycle/removal" input want @calls)
  (ensure! (= (if remove? 0 1) (count (:children @root))) "lifecycle/tree" input (if remove? 0 1) (count (:children @root)))))
(defn lifecycle-property [_]
 (lifecycle-case! (h/draw! (g/hmap {:depth (g/integer 0 5) :remove? (g/boolean)}) "lifecycle")))
(def properties {"keyed" keyed-property "permutation" permutation-property "lifecycle" lifecycle-property})
(def summary-keys [:passed? :status :seed :test-cases :valid-test-cases :invalid-test-cases :overrun-test-cases :interesting-test-cases :n-failures :flaky? :error])
(defn run-property! [id]
 (let [res (h/run-test! {:test-cases 500 :seed 142857 :database "" :verbosity :normal :stop-on-first-failure? true} (get properties id))
       summary (select-keys res summary-keys)]
  (prn id summary)
  (spit (str id "-summary.edn") (pr-str summary))
  (ensure! (and (false? (:passed? res)) (= :failed (:status res)) (not (:flaky? res))) "harness/expected-stable-defect" id :stable-failure summary)
  (let [data (-> res :final first :exception ex-data)]
   (prn :retained data)
   (spit (str id "-minimum.edn") (pr-str (:input data)))
   (spit (str id "-evidence.edn") (pr-str data)))
  (spit (str id "-bundle.edn") (codec/encode (bundle/from-result (provenance id) res)))))
(defn replay! [id]
 (let [b (codec/decode (slurp (str id "-bundle.edn")))
       calls (atom 0)
       mismatch (h/replay-bundle! (assoc (provenance id) :generator-revision "incompatible") b (fn [_] (swap! calls inc)))
       res (h/replay-bundle! (provenance id) b (get properties id))]
  (ensure! (and (= :incompatible (:status mismatch)) (zero? @calls)) "harness/provenance-gate" id :rejected-without-execution mismatch)
  (prn id :replay (select-keys res [:status :reproduced? :flaky?]))
  (ensure! (and (= :reproduced (:status res)) (:reproduced? res) (not (:flaky? res))) "harness/replay" id :reproduced (:status res))))
(defn positives! []
 (let [res (t/run-tests (quote glitter.core-test))]
  (ensure! (zero? (+ (:fail res) (:error res))) "harness/existing-tests" nil :pass res))
 (doseq [frames [[[] []] [[0] [0]] [[0 1 2] [2 0 1]] [[0 1] [0 1 2]] [[0 1 2] [0 2]]]]
  (reconcile-case! frames))
 (doseq [input [{:depth 0 :remove? true} {:depth 5 :remove? false}]] (lifecycle-case! input))
 ;; Semantic broken control: the snapshot oracle rejects a duplicated node.
 (let [want (expected [0 1]) bad (conj want (first want))]
  (try (ensure! (= want bad) "control/duplicate" [0 1] want bad)
       (throw (ex-info "Control unexpectedly passed" {}))
       (catch Exception e (ensure! (= "control/duplicate" (:hegel/origin (ex-data e))) "harness/control" nil :control-rejected (ex-data e)))))
 (prn :positive-witnesses :passed :semantic-control :rejected))
(defn passing-generated! []
 (let [res (h/run-test! {:test-cases 500 :seed 424242 :database "" :verbosity :quiet}
             (fn [_]
              (let [frames (h/draw! (g/vector {:min-size 2 :max-size 8} keys-gen))]
               (reconcile-case! (mapv #(vec (sort %)) frames)))))]
  (prn :ordered-edits (select-keys res summary-keys))
  (ensure! (true? (:passed? res)) "harness/ordered-edits" nil :pass (select-keys res summary-keys))))
(defn -main [& args]
 (case (first args)
  "generate" (do (positives!) (doseq [id ["permutation" "keyed" "lifecycle"]] (run-property! id)))
  "replay" (doseq [id ["permutation" "keyed" "lifecycle"]] (replay! id))
  "positives" (do (positives!) (passing-generated!))
  (throw (ex-info "Expected generate/replay/positives" {}))))
