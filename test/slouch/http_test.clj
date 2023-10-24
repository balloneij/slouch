(ns slouch.http-test
  (:require [slouch.http :as http]
            [clojure.test :refer :all]))

(def test-http-config {:url "http://localhost:5984"
                       :name "test"
                       :username "admin"
                       :password "admin"
                       :socket-timeout 5000
                       :connection-timeout 5000
                       :pool-timeout 60
                       :pool-threads 2
                       :session-auth-threshold 60
                       :session-timing-error 30})


(def ^:dynamic *http* nil)

(defn http-fixture [f]
  (binding [*http* (http/start-http test-http-config)]
    (try
      (f)
      (finally
        (http/stop-http *http*)))))

(use-fixtures :once http-fixture)

(deftest ^:integration get-request
  (is (= 200 (:status (http/get *http* "/_all_docs" {:query-params {"limit" 1}})))))
