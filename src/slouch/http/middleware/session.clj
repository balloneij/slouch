(ns slouch.http.middleware.session
  (:require [clj-http.client :as client]
            [java-time.api :as jt]
            [taoensso.timbre :as log]
            [slouch.http.response :refer [get-header set-cookie]]
            [slouch.http.time :refer [parse-date]]))

(defn nil-session []
  (atom {:reauth-at (jt/instant 0)
         :expires-at (jt/instant 0)
         :reauth-prom (deliver (promise) nil)
         :token ""}))

(defn send-auth-request! [config]
  (let [{:keys [url insecure? username password
                socket-timeout connection-timeout]} config]
    (client/with-middleware client/default-middleware
      (client/post (str url "/_session")
                   {:insecure? insecure?
                    :socket-timeout socket-timeout
                    :connection-timeout connection-timeout
                    :form-params {:name username
                                  :password password}}))))

(defn auth-response [response config]
  (let [now (jt/instant)
        {auth-threshold :session-auth-threshold
         timing-error :session-timing-error} config
        duration (jt/duration
                  (parse-date (get-header response "Date"))
                  (get-in response [:cookies "AuthSession" :expires]))
        expires-at (-> (jt/plus now duration)
                       (jt/minus (jt/seconds timing-error)))]
    {:expires-at expires-at
     :reauth-at (jt/minus expires-at (jt/seconds auth-threshold))
     :token (get-in response [:cookies "AuthSession" :value])}))

(defn session-status [{:keys [reauth-at expires-at]}]
  (let [now (jt/instant)]
    (cond
      (jt/after? now expires-at) :expired
      (jt/after? now reauth-at)  :expiring
      :else                      :ok)))

(defn try-volunteer-reauth
  "Volunteers the current process to reauthenticate. If successful, returns
  a promise to be delivered AFTER the session has been updated

  A return value of nil indicates a competing thread volunteered first"
  [session]
  (let [reauth-prom (promise)
        old-reauth-promise? (fn [{p :reauth-prom}] (realized? p))
        winning-prom (-> (swap! session
                                (fn [m]
                                  (if (and (#{:expired :expiring} (session-status m))
                                           (old-reauth-promise? m))
                                    (assoc m :reauth-prom reauth-prom)
                                    m)))
                         (:reauth-prom))]
    (when (= winning-prom reauth-prom)
      reauth-prom)))

(defn reauth [config session prom]
  (try
    (log/info "Reauthenticating with CouchDB")
    (let [resp (send-auth-request! config)
          {:keys [expires-at reauth-at token]} (auth-response resp config)
          update-session (fn update-session [s]
                           (if (jt/after? expires-at (:expires-at s))
                             (assoc s
                                    :expires-at expires-at
                                    :reauth-at reauth-at
                                    :token token)
                             s))]
      (-> (swap! session update-session)
          (:token)))
    (finally
      (deliver prom :done))))

(defn wait-for-reauth [session]
  (let [p (-> @session :reauth-prom)]
    (when (not (realized? p))
      (log/debug "Waiting for another thread to reauthenticate with CouchDB")
      @p))
  (-> @session :token))

(defn try-reauth [config session]
  (when-let [prom (try-volunteer-reauth session)]
    (reauth config session prom)))

(defn session-token [config session]
  (let [{:as s token :token} @session]
    (case (session-status s)
      :expired  (or (try-reauth config session)
                    (wait-for-reauth session))
      :expiring (or (try-reauth config session)
                    token)
      :ok       token)))

(defn session-request [request config session]
  (let [token (session-token config session)]
    (-> request
      (set-cookie "AuthSession" token))))

(defn wrap-session
  "Fires a request to create a new Couch session if one does not
  exist or an existing session is near expiry."
  [config]
  (let [session (nil-session)]
    (fn [client]
      (fn [request]
        (client (session-request request config session))))))
