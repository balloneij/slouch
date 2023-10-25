(ns slouch.feed-test
  (:require [slouch.feed :as feed]
            [slouch.document :as doc]
            [slouch.http-test :refer [*http* http-fixture test-http-config]]
            [clojure.test :refer :all]
            [java-time.api :as jt]))

(def refresh-interval 1000)

(def test-feed-config (merge test-http-config
                             {:feed-refresh-interval refresh-interval}))

(def ^:dynamic *feed* nil)

(defn feed-fixture [f]
  (binding [*feed* (feed/start-feed (:middleware *http*) test-feed-config)]
    (try
      (f)
      (finally
        (feed/stop-feed *feed*)))))

(use-fixtures :once http-fixture)
(use-fixtures :each feed-fixture)

(defn- random-id []
  (str "feed-test-" (random-uuid)))

(defn- eventually* [pred]
  (loop [remaining-ms (* refresh-interval 2)
         before (jt/instant)]
    (and (or (pos? remaining-ms) nil)
         (or (pred)
             (do (Thread/sleep 250)
                 (recur (- remaining-ms (jt/as (jt/duration before (jt/instant)) :millis))
                        (jt/instant)))))))

(defmacro eventually
  {:style/indent 0}
  [& body]
  `(let [pred# (fn [] ~@body)]
     (eventually* pred#)))

(defn doc [id]
  (let [rev (:_rev (or (doc/insert *http* id {})
                       (doc/get *http* id)))]
    (is (some? rev))
    rev))

(defn update-doc [id rev]
  (let [new-rev (:_rev (doc/try-replace *http* id rev {}))]
    (is (some? new-rev))
    new-rev))

(defn delete-doc [id rev]
  (is (doc/remove *http* id rev)))

(deftest ^:integration monitor-watchlist-test
  (testing "updated docs"
    (let [id (random-id)
          rev (doc id)]
      (feed/watch *feed* #{id})
      (is (eventually (= (feed/doc-revision *feed* id) rev)))
      (let [new-rev (update-doc id rev)]
        (is (eventually (= (feed/doc-revision *feed* id) new-rev))))))

  (testing "deleted docs"
    (let [id (random-id)
          rev (doc id)]
      (feed/watch *feed* #{id})
      (is (eventually (= (feed/doc-revision *feed* id) rev)))
      (delete-doc id rev)
      (is (eventually (nil? (feed/doc-revision *feed* id)))))))

(deftest ^:integration update-watchlist-test
  (testing "change watchlist"
    (let [id-a (random-id)
          id-b (random-id)
          rev-a (doc id-a)
          rev-b (doc id-b)]
      (feed/watch *feed* #{})

      (is (nil? (feed/doc-revision *feed* id-a)))
      (is (nil? (feed/doc-revision *feed* id-b)))

      (feed/watch *feed* #{id-a id-b})

      (is (eventually (= (feed/doc-revision *feed* id-a) rev-a)))
      (is (eventually (= (feed/doc-revision *feed* id-b) rev-b)))

      (feed/watch *feed* #{id-a})
      (is (= rev-a (feed/doc-revision *feed* id-a)))
      (is (eventually (nil? (feed/doc-revision *feed* id-b))))

      (feed/watch *feed* #{})
      (is (eventually (nil? (feed/doc-revision *feed* id-a))))))

  (testing "nonexistent doc"
    (let [id (random-id)]
      (feed/watch *feed* #{id})
      (is (not (eventually (some? (feed/doc-revision *feed* id)))))))

  (testing "deleted doc"
    (let [id (random-id)
          rev (doc id)]
      (delete-doc id rev)
      (feed/watch *feed* #{id})
      (is (not (eventually (some? (feed/doc-revision *feed* id))))))))
