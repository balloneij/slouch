(ns slouch.registry-test
  (:require [slouch.registry :as reg]
            [clojure.test :refer :all]))

(deftest registry-test
  (testing "create, read, delete"
    (let [reg (reg/registry 15 (fn [_]))
          id "255ce80b1928875f253f5fca670d0599"
          rev "1-967a00dff5e02add41819138abb3284d"
          doc {:_id id
               :_rev rev}]
      (is (= doc (reg/save reg doc)))
      (is (= doc (reg/lookup reg id)))
      (is (= rev (reg/lookup-rev reg id)))
      (is (reg/delete reg id))
      (is (nil? (reg/lookup reg id)))))

  (testing "update"
    (let [reg (reg/registry 15 (fn [_]))
          doc-rev-1 {:_id "255ce80b1928875f253f5fca670d0599"
                     :_rev "1-967a00dff5e02add41819138abb3284d"}
          doc-rev-2 {:_id "255ce80b1928875f253f5fca670d0599"
                     :_rev "2-fd5f79360ff727161f22d11ac660bec4"}]
      (is (= doc-rev-1 (reg/save reg doc-rev-1)))
      (is (= doc-rev-1 (reg/save reg doc-rev-1)))
      (is (= doc-rev-2 (reg/save reg doc-rev-2)))
      (is (= doc-rev-2 (reg/save reg doc-rev-1)))))

  (testing "nil documents"
    (let [reg (reg/registry 15 (fn [_]))]
      (is (nil? (reg/save reg nil)))
      (is (reg/delete reg nil))))

  (testing "change watch"
    (let [doc-ids (atom #{})
          reg (reg/registry 15 #(reset! doc-ids %))
          id-a "255ce80b1928875f253f5fca670d0599"
          id-b "255ce80b1928875f253f5fca670d3e15"
          doc-a {:_id id-a
                 :_rev "1-967a00dff5e02add41819138abb3284d"}
          doc-b {:_id id-b
                 :_rev "1-967a00dff5e02add41819138abb3284d"}]
      (is (= #{} @doc-ids))

      (reg/save reg doc-a)
      (is (= #{id-a} @doc-ids))

      (reg/save reg doc-b)
      (is (= #{id-a id-b} @doc-ids))

      (reg/delete reg id-a)
      (is (= #{id-b} @doc-ids))

      (reg/delete reg id-b)
      (is (= #{} @doc-ids)))))
