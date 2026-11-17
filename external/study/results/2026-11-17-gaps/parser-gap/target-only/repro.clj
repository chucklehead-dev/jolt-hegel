(require (quote [instaparse.core :as i]))
(doseq [fmt [:hiccup :enlive]
        [grammar text] [["<S> = \u0027a\u0027*" ""]
                        ["<S> = \u0027a\u0027" "a"]
                        ["<S> = \u0027a\u0027 T; T = \u0027b\u0027" "ab"]
                        ["<S> = \u0027a\u0027 T; T = \u0027\n\u0027 \u0027b\u0027" "a\nb"]]]
 (let [t ((i/parser grammar :output-format fmt) text)
       outcome (try (doall (i/add-line-and-column-info-to-metadata text t)) :unexpected-success
                    (catch IllegalArgumentException e (.getMessage e)))]
  (assert (not (i/failure? t)))
  (prn :format fmt :grammar grammar :text text :tree t :annotation-outcome outcome)
  (assert (= "Invalid parse-tree, not recognized as either enlive or hiccup format." outcome))))
;; Visible-root reference demonstrates that the tagged child should receive [1,2] -> [1,3].
(let [p (i/parser "S = \u0027a\u0027 T; T = \u0027b\u0027")
      tree (i/add-line-and-column-info-to-metadata "ab" (p "ab"))
      child (nth tree 2)]
 (prn :visible-child child :metadata (meta child))
 (assert (= 2 (:instaparse.gll/start-column (meta child))))
 (assert (= 3 (:instaparse.gll/end-column (meta child)))))
