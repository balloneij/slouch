(ns slouch.http.middleware.exceptions-test
  (:require [slouch.http.middleware.exceptions :refer [wrap-exceptions]]
            [slingshot.slingshot :refer [try+]]
            [clojure.test :refer :all]))

(deftest rethrow-slingshot-exception-with-type-slouch
  (let [handle (fn [_] (/ 1337 0))
        handle (wrap-exceptions handle)]
    (is (= :slouch
           (try+
            (handle {})
            :no-error
            (catch [:type :slouch] _
              :slouch)
            (catch Object _
              :unknown))))))
