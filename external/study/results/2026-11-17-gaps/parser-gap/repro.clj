(require (quote [instaparse.core :as i]))
(doseq [fmt [:hiccup :enlive]]
 (let [p (i/parser "<S> = \u0027a\u0027" :output-format fmt) t (p "a")]
  (prn :format fmt :tree t)
  (try (prn :annotated (i/add-line-and-column-info-to-metadata "a" t))
    (catch Exception e (prn :exception (str (class e)) :message (.getMessage e))))))
