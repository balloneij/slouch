(ns slouch.util)

(def rev-max-value (str Long/MAX_VALUE "-max-value"))

(def rev-iteration-re #"^(\d+)\-.+$")

(defn parse-rev-iteration [rev]
  (let [[_ iteration] (re-find rev-iteration-re rev)]
    (parse-long iteration)))

(defn rev-iter>= [x y]
  (or (= x y)
      (> (parse-rev-iteration x)
         (parse-rev-iteration y))))

(defn rev-min [x y]
  (if (rev-iter>= x y) y x))

(defn newest-doc [doc-x doc-y]
  (cond
    (nil? doc-x) doc-y
    (nil? doc-y) doc-x
    (rev-iter>= (:_rev doc-x) (:_rev doc-y)) doc-x
    :else doc-y))
