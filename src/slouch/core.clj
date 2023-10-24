(ns slouch.core
  (:require [clj-http.client :as http]
            [clj-http.conn-mgr :as conn]
            [clj-http.core :as http-core]
            [clojure.core.async :as a]))


(http/with-async-connection-pool {:timeout 5 :threads 4 :insecure? false :default-per-route 10}
  (println conn/*async-connection-manager*)
  (println conn/*connection-manager*)

  (http/request {:method :get
                 :url "http://localhost:5984/"
                 :async? true

                 }
(fn [response] (println "My response" response))
(fn [exception] (println "exception message is: " (.getMessage exception)))
                )

  )

;; (defn foo
;;   "I don't do a whole lot."
;;   [x]
;;   (println x "Hello, World!"))


;; (defn queue-request [request-chan params]
;;   (a/>! request-chan (assoc params :request-chan request-chan)))

;; (defn set-default-params [params]
;;   (merge params
;;          {:async? true
;;           :as :json}))


;; (defn wrap-raise [handler]
;;   (fn [ex]
;;     (handler ex)))

;; (declare wrap-respond)

;; (defn send-request [request respond raise]
;;   (http/request (set-default-params request)
;;                 (wrap-respond respond)
;;                 (wrap-raise raise)))

;; (defn wrap-respond [handler]
;;   (fn [response]
;;     (when-let [{:keys [respond raise] :as another-request} (handler response)]
;;       (send-request (http/reuse-pool another-request
;;                                      response)
;;                     respond raise))))

;; (defn start-connection-pool []
;;   (let [request-chan (a/chan (a/buffer 2000))]
;;     (a/go
;;       (http/with-async-connection-pool {;; Generous timeout, might as well keep them open. Too long will, and
;;                                         ;; we might not be able to detect dead connections
;;                                         :timeout 60
;;                                         :threads 4
;;                                         :insecure? true
;;                                         ;; This value controls the max # of simultaneous connections per host.
;;                                         ;; Since we're only connecting to one host, it would probably be worth
;;                                         ;; expirementing with a high value here. Default is 2, which is likely too small.
;;                                         :default-per-route 1000}
;;         (loop [x 0]
;;           (when-let [{:keys [respond raise] :as request} (a/<! request-chan)]
;;             (println "kick" x)
;;             (send-request request respond raise)
;;             (recur (inc x))))))
;;     request-chan))

;; (http/with-async-connection-pool {:timeout 60
;;                                   :threads 8
;;                                   :insecure? true
;;                                   :default-per-route 1000}
;;   (time
;;    (let [futs (doall
;;                (for [x (range 1000)]
;;                  (let [c (a/chan)]
;;                    (http/request {:method :get
;;                                   :url "http://localhost:5984"
;;                                   :async? true}
;;                                  (fn [resp]
;;                                    (a/>!! c resp))
;;                                  println)
;;                    c)))
;;          results (map a/<!! futs)]
;;      (doseq [r results]
;;        1))))

;; (println "xx")

;; (dotimes [x 10]

;;   )
;; (time
;;    (let [acm (conn/make-reusable-async-conn-manager {:timeout 600
;;                                                      ;; NOTE Threads should == default per route
;;                                                      :threads 16
;;                                                      :default-per-route 16
;;                                                      :insecure? true
;;                                                      ;; TODO test connect timeout
;;                                                      ;; TODO Move into an http.clj
;;                                                      :connect-timeout 1000})
;;          ahclient (http-core/build-async-http-client {} acm)]
;;      (let [futs (doall
;;                  (for [_ (range 1000)]
;;                    (http/request {:method :get
;;                                   :url "http://localhost:5984"
;;                                   :connection-manager acm :http-client ahclient
;;                                   :async? true}
;;                                  (fn [resp]
;;                                    ;; (println resp)
;;                                    resp)
;;                                  println)))
;;            results (map #(.get %) futs)]
;;        (doseq [r results]
;;          r)))
;;    )

;; (http/with-additional-middleware)

;; (a/map)

;; (def pool (start-connection-pool))

;; (a/close! pool)


;; (time

;;  (let [y (atom 0)
;;        x 100]
;;    (dotimes [n x]

;;      (a/>!! pool {:method :get
;;                   :url "http://localhost:5984/"
;;                   :respond (fn [_]
;;                              (swap! y inc)
;;                              nil)
;;                   :raise println})
;;      )
;;    (loop []
;;      (when (not= @y x)
;;        (recur))))
;;  )

;; (http/with-async-connection-pool {:threads 4
;;                                   :default-per-route 4}
;; (time
;;  (let [y (atom 0)
;;        x 100]
;;    (dotimes [n x]
;;     (http/get "http://localhost:5984/"
;;              {:async? true}
;; (fn [_]
;;                          (swap! y inc))
;;     println
;;              )
;;     )

;;    (loop []
;;     (when (not= @y x)
;;       (recur))))

;;  )
;;      )

;; (time
;;  (let [y (atom 0)
;;        x 4]
;;    (dotimes [n x]
;;     (http/get "http://localhost:5984/"
;;              {:async? true}
;; (fn [_]
;;                          (swap! y inc))
;;     println
;;              )
;;     )
;;    (loop []
;;     (when (not= @y x)
;;       (recur))))

;;  )

;; (+ 1 23)

;; (http/reuse-pool)

;; (http/reques)


;; (defn open-pool []
;;   (a/go))

;; (defn startasdfasdf)
