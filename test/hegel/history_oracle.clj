(ns hegel.history-oracle
  "Test-only exhaustive oracle for small canonical history fixtures.

  This namespace parses its canonical fixture events without calling
  hegel.history/operations or copying the production search. It enumerates
  every permutation of the fixture operations and applies precedence plus a
  separately written register model. Three-operation cases are selected
  backtracking controls, not an exhaustive claim."
  (:require [hegel.history :as history]))

(def ^:private initial-register 0)
(def ^:private two-operation-fixture-count 300)

(def ^:private specifications
  [{:operation :write :input 0 :outcome :return :value :ok}
   {:operation :write :input 1 :outcome :return :value :ok}
   {:operation :read :outcome :return :value 0}
   {:operation :read :outcome :return :value 1}
   {:operation :fail :outcome :throw :value :expected}])

(defn- position [values needle]
  (first (keep-indexed (fn [index value]
                         (when (= needle value) index))
                       values)))

(defn- remove-at [values index]
  (vec (concat (subvec values 0 index)
               (subvec values (inc index)))))

(defn- permutations [values]
  (if (empty? values)
    [[]]
    (mapcat (fn [index]
              (let [value (nth values index)
                    remaining (remove-at values index)]
                (map (fn [suffix] (vec (cons value suffix)))
                     (permutations remaining))))
            (range (count values)))))

(defn- valid-event-orders []
  (filter (fn [order]
            (and (< (position order [:left :invoke])
                    (position order [:left :terminal]))
                 (< (position order [:right :invoke])
                    (position order [:right :terminal]))))
          (permutations [[:left :invoke] [:left :terminal]
                         [:right :invoke] [:right :terminal]])))

(defn- operation-event [id specification partition phase sequence]
  (if (= :invoke phase)
    {:seq sequence
     :operation-id id
     :phase :invoke
     :operation (:operation specification)
     :input (:input specification)
     :partition partition}
    {:seq sequence
     :operation-id id
     :phase (:outcome specification)
     :value (:value specification)
     :partition partition}))

(defn- fixture [order left right partitions]
  (let [specification-for {:left left :right right}
        id-for {:left false :right :right}
        partition-for {:left (first partitions) :right (second partitions)}
        events (mapv (fn [sequence [name phase]]
                       (operation-event (get id-for name)
                                        (get specification-for name)
                                        (get partition-for name)
                                        phase
                                        sequence))
                     (range)
                     order)]
    {:events events}))

(defn- oracle-operations
  "Parse a known-canonical fixture without calling `hegel.history/operations`.

  Fixtures are deliberately constructed as event streams. This tiny parser is
  an independent test oracle boundary, rather than a second implementation of
  production validation: malformed inputs are tested through the public API.
  Every generated fixture has exactly one invocation and terminal per ID."
  [events]
  (->> (group-by :operation-id events)
       vals
       (map (fn [operation-events]
              (let [invoke (first (filter #(= :invoke (:phase %))
                                          operation-events))
                    terminal (first (remove #(= :invoke (:phase %))
                                            operation-events))]
                {:id (:operation-id invoke)
                 :operation (:operation invoke)
                 :input (:input invoke)
                 :outcome (:phase terminal)
                 :value (:value terminal)
                 :partition (:partition invoke)
                 :invoke-seq (:seq invoke)
                 :terminal-seq (:seq terminal)})))
       (sort-by :invoke-seq)
       vec))

(defn- exhaustive-two-operation-fixtures []
  (vec
   (for [order (valid-event-orders)
         left specifications
         right specifications
         partitions [[:left :left] [:left :right]]]
     (fixture order left right partitions))))

(defn- oracle-step [state operation]
  (cond
    (= :write (:operation operation))
    (when (and (= :return (:outcome operation))
               (= :ok (:value operation)))
      {:state (:input operation)})

    (= :read (:operation operation))
    (when (and (= :return (:outcome operation))
               (= state (:value operation)))
      {:state state})

    (= :fail (:operation operation))
    (when (= :throw (:outcome operation))
      {:state state})

    :else nil))

(defn- production-step [state operation]
  ;; Intentionally separate from oracle-step so a shared test helper cannot
  ;; hide a model defect in both sides of the differential.
  (case (:operation operation)
    :write (when (and (= :return (:outcome operation))
                      (= :ok (:value operation)))
             {:state (:input operation)})
    :read (when (and (= :return (:outcome operation))
                     (= (:value operation) state))
            {:state state})
    :fail (when (= :throw (:outcome operation))
            {:state state})
    nil))

(defn- precedes? [left right]
  (< (:terminal-seq left) (:invoke-seq right)))

(defn- order-respects-precedence? [order]
  (every?
   (fn [[left-index left]]
     (every?
      (fn [[right-index right]]
        (or (= left-index right-index)
            (not (precedes? left right))
            (< left-index right-index)))
      (map-indexed vector order)))
   (map-indexed vector order)))

(defn- oracle-linearizable-operations? [initial step operations]
  (boolean
   (some (fn [order]
           (when (order-respects-precedence? order)
             (loop [state initial
                    remaining order]
               (if-let [operation (first remaining)]
                 (when-let [transition (step state operation)]
                   (recur (:state transition) (next remaining)))
                 true))))
         (permutations operations))))

(defn- oracle-linearizable? [initial step operations]
  (every?
   (fn [[_ partition-operations]]
     (oracle-linearizable-operations? initial step partition-operations))
   (group-by :partition operations)))

(defn- production-linearizable-with-step?
  [step fixture sequence-start max-operations]
  (history/linearizable?
   initial-register
   step
   (:events fixture)
   {:partition-by #(-> % :invoke :partition)
    :sequence-start sequence-start
    :max-operations max-operations}))

(defn- production-linearizable? [fixture sequence-start max-operations]
  (production-linearizable-with-step?
   production-step fixture sequence-start max-operations))

(defn- production-unpartitioned-linearizable?
  [fixture sequence-start max-operations]
  (history/linearizable?
   initial-register
   production-step
   (:events fixture)
   {:sequence-start sequence-start
    :max-operations max-operations}))

(defn- rename-id [id]
  (if (= false id) :false-id :renamed-right))

(defn- rename-fixture [fixture]
  {:events (mapv #(update % :operation-id rename-id) (:events fixture))})

(defn- offset-fixture [fixture offset]
  {:events (mapv #(update % :seq + offset) (:events fixture))})

(defn- bad-false-membership? [operations]
  ;; This is the original membership bug: a set is invoked as a predicate, so
  ;; a selected `false` ID still returns false. A later operation therefore
  ;; remains blocked even after its false-ID predecessor was selected.
  (let [before (into {}
                     (map (fn [operation]
                            [(:id operation)
                             (set (map :id
                                       (filter #(precedes? % operation)
                                               operations)))])
                          operations))]
    (boolean
     (some
      (fn [order]
        (loop [state initial-register
               remaining order
               selected #{}]
          (if-let [operation (first remaining)]
            (let [eligible?
                  (every? selected (get before (:id operation)))]
              (when (and eligible? (oracle-step state operation))
                (recur (:state (oracle-step state operation))
                       (next remaining)
                       (conj selected (:id operation)))))
            true)))
      (permutations operations)))))

(defn- fold-operations [initial step operations]
  (loop [state initial
         remaining operations]
    (if-let [operation (first remaining)]
      (when-let [transition (step state operation)]
        (recur (:state transition) (next remaining)))
      true)))

(defn- bad-precedence-ignoring? [initial step operations]
  ;; Same finite candidate space as the oracle, but deliberately omits the
  ;; real-time precedence guard before folding a candidate order.
  (boolean (some #(fold-operations initial step %) (permutations operations))))

(defn- bad-model-step [state _]
  {:state state})

(defn- single-operation-fixture []
  (let [specification {:operation :read :outcome :return :value 0}]
    {:events [(operation-event false specification :left :invoke 0)
              (operation-event false specification :left :return 1)]}))

(defn- fixture-events [operations]
  (vec
   (sort-by
    :seq
    (mapcat
     (fn [operation]
       [{:seq (:invoke-seq operation)
         :operation-id (:id operation)
         :phase :invoke
         :operation (:operation operation)
         :input (:input operation)
         :partition (:partition operation)}
        {:seq (:terminal-seq operation)
         :operation-id (:id operation)
         :phase (:outcome operation)
         :value (:value operation)
         :partition (:partition operation)}])
     operations))))

(defn- three-operation-fixtures []
  ;; The first requires trying a non-invocation order; the second requires
  ;; rejecting a tempting read-before-write order; the third checks that
  ;; partitions reset independently. These are bounded controls only.
  (mapv (fn [operations] {:events (fixture-events operations)})
   [[{:id false :operation :write :input 1 :outcome :return
                  :value :ok :partition :one :invoke-seq 0 :terminal-seq 4}
                 {:id :read :operation :read :outcome :return :value 0
                  :partition :one :invoke-seq 1 :terminal-seq 5}
                 {:id :write-zero :operation :write :input 0 :outcome :return
                  :value :ok :partition :one :invoke-seq 2 :terminal-seq 3}]
    [{:id false :operation :write :input 1 :outcome :return
                  :value :ok :partition :one :invoke-seq 0 :terminal-seq 1}
                 {:id :read :operation :read :outcome :return :value 0
                  :partition :one :invoke-seq 2 :terminal-seq 5}
                 {:id :write-two :operation :write :input 2 :outcome :return
                  :value :ok :partition :one :invoke-seq 3 :terminal-seq 4}]
    [{:id false :operation :write :input 1 :outcome :return
                  :value :ok :partition :left :invoke-seq 0 :terminal-seq 3}
                 {:id :right-read :operation :read :outcome :return :value 0
                  :partition :right :invoke-seq 1 :terminal-seq 4}
                 {:id :left-read :operation :read :outcome :return :value 1
                  :partition :left :invoke-seq 2 :terminal-seq 5}]]))

(defn- checker-category [events opts]
  (try
    (history/check! initial-register production-step events opts)
    :legal
    (catch Throwable error
      (case (:type (ex-data error))
        ::history/malformed-history :malformed
        ::history/not-linearizable :illegal
        ::history/operation-bound :bounded-error
        :unexpected-error))))

(defn- count-categories [fixtures]
  (reduce (fn [counts fixture]
            (let [legal? (oracle-linearizable?
                          initial-register oracle-step
                          (oracle-operations (:events fixture)))]
              (update counts (if legal? :legal :illegal) inc)))
          {:legal 0 :illegal 0}
          fixtures))

(defn checks []
  (let [fixtures (exhaustive-two-operation-fixtures)
        categories (count-categories fixtures)
        empty-fixture {:events []}
        one-fixture (single-operation-fixture)
        false-precedence
        (let [operations [{:id false :operation :write :input 1 :outcome :return
                           :value :ok :partition :left
                           :invoke-seq 0 :terminal-seq 1}
                          {:id :read :operation :read :outcome :return :value 0
                           :partition :left
                           :invoke-seq 2 :terminal-seq 3}]]
          {:events (fixture-events operations)})
        false-id-predecessor
        (let [operations [{:id false :operation :write :input 1 :outcome :return
                           :value :ok :partition :left
                           :invoke-seq 0 :terminal-seq 1}
                          {:id :read :operation :read :outcome :return :value 1
                           :partition :left
                           :invoke-seq 2 :terminal-seq 3}]]
          {:events (fixture-events operations)})
        malformed [{:seq 0 :operation-id false :phase :invoke :operation :read}]
        bounded-events
        [{:seq 0 :operation-id false :phase :invoke :operation :read}
         {:seq 1 :operation-id false :phase :return :value 0}
         {:seq 2 :operation-id :next :phase :invoke :operation :read}
         {:seq 3 :operation-id :next :phase :return :value 0}]]
    [[(str "history oracle exhaustively checks " (count fixtures)
          " canonical two-operation fixtures")
      (= two-operation-fixture-count (count fixtures))]
     [(str "history oracle reports bounded categories " categories)
      (and (pos? (:legal categories))
           (pos? (:illegal categories))
           (= (count fixtures) (+ (:legal categories) (:illegal categories))))]
     ["history oracle and production checker agree on every exhaustive fixture"
      (every? (fn [fixture]
                (= (oracle-linearizable? initial-register oracle-step
                                         (oracle-operations (:events fixture)))
                   (production-linearizable? fixture 0 2)))
              fixtures)]
     ["history oracle and unpartitioned production checker agree on every exhaustive fixture"
      (every? (fn [fixture]
                (= (oracle-linearizable-operations?
                    initial-register oracle-step
                    (oracle-operations (:events fixture)))
                   (production-unpartitioned-linearizable? fixture 0 2)))
              fixtures)]
     ["history oracle preserves empty and one-operation identities"
      (and (oracle-linearizable? initial-register oracle-step
                                 (oracle-operations (:events empty-fixture)))
           (oracle-linearizable? initial-register oracle-step
                                 (oracle-operations (:events one-fixture)))
           (production-linearizable? empty-fixture nil 2)
           (production-linearizable? one-fixture 0 2))]
     ["history oracle checks selected three-operation reorder/backtrack and partition controls"
      (let [three-fixtures (three-operation-fixtures)
            first-operations (oracle-operations (:events (first three-fixtures)))
            results
            (mapv (fn [fixture]
                    [(oracle-linearizable? initial-register oracle-step
                                            (oracle-operations (:events fixture)))
                     (production-linearizable? fixture 0 3)])
                  three-fixtures)]
        (and (= 3 (count results))
             ;; Invocation order is write-1, read-0, write-0. Its fold fails,
             ;; while the exhaustive oracle finds the reordered witness.
             (nil? (fold-operations initial-register oracle-step
                                    first-operations))
             (= [[true true] [false false] [true true]] results)))]
     ["history ID bijections preserve false-ID results"
      (every? (fn [fixture]
                (= (oracle-linearizable? initial-register oracle-step
                                         (oracle-operations (:events fixture)))
                   (oracle-linearizable? initial-register oracle-step
                                         (oracle-operations
                                          (:events (rename-fixture fixture))))
                   (production-linearizable? fixture 0 2)
                   (production-linearizable? (rename-fixture fixture) 0 2)))
              fixtures)]
     ["history sequence offsets preserve checker and oracle results"
      (every? (fn [fixture]
                (let [offset 37
                      shifted (offset-fixture fixture offset)]
                  (= (oracle-linearizable? initial-register oracle-step
                                           (oracle-operations (:events fixture)))
                     (oracle-linearizable? initial-register oracle-step
                                           (oracle-operations (:events shifted)))
                     (production-linearizable? fixture 0 2)
                     (production-linearizable? shifted offset 2))))
              fixtures)]
     ["false-ID membership mutant fails the independent differential"
      (let [operations (oracle-operations (:events false-id-predecessor))]
        (and (oracle-linearizable? initial-register oracle-step operations)
             (not (bad-false-membership? operations))))]
     ["precedence-ignoring checker mutant fails the independent differential"
      (let [operations (oracle-operations (:events false-precedence))]
        (and (not (oracle-linearizable? initial-register oracle-step operations))
             (bad-precedence-ignoring? initial-register oracle-step operations)))]
     ["permissive model mutant fails the independent differential"
      (some (fn [fixture]
              (not= (oracle-linearizable? initial-register oracle-step
                                           (oracle-operations (:events fixture)))
                    (production-linearizable-with-step?
                     bad-model-step fixture 0 2)))
            fixtures)]
     ["malformed history is distinct from an illegal canonical history"
      (and (= :malformed
              (checker-category malformed {:max-operations 2}))
           (= :illegal
              (checker-category (:events false-precedence)
                                {:max-operations 2
                                 :partition-by #(-> % :invoke :partition)})))]
     ["current operation limit is a bounded error, not an inconclusive verdict"
      (= :bounded-error
         (checker-category bounded-events {:max-operations 1}))]]))
