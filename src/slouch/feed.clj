(ns slouch.feed
  (:require [clojure.core.async :as a]
            [taoensso.timbre :as log]
            [cheshire.parse :as parse]
            [cheshire.factory :as factory]
            [clj-http.client :as client]
            [clojure.set :refer [difference]]
            [slouch.util :as util]
            [clj-http.conn-mgr :as conn]
            [clj-http.core :as http-core])
  (:import [java.io Reader]
           [com.fasterxml.jackson.core JsonParser]))

(defn- create-parser [^Reader rdr]
  (.createParser factory/json-factory rdr))

(defn- parse-continuous-json* [^JsonParser jp]
  (lazy-seq
   (.nextToken jp)
   (when (some? (.getCurrentToken jp))
     (cons (parse/parse-object jp keyword false nil)
           (parse-continuous-json* jp)))))

(defn parse-continuous-json [rdr]
  (parse-continuous-json* (create-parser rdr)))

(defn- changes-request [config cm http-client]
  (let [{:keys [url name insecure? connection-timeout socket-timeout]} config]
    {:method :post
     :url (str url "/" name "/_changes")
     :content-type :json
     :insecure? insecure?
     :connection-timeout connection-timeout
     :socket-timeout socket-timeout
     :query-params {"filter" "_doc_ids"}
     :unexceptional-status #{200}
     :connection-manager cm
     :http-client http-client}))

(defn- poll-request [req ids seq-id]
  (-> req
      (assoc :as :json)
      (assoc-in [:form-params "doc_ids"] ids)
      (assoc-in [:query-params "since"] (or seq-id "0"))))

(defn- continuous-poll-request [req ids seq-id duration-ms]
  (-> req
      (assoc :as :reader)
      (update :socket-timeout + duration-ms)
      (update :query-params assoc
              "feed" "continuous"
              "timeout" duration-ms)
      (assoc-in [:form-params "doc_ids"] ids)
      (assoc-in [:query-params "since"] (or seq-id "0"))))

(defn- monitor-watchlist [base-req ids last-seq-id duration-ms revisions]
  (let [req (continuous-poll-request base-req ids last-seq-id duration-ms)
        resp (client/request req)]
    (with-open [rdr ^Reader (:body resp)]
      (loop [obj-seq (parse-continuous-json rdr)]
        (if-let [obj (first obj-seq)]
          (if-let [seq-id (:last-seq obj)]
            seq-id
            (let [{id :id [{rev :rev}] :changes deleted? :deleted} obj]
              (if deleted?
                (swap! revisions dissoc id)
                (swap! revisions assoc id rev))
              (recur (rest obj-seq))))
          last-seq-id)))))

(defn- poll [base-req ids]
  (let [req (poll-request base-req ids nil)
        {seq-id :last_seq results :results} (:body (client/request req))]
    {:seq-id seq-id
     :revisions (reduce (fn [m {id :id [{rev :rev}] :changes deleted? :deleted}]
                          (if deleted? m (assoc m id rev)))
                        {} results)}))

(defn- update-watchlist [base-req old-ids new-ids last-seq-id revisions]
  (let [removed-ids (difference old-ids new-ids)
        added-ids (difference new-ids old-ids)
        {seq-id :seq-id new-revisions :revisions} (poll base-req added-ids)]
    (swap! revisions #(-> (apply dissoc % removed-ids)
                          (merge new-revisions)))
    (if (and seq-id last-seq-id)
      (util/rev-min seq-id last-seq-id)
      nil)))

(defn- change-feed [middleware config revisions doc-ids-ch]
  (try
    (let [{refresh-interval :feed-refresh-interval} config
          cm (conn/make-reusable-conn-manager {:threads 1 :default-per-route 1})
          http-client (http-core/build-http-client {} false cm)
          base-req (changes-request config cm http-client)]
      (client/with-middleware middleware
        (loop [doc-ids #{}
               last-seq-id util/rev-max-value]
          (when-let [new-doc-ids (first (a/alts!! [doc-ids-ch] :default :no-change))]
            (if (= new-doc-ids :no-change)
              (if (empty? doc-ids)
                (do (Thread/sleep refresh-interval)
                    (recur doc-ids util/rev-max-value))
                (recur doc-ids
                       (monitor-watchlist base-req doc-ids last-seq-id refresh-interval revisions)))
              (recur new-doc-ids
                     (update-watchlist base-req doc-ids new-doc-ids last-seq-id revisions)))))))
    (catch Throwable e
      (log/error e "Unexpected exception in document change feed"))
    (finally
      (reset! revisions {})
      (a/close! doc-ids-ch))))

(defn doc-revision [feed doc-id]
  (-> feed
      (:revision)
      (deref)
      (get doc-id)))

(defn watch [feed doc-ids]
  (a/>!! (:doc-ids-ch feed) doc-ids))

(defn start-feed [middleware {:as config :keys [url name insecure?
                                                connection-timeout socket-timeout
                                                feed-refresh-interval]}]
  (let [revision (atom {})
        doc-ids-ch (a/chan (a/sliding-buffer 1))
        daemon-f ^Runnable (partial change-feed middleware config revision doc-ids-ch)]
    {:revision revision
     :doc-ids-ch doc-ids-ch
     :daemon (doto (Thread. daemon-f "doc-change-feed") (.start))}))

(defn stop-feed [feed]
  (let [{:keys [^Thread daemon doc-ids-ch]} feed]
    (a/close! doc-ids-ch)
    (.join daemon)))

(defn init [db]
  (let [{{mw :middleware} :http config :config} db]
    (assoc db :feed (start-feed mw config))))

(defn deinit [db]
  (stop-feed (:feed db))
  db)
