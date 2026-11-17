(require (quote [instaparse.core :as i]))
(doseq [gr ["S = A*; <A> = <\u0027x\u0027> \u0027a\u0027" "S = (<\u0027x\u0027> \u0027a\u0027)*" "S = A*; <A> = <\u0027xa\u0027>" "S = \u0027x\u0027 <\u0027h\u0027> A*; A = \u0027a\u0027" "S = (\u0027x\u0027 <\u0027h\u0027>) \u0027a\u0027*"] txt ["xa" "xaxa" "xh" "xha" "xhaa"]]
 (let [p (i/parser gr) a (p txt) b (p txt :optimize :memory)]
  (when (not= a b) (prn gr txt a b))))
