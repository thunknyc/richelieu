# Richelieu

A Clojure library for applying advice.

## Usage

![Clojars Project](http://clojars.org/thunknyc/richelieu/latest-version.svg)


## Introduction

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

## Advice functions

Advice functions take a function argument followed by zero or more
arguments. They should generally, after optionally futzing with the
arguments, call the passed function, and return a value, which should
generally be the optionally-futzed-with return value.

```clojure
(defn identity-advice
  ^:unadvisable
  [orig-fn & args]
  (apply orig-fn args))
```

You probably want to tell Richelieu to not accidentally advise your
advice functions; this can result in infinite loops. You can can let
Richelieu know that it shouldn't advise a var by associating
`:unadvisable` metadata with it. You can also use the `defadvice`
macro, which does this (and little else) for you.

## Example

```clojure
(require '[richelieu.core :refer [advice advise-var
                                  *current-advised*
                                  defadvice]])

;;; This is a simple function.
(defn add [& xs] (apply + xs))

;;; `defadvice is just a way to use `defn` with '^:unadvisable`
;;; metadata to prevent crazy infinite advice loops.
(defadvice plus1
  "Adds one to each incoming argument, does nothing to the output."
  [f & xs]
  (apply f (map inc xs)))

(defadvice times2
  "Multiplies each incoming argument by two, does nothing to the
   output."
  [f & xs]
  (apply f (map (partial * 2) xs)))

;;; This tracing advise shows how to get the current advised object,
;;; which can either be a var or a function value, depending on the
;;; context in which the advice with added.
(defadvice trace
  "Writes passed arguments and passes them to underlying
  function. Writes resulting value before returning it as result."
  [f & args]
  (printf "> %s %s\n" *current-advised* args)
  (let [res (apply f args)]
    (printf "< %s %s\n" *current-advised* res)
    res))

;;; You can advise raw functions.
(def add* (-> add
              (advise plus1)
              (advise trace)
              (advise times2)
              (advise trace)))

;;; Or vars.
(advise-var #'add trace)
(unadvise-var #'add trace)

;;; This is safe because we used `defadvice` to prevent trace from
;;; advising itself--or other advice functions.
(advise-ns 'user trace)
```

## License

Copyright © 2014 Edwin Watkeys

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
