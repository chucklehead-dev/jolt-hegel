# Grammars as Clojure data

Use `hegel.generator/grammar` to generate strings in a small language, such as
expressions accepted by a parser. Hegel uses the grammar to generate and shrink
those strings. For ordinary records and collections, use the existing generators
or Malli adapter.

```clojure
(require '[hegel.core :as h]
         '[hegel.generator :as g]
         '[hegel.grammar :as grammar])

(def expressions
  {:start :expr
   :rules
   {:expr    [:alt :integer [:cat "(" :expr " + " :expr ")"]]
    :integer [:repeat {:min 1 :max 4} :digit]
    :digit   [:char-range "0" "9"]}})

(def expression-gen
  (g/grammar expressions {:max-size 80 :max-depth 20}))

(h/sample 5 expression-gen)
;; Strings such as "0", "42", or "(1 + 20)"; exact samples vary.
```

`:start` selects the root rule and defaults to `:start`. `:rules` is a nonempty
map of rule keywords to expressions. Rules may refer to themselves or each
other, provided every rule has a route to a finite string.

| Expression | Meaning |
| --- | --- |
| `"hello"` | Literal text, escaped automatically; `""` is the empty string |
| `:word` | Reference to the rule named `:word` |
| `[:cat "hello" " " :word]` | Concatenate one or more expressions |
| `[:alt "yes" "no"]` | Choose one of one or more alternatives |
| `[:optional "-"]` | Zero or one occurrence |
| `[:repeat {:min 1 :max 4} :digit]` | Inclusive bounded repetition; both bounds required, from 0 to 10000 |
| `[:char-range "a" "z"]` | One character from an inclusive Unicode range |

Whitespace is explicit: `[:cat "a" "b"]` produces `"ab"`. The compiler does
not insert spaces. Range endpoints must each contain exactly one Unicode scalar
value, including supplementary characters such as emoji.

Rule names are unqualified keywords beginning with a lowercase ASCII letter,
followed by lowercase letters, digits, underscores or hyphens. Hyphens become
underscores in Lark. A grammar cannot contain both `:some-rule` and `:some_rule`.
Strings always mean literal text; they are never interpreted as raw Lark or
regular expressions.

## Compose and inspect

Grammar definitions are ordinary data:

```clojure
(def signed-expressions
  (assoc-in expressions [:rules :integer]
            [:cat [:optional "-"] [:repeat {:min 1 :max 4} :digit]]))

;; Use the resulting generator with existing Hegel combinators.
(g/fmap (fn [query] {:query query}) (g/grammar signed-expressions))

;; Pure compilation: no native library or generator is loaded by this namespace.
(grammar/compile expressions)
;; => {:source "digit: ...\nexpr: ...\ninteger: ...\n", :start "expr"}
```

`grammar/compile` emits stable rule ordering and validates the data before native
compilation. Invalid operators, references, bounds, names and nonproductive
cycles throw a usage error carrying `:path`, for example `[:rules :expr 2]`.
Native compilation remains authoritative for additional engine constraints.

Generator options are `:max-size`, `:max-depth` and `:alphabet`, with the same
meaning as `grammar-str`. Hegel's size and depth limits still apply: a productive
grammar does not guarantee a derivation fits a particular configured budget.
Select the start rule in the grammar data, rather than in generator options.

## Existing Lark and other generator systems

Keep using `g/grammar-str` for existing Lark source, imports, regex terminals,
ignore directives and other syntax outside this small DSL. There is deliberately
no raw-source splice inside the data DSL.

The DSL produces an ordinary Hegel generator. It does not add a Malli grammar
annotation or convert test.check generators; those integrations remain separate
API work. Native Hegel owns grammar-aware shrinking in both `grammar` and
`grammar-str`.
