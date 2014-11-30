# Richelieu

A Clojure library for applying advice.

## Dicussion

While working on thunknyc/profile a couple things dawned on me. First,
there's more than one way to profile code. The profiling library, as
of the writing of this, collects the time elapsed during function
evaluations in an map, while it's quite conceivable that profiled data
can be collected and distributed in many ways. (I'm thinking of
core.async here, specifically.) Second, profiling and tracing are
basically minor variations on the same theme, and it is a moral crime
that profiling and tracing--and any similar library--does not share a
common library to manage decorating (or _advising_, in classic Lisp
terminology) functions. Thus the birth of Richelieu.

For simplicity's sake, Richelieu focuses exclusively on advising
_around_ functions, not _before_ and/or _after_.

### Advice functions

```clojure
(def identity-advice (fn [orig-fn & args] (apply orig-fn args)))
```

## Usage

```clojure
(require '[richelieu.core :refer [advice]])

(defn add [& xs] (apply + xs))

(defn plus1-advice [f & xs] (apply f (map inc xs)))
(defn times2-advice [f & xs] (apply f (map (partial * 2) xs)))

(defn advise-trace
  [f name]
  (advise
   f
   (fn [g & args] (printf "> %s %s\n" name args)
     (let [res (apply g args)]
       (printf "< %s %s\n" name res)
       res))))

(def add* (-> add
              (advise #'plus1-advice)
              (advise-trace :around-plus1)
              (advise #'times2-advice)
              (advise-trace :around-times2)))
```

## License

Copyright © 2014 Edwin Watkeys

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
