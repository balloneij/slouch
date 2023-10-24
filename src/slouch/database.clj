(ns slouch.database
  (:require [slouch.feed :as feed]
            [slouch.http :as http]
            [slouch.snapshot :as snap]
            [slouch.document :as doc]
            [slouch.registry :as reg]
            [slingshot.slingshot :refer [throw+]]))

(defn init [config]
  (-> {:config config}
      (http/init)
      (feed/init)
      (reg/init)))

(defn deinit [db]
  (-> db
      (reg/deinit)
      (feed/deinit)
      (http/deinit)))

(defn wrap-db
  ([handler db]
   (wrap-db handler :db db))
  ([handler k db]
   (fn [request]
     (snap/with-snapshot-context
       (-> request
           (assoc k db)
           (handler))))))

(defn get-doc [db id]
  (let [{:keys [registry http]} db]
    (or (snap/lookup id)
        (reg/lookup registry id)
        (->> (doc/get http id)
             (reg/save registry)
             (snap/save)))))

(defn get-rev-doc [db id]
  (let [{:keys [registry http]} db]
    (or (snap/lookup-rev id)
        (reg/lookup-rev registry id)
        (doc/rev http id))))

(defn exists? [db id]
  (some? (get-rev-doc db id)))

(defn remove-doc
  ([db id]
   (let [{:keys [registry http]} db]
     (loop [rev (or (reg/lookup-rev registry id)
                    (snap/lookup-rev id)
                    (doc/rev http id))]
       (when (and rev (not (remove-doc db id rev)))
         (recur (doc/rev http id))))
     true))
  ([db id rev]
   (let [{:keys [registry http]} db]
     (if (doc/remove http id rev)
       (do (snap/delete id)
           (reg/delete registry id)
           true)
       false))))

(defn insert-doc* [db id value]
  (let [{:keys [registry http]} db]
    (->> (doc/insert http id value)
         (reg/save registry)
         (snap/save))))

(defn insert-doc
  ([db value]
   (loop []
     (or (insert-doc* db (random-uuid) value)
         (recur))))
  ([db id value]
   (or (insert-doc* db id value)
       (throw+ {:type :slouch :error :duplicate-id}
               (IllegalStateException.
                (str "A document with an id '" id "' already exists"))))))

(defn get-or-insert-doc [db id value-fn]
  (let [value (delay (value-fn))]
    (loop []
      (or (get-doc db id)
          (insert-doc* db id @value)
          (recur)))))

(defn swap [db id f & args]
  (let [{:keys [http registry]} db]
    (loop [doc (or (reg/lookup registry id)
                   (snap/lookup id)
                   (doc/get http id))]
      (let [rev (:_rev doc)
            value (apply f doc args)]
        (or (doc/try-replace http id rev value)
            (recur (doc/get http id)))))))

(defn reset [db id value]
  (let [{:keys [http registry]} db]
    (loop [rev (or (reg/lookup-rev registry id)
                   (snap/lookup-rev id)
                   (doc/rev http id))]
      (or (doc/try-replace http id rev value)
          (recur (doc/rev http id))))))

(defn compare-and-set [db id old new]
  (some? (doc/try-replace (:http db) id (:_rev old) new)))

