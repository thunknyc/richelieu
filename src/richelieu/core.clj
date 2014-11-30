(ns richelieu.core)

(defn apply-advice
  [advice f args]
  (if (seq advice)
    (apply (first advice)
           (fn [& args] (apply-advice (rest advice) f args))
           args)
    (apply f args)))

(defn- advisor
  [f advice]
  (if (seq advice)
    (with-meta
      (fn [& args]
        (apply-advice advice f args))
      {::advised-fn f
       ::advice advice})
    f))

(defn advice
  [f]
  (let [fm (meta f)]
    (if-let [g (::advised-fn fm)]
      (::advice fm)
      nil)))

(defn advised
  [f]
  (if-not (advice f)
    f
    (-> f meta ::advised-fn)))

(defn advise
  "Wrap `f` (and any other wrapped advice functions) with `advicef`."
  [f advicef]
  (advisor (advised f)
           (conj (advice f) advicef)))

(defn unadvise-all
  [f]
  (advised f))

(defn unadvise
  [f advicef]
  (advisor (advised f)
           (remove #(= advicef %) (advice f))))
