(ns slouch.http.middleware.exceptions
  (:require [slingshot.slingshot :refer [throw+]]))

(defn wrap-exceptions [client]
  (fn [request]
    (try
      (client request)
      (catch Throwable err
        (throw+ {:type :slouch :error :uncaught-exception}
                err "Unhandled Slouch exception")))))
