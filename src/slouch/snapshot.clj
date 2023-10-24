(ns slouch.snapshot
  (:require [slouch.util :as util]))

(def ^:dynamic *ctx* nil)

(defmacro with-snapshot-context
  {:style/indent 0}
  [& body]
  `(binding [*ctx* (atom {})]
     ~@body))

(defn wrap-snapshot-context [handler]
  (fn [request]
    (with-snapshot-context
      (handler request))))

(defn lookup [id]
  (when *ctx*
    (get (deref *ctx*) id)))

(defn lookup-rev [id]
  (:_rev (lookup id)))

(defn save [new-doc]
  (if-let [{id :_id} (and *ctx* new-doc)]
    (-> (swap! *ctx* update id util/newest-doc new-doc)
        (get id))
    new-doc))

(defn delete [id]
  (when *ctx*
    (swap! *ctx* dissoc id))
  true)
