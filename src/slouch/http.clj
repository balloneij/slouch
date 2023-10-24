(ns slouch.http
  (:require [clj-http.client :as client]
            [clj-http.conn-mgr :as conn]
            [clj-http.core :as http-core]
            [slouch.http.middleware.session :refer [wrap-session]]
            [slouch.http.middleware.exceptions :refer [wrap-exceptions]])
  (:import [org.apache.http.impl.conn PoolingHttpClientConnectionManager])
  (:refer-clojure :exclude [get]))

(defn- conn-manager [config]
  (let [{:keys [pool-timeout pool-threads insecure?]} config]
    (conn/make-reusable-conn-manager
     {:timeout pool-timeout
      :threads pool-threads
      :default-per-route pool-threads
      :insecure? insecure?})))

(defn- http-client [cm]
  (http-core/build-http-client {} false cm))

(defn middleware [config]
  (into []
        (concat client/default-middleware
                [(wrap-session config)
                 wrap-exceptions])))

(defn start-http [{:as config
                   :keys [url name socket-timeout connection-timeout insecure?
                          pool-timeout pool-threads username password
                          session-auth-threshold session-timing-error]}]
  (let [cm (conn-manager config)]
    {:cm cm
     :config config
     :client (http-client cm)
     :middleware (middleware config)}))

(defn stop-http [http]
  (.shutdown ^PoolingHttpClientConnectionManager (:cm http)))

(defn init [db]
  (let [config (:config db)]
    (assoc db :http (start-http config))))

(defn deinit [db]
  (stop-http (:http db))
  db)

(defn- base-couch-request [req config cm client]
  (let [{:keys [method endpoint]} req
        {:keys [url name socket-timeout connection-timeout insecure?]} config]
    (cond-> (assoc req
                   :url (str url "/" name endpoint)
                   :insecure? insecure?
                   :socket-timeout socket-timeout
                   :connect-timeout connection-timeout
                   :accept :json
                   :as :json)
      cm (assoc :connection-manager cm)
      client (assoc :http-client client)
      (#{:post :put} method) (assoc :content-type :json))))

(defn request [http req]
  (let [{:keys [cm client middleware config]} http]
    (client/with-middleware middleware
      (-> req
          (base-couch-request config cm client)
          (client/request)))))

(defn head [http endpoint & [opts]]
  (request http (assoc opts
                       :method :head
                       :endpoint endpoint)))

(defn get [http endpoint & [opts]]
  (request http (assoc opts
                       :method :get
                       :endpoint endpoint)))

(defn put [http endpoint & [opts]]
  (request http (assoc opts
                       :method :put
                       :endpoint endpoint)))

(defn post [http endpoint & [opts]]
  (request http (assoc opts
                       :method :post
                       :endpoint endpoint)))

(defn delete [http endpoint & [opts]]
  (request http (assoc opts
                       :method :delete
                       :endpoint endpoint)))
