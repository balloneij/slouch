(ns slouch.api-test
  (:require
   [slouch.feed-test :refer [test-feed-config]]
   [slouch.api :as slouch]
   [slouch.document :as doc]
   [slouch.util :as util]
   [clojure.test :refer :all]))

(def db-config test-feed-config)

(defn- valid-rev? [rev]
  (try
    (and (some? rev)
         (pos? (util/parse-rev-iteration rev)))
    (catch Exception _ false)))

(deftest ^:integration api-test
  (testing "README.org quickstart"
    (testing "database"
      (with-open [_db (slouch/database db-config)])

      (slouch/with-database [_db db-config]))

    (testing "documents"
      (slouch/with-database [db db-config]
        (let [doc (slouch/insert db {:sales 0})]
          (is (= 0 (:sales @doc)))

          (swap! doc update :sales inc)
          (is (= 1 (:sales @doc)))

          (reset! doc {:sales 0})
          (is (= 0 (:sales @doc)))

          (slouch/remove db (slouch/id doc))
          (is (not (slouch/exists? doc)))))))

  (slouch/with-database [db db-config]
    (let [{:keys [http]} db]
      (testing "insert documents"
        (let [doc (slouch/insert db {:name "21" :artist "Adele"})
              {:as value id :_id rev :_rev} @doc]
          (is java.util.UUID (parse-uuid id))
          (is (= id (slouch/id doc)))
          (is (doc/get http id) value)
          (is (valid-rev? rev)))

        (let [id (str "api-test-" (random-uuid))
              doc (slouch/insert db id {:name "21" :artist "Adele"})
              {:as value actual-id :_id rev :_rev} @doc]
          (is (= id actual-id))
          (is (= id (slouch/id doc)))
          (is (doc/get http id) value)
          (is (valid-rev? rev))))

      (testing "get documents"
        (let [original-doc (slouch/insert db {:name "Thriller" :artist "Michael Jackson"})
              {:as value id :_id} @original-doc
              doc (slouch/get db id)]
          (is (= original-doc doc))
          (is (= value @doc))
          (is (= id (slouch/id doc)))
          (is (slouch/exists? doc))))

      (testing "get or insert"
        (let [id (str "api-test-" (random-uuid))]
          (is (nil? (slouch/get db id)))

          (let [rand-doc-f (fn [] {:sales (rand-int 1000)})
                doc (slouch/get-or-insert db id rand-doc-f)]
            (is (slouch/exists? doc))
            (is (= id (slouch/id doc)))

            (let [doc2 (slouch/get-or-insert db id rand-doc-f)]
              (is (= doc doc2))
              (is (= (slouch/value doc) (slouch/value doc2)))))))

      (testing "atomic operations"
        (let [id (str "api-test-" (random-uuid))
              doc (slouch/insert db id {:sales 0})
              old-value @doc]
          (let [result (deref doc)]
            (is (= {:sales 0 :_id id :_rev (slouch/rev doc)}
                   result)))

          (let [result (swap! doc update :sales inc)]
            (is (= {:sales 1 :_id id :_rev (slouch/rev doc)}
                   result)))

          (let [result (reset! doc {:sales 208})]
            (is (= {:sales 208 :_id id :_rev (slouch/rev doc)}
                   result)))

          (is (not (compare-and-set! doc old-value {:sales 1})))
          (is (compare-and-set! doc @doc {:sales 1}))))

      (testing "misc"
        (is (= (slouch/document db "123") (slouch/document db "123")))))))
