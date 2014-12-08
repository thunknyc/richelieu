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

For an example of Richelieu in action, see
[Henri](http://github.com/thunknyc/henri), a tracing library that can
send statistics to either `*err*`, Hosted Graphite, or Datadog via
`statsd`.

## Advice functions

Advice functions take a function argument followed by zero or more
arguments. They should generally, after optionally futzing with the
arguments, call the passed function, and return a value, which should
generally be the optionally-futzed-with return value.

```clojure
(defn identity-advice
  ^:richelieu.core/no-advice
  [orig-fn & args]
  (apply orig-fn args))
```

You probably want to tell Richelieu to not accidentally advise your
advice functions; this can result in infinite loops. You can can let
Richelieu know that it shouldn't advise a var by associating
`:richelieu.core/no-advice` metadata with it. You can also use the
`defadvice` macro, which does this (and little else) for
you. I.e. `defn-` is to privacy as `defadvice` is to non-advisability.

## Example

```clojure
(require '[richelieu.core :refer :all])

;;; Here are some simple functions.

(defn add [& xs] (apply + xs))
(defn mult [& xs] (apply * xs))
(defn sum-squares [& xs]
  (apply add (map #(mult % %) xs)))

;;; `defadvice` is just a way to use `defn` with ':richelieu/no-advice`
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

;;; You can advise raw functions.

(def add* (-> add
              (advise plus1)
              (advise times2)))

(add* 1 1)

;;; But more often, you'll want to trace vars, which is what the rest
;;; of the example deals with.

;;; This tracing advice shows how to get the current advised object,
;;; which can either be a var or a function value, depending on the
;;; context in which the advice was added.

(def ^:dynamic *trace-depth* 0)

(defn- ^:richelieu.core/no-advice trace-indent []
  (apply str (repeat *trace-depth* \space)))

(defadvice trace
  "Writes passed arguments and passes them to underlying
  function. Writes resulting value before returning it as result."
  [f & args] 
  (printf "%s> %s %s\n" (trace-indent) *current-advised* args)
  (let [res (binding [*trace-depth* (inc *trace-depth*)]
              (apply f args))]
    (printf "%s< %s %s\n" (trace-indent) *current-advised* res)
    res))

(advise-var #'add trace)
(unadvise-var #'add trace)

;;; This is safe because we used `defadvice` to prevent trace from
;;; advising itself--or other advice functions.

(advise-ns 'user trace)

(sum-squares 1 2 3 4)
;;; The above invocation produces the following output:

;; > #'user/sum-squares (1 2 3 4)
;;  > #'user/mult (1 1)
;;  < #'user/mult 1
;;  > #'user/mult (2 2)
;;  < #'user/mult 4
;;  > #'user/mult (3 3)
;;  < #'user/mult 9
;;  > #'user/mult (4 4)
;;  < #'user/mult 16
;;  > #'user/add (1 4 9 16)
;;  < #'user/add 30
;; < #'user/sum-squares 30

;;; You can also suppress the evalutation of advice with the
;;; `without-advice` macro. For example, the following will produce
;;; no tracing output, but will allow any other advice that (possibly
;;; someone else) attached to any function.

(without-advice [trace] (sum-squares (1 2 3 4))) ;; ==> 30, no tracing

;;; Finally, it will often be a good idea to refer to advice functions
;;; via var quoting instead of by a simple reference. This will allow
;;; you to redefine them during development and still add or remove old
;;; versions of attached advice functions, because they will be
;;; associated with the var, not the particular function value that the
;;; var pointed to at the time.

(advise-ns 'user trace)   ;; This works great until you re-eval
                          ;; your `(defadvice trace ...)` form.

(advise-ns 'user #'trace) ;; Infinitesimally slower but highly
                          ;; recommended.
```

## License

Copyright © 2014 Edwin Watkeys

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
