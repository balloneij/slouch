(ns slouch.snapshot-test
  (:require [slouch.snapshot :as snap]
            [clojure.test :refer :all]))

(deftest snapshot-context-test
  (let [id "40ba6551-1dde-42a3-aeb4-6162ed5be5e1"
        rev "21-967a00dff5e02add41819138abb3284d"
        doc {:_id id :_rev rev}]

    (testing "within context"
      (snap/with-snapshot-context
        (is (nil? (snap/lookup id)))
        (is (nil? (snap/lookup-rev id)))

        (is (= doc (snap/save doc)))
        (is (= doc (snap/lookup id)))
        (is (= rev (snap/lookup-rev id)))

        (is (snap/delete id))
        (is (snap/delete (random-uuid)))
        (is (snap/delete nil))

        (is (nil? (snap/lookup id)))
        (is (nil? (snap/lookup-rev id)))))

    (testing "outside context"
      (is (nil? (snap/lookup id)))
      (is (nil? (snap/lookup-rev id)))

      (is (= doc (snap/save doc)))
      (is (nil? (snap/save nil)))
      (is (nil? (snap/lookup id)))
      (is (nil? (snap/lookup-rev id)))

      (is (snap/delete id))
      (is (snap/delete nil)))))
