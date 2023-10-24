(ns slouch.util-test
  (:require [slouch.util :as util]
            [clojure.test :refer :all]))

(deftest compare-revisions
  (let [low-rev "32-967a00dff5e02add41819138abb3284d"
        high-rev "201-967a00dff5e02add41819138abb3284d"]
    (is (util/rev-iter>= low-rev low-rev))
    (is (util/rev-iter>= high-rev high-rev))
    (is (util/rev-iter>= high-rev low-rev))
    (is (not (util/rev-iter>= low-rev high-rev)))
    (is (util/rev-iter>= util/rev-max-value high-rev))
    (is (util/rev-iter>= util/rev-max-value low-rev))

    (is (= low-rev (util/rev-min high-rev low-rev)))
    (is (= low-rev (util/rev-min low-rev high-rev)))
    (is (= low-rev (util/rev-min low-rev low-rev)))
    (is (= high-rev (util/rev-min util/rev-max-value high-rev)))))

(deftest newest-doc-test
  (let [x {:_id "1" :_rev "21-967a00dff5e02add41819138abb3284d"}
        y {:_id "1" :_rev "33-867a00dff5e02add41819138abb3284d"}]
    (is (= y (util/newest-doc x y)))
    (is (= y (util/newest-doc y y)))))
