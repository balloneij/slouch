(ns slouch.registry
  (:require [clojure.core.cache.wrapped :as cache]
            [java-time.api :as jt]
            [slouch.util :as util]))

(defn registry [ttl-min watch-f]
  (-> (cache/ttl-cache-factory {} {:ttl (jt/as (jt/minutes ttl-min) :millis)})
      (add-watch :watch-doc-ids (fn [_ _ _ new-state]
                                  (watch-f (into #{} (keys new-state)))))))

(defn lookup [reg id]
  (when-let [entry (cache/lookup reg id)]
    @entry))

(defn lookup-rev [reg id]
  (:_rev (lookup reg id)))

(defn save [reg new-doc]
  (when-let [{id :_id} new-doc]
    (-> (cache/lookup-or-miss reg id (fn [_] (atom new-doc)))
        (swap! util/newest-doc new-doc))))

(defn delete [reg id]
  (cache/evict reg id)
  true)

(defn init [db]
  (let [{:keys [config feed]} db
        ttl-min (:cache-doc-ttl config)
        watch-f (:registry-watch-f feed)]
    (assoc db :registry (registry ttl-min watch-f))))

(defn deinit [db]
  db)
