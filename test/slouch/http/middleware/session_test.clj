(ns slouch.http.middleware.session-test
  (:require [clojure.test :refer :all]
            [slouch.http.middleware.session :refer [nil-session try-volunteer-reauth session-status]]
            [java-time.api :as jt]))

(deftest test-session-status
  (let [future-time (jt/plus (jt/instant) (jt/days 1))
        past-time (jt/minus (jt/instant) (jt/days 1))]
    (is (= :ok
           (session-status {:reauth-at future-time
                            :expires-at future-time})))
    (is (= :expiring
           (session-status {:reauth-at past-time
                            :expires-at future-time})))
    (is (= :expired
           (session-status {:reauth-at past-time
                            :expires-at past-time})))))

(deftest volunteer-reauth
  (let [session (nil-session)
        winner (try-volunteer-reauth session)
        loser (try-volunteer-reauth session)]

    (is (some? winner))
    (is (nil? loser))

    (testing "Reset after reauth delivered"
      (deliver winner nil)

      (let [winner (try-volunteer-reauth session)
            loser (try-volunteer-reauth session)]
        (is (some? winner))
        (is (nil? loser)))))

  (testing "Only volunteer for expiring or expired sessions"
    (let [future-time (jt/plus (jt/instant) (jt/days 1))
          expired-session (nil-session)
          expiring-session (doto (nil-session)
                             (swap! assoc :expires-at future-time))
          ok-session (doto (nil-session)
                       (swap! assoc :reauth-at future-time :expires-at future-time))]

      (is (some? (try-volunteer-reauth expired-session)))
      (is (nil? (try-volunteer-reauth expired-session)))

      (is (some? (try-volunteer-reauth expiring-session)))
      (is (nil? (try-volunteer-reauth expiring-session)))

      (is (nil? (try-volunteer-reauth ok-session))))))
