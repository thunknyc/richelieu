(ns richelieu.core
  (:require [clojure.set :refer [union]]))

(def ^:dynamic *current-advised* nil)
(def ^:dynamic *current-suppressed* #{})

(defn ^:private apply-advice
  [advice f args]
  (if (seq advice)
    (apply (first advice)
           (fn [& args] (apply-advice (rest advice) f args))
           args)
    (apply f args)))

(defn ^:private advisor
  [label f advice]
  (if (seq advice)
    (with-meta
      (fn [& args]
        (binding [*current-advised* label]
          (apply-advice (remove *current-suppressed* advice)
                        f args)))
      {::advised-fn f
       ::advice advice})
    f))

(defn advice
  "Returns the sequence of functions currently advising `f` or nil."
  [f]
  (let [fm (meta f)]
    (if-let [g (::advised-fn fm)]
      (::advice fm)
      nil)))

(defn advised
  "Returns the underlying advised function free of any advice, or `f`
  if it is not advised."
  [f]
  (if-not (advice f)
    f
    (-> f meta ::advised-fn)))

(defn unadvise-all
  "Returns the wrapped function 'inside' `f`, or `f` it is not
  advised. Equivalent to `advised`."
  [f]
  (advised f))

(defn ^:private advise*
  [label f advicef]
  (advisor label
           (advised f)
           (conj (advice f) advicef)))

(defn advise
  "Wrap `f` (and any other wrapped advice functions) with
  `advicef`."
  [f advicef]
  (advise* (advised f) f advicef))

(defn- unadvise*
  [label f advicef]
  (advisor label
           (advised f)
           (remove (partial = advicef) (advice f))))

(defn unadvise
  "Remove `advicef` from sequence of advised functions for `f` if
  present. Returns either an advised or unadvised function, depending
  on whether advise remains on `f`."
  [f advicef]
  (unadvise* (advised f) f advicef))

(defn advise-var
  "Change binding of `var` to refer to and advised version of its
  value, advised with `advicef`. No var with
  `:richelieu.core/no-advice` metadata will be advised."
  [var advicef]
  (if (not (-> var meta ::no-advice))
    (alter-var-root
     var
     (fn [_] (advise* var @var advicef)))))

(defn unadvise-var
  "Possibly change binding of `var` to ref to a version of the bound
  value that is not wrapped by `advicef. Bound value may be advised or
  not, depending on whether any advice remains on the bound value."
  [var advicef]
  (alter-var-root var (fn [_] (unadvise* var @var advicef))))

(defn ^:private advised-by?
  [advicef f]
  (some #{advicef} (advice f)))

(defn toggle-var-advice
  "Update binding of `var` so that it is advised by `advicef` if it
  wasn't already, or so that it is not advised by `advicef` if it
  previously was."
  [var advicef]
  (if (advised-by? advicef @var)
    (unadvise-var var advicef)
    (advise-var var advicef)))

(defn ^:private ns-functions [ns include-privates?]
  (let [varmap (if include-privates? (ns-interns ns)
                   (ns-publics ns))]
    (->> (filter (fn [[_ bound]] (fn? @bound)) varmap)
         (map second))))

(defn advise-ns
  "Add advice function `advicef` to each function-containing var in
  `ns`. If `advise-privates?` is present and true, also advise private
  functions inside `ns`. No vars with `:richelieu.core/no-advice`
  metadata will be advised."
  ([ns advicef] (advise-ns ns advicef false))
  ([ns advicef advise-privates?]
   (let [vars (ns-functions ns advise-privates?)]
     (doseq [v vars] (advise-var v advicef)))))

(defn unadvise-ns
  "Remove `advicef` from all public or private vars inside `ns`."
  [ns advicef]
  (let [vars (ns-functions ns true)]
     (doseq [v vars] (unadvise-var v advicef))))

(defn ^:private ns-some-advised?
  [ns advicef]
  (->> (ns-interns ns)
       (map (comp deref second))
       (some (partial advised-by? advice))))

(defn toggle-advise-ns
  "If any var in `ns` is advised by `advicef`, remove `advicef` from
  all function-containing public vars in `ns`. Otherwise, add
  `advicef` to all public function-containing vars in `ns`, or all
  public and private ones if `advise-privates?` is present and
  true. No vars with `:richelieu.core/no-advice` metadata will be
  advised."
  ([ns advicef] (toggle-advise-ns ns advicef false))
  ([ns advicef advise-privates?]
   (if (ns-some-advised? ns advicef)
     (unadvise-ns ns advicef)
     (advise-ns ns advicef))))

(defmacro defadvice
  "Same as `defn`, but flags var as one which should never have advice
  applied."
  [name & decls]
  `(defn ~(with-meta name (assoc (meta name) ::no-advice true)) ~@decls))

(defmacro without-advice
  "Prevent any advice function in `advicefs` evaluate (qua advice) in
  dynamic scope of body."
  [advicefs & body]
  `(binding [*current-suppressed*
             (union *current-suppressed* (set ~advicefs))]
     ~@body))
