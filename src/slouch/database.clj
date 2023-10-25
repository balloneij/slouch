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
  (let [{:keys [http registry]} db]
    (or (snap/lookup id)
        (reg/lookup registry id)
        (->> (doc/get http id)
             (reg/save registry)
             (snap/save)))))

(defn get-rev-doc [db id]
  (let [{:keys [http registry]} db]
    (or (snap/lookup-rev id)
        (reg/lookup-rev registry id)
        (doc/rev http id))))

(defn exists? [db id]
  (some? (get-rev-doc db id)))

(defn remove-doc
  ([db id]
   (let [{:keys [http registry]} db]
     (loop [rev (or (reg/lookup-rev registry id)
                    (snap/lookup-rev id)
                    (doc/rev http id))]
       (when (and rev (not (remove-doc db id rev)))
         (recur (doc/rev http id))))
     true))
  ([db id rev]
   (let [{:keys [http registry]} db]
     (if (doc/remove http id rev)
       (do (snap/delete id)
           (reg/delete registry id)
           true)
       false))))

(defn- insert-doc* [http registry id value]
  (->> (doc/insert http id value)
       (reg/save registry)
       (snap/save)))

(defn insert-doc
  ([db value]
   (let [{:keys [http registry]} db]
     (loop []
       (or (insert-doc* http registry (random-uuid) value)
           (recur)))))
  ([db id value]
   (let [{:keys [http registry]} db]
     (or (insert-doc* http registry id value)
         (throw+ {:type :slouch :error :duplicate-id}
                 (IllegalStateException.
                  (str "A document with an id '" id "' already exists")))))))

(defn get-or-insert-doc [db id value-fn]
  (let [{:keys [http registry]} db
        value (delay (value-fn))]
    (loop []
      (or (get-doc db id)
          (insert-doc* http registry id @value)
          (recur)))))

(defn- update-doc* [http registry id rev value]
  (->> (doc/try-replace http id rev value)
       (reg/save registry)
       (snap/save)))

(defn swap [db id f & args]
  (let [{:keys [http registry]} db]
    (loop [doc (or (reg/lookup registry id)
                   (snap/lookup id)
                   (doc/get http id))]
      (let [rev (:_rev doc)
            value (apply f doc args)]
        (or (update-doc* http registry id rev value)
            (recur (doc/get http id)))))))

(defn reset [db id value]
  (let [{:keys [http registry]} db]
    (loop [rev (or (reg/lookup-rev registry id)
                   (snap/lookup-rev id)
                   (doc/rev http id))]
      (or (update-doc* http registry id rev value)
          (recur (doc/rev http id))))))

(defn compare-and-set [db id old new]
  (let [{:keys [http registry]} db
        rev (:_rev old)]
    (some? (update-doc* http registry id rev new))))
