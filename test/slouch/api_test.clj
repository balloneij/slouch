(ns slouch.api-test
  (:require
   [slouch.feed-test :refer [test-feed-config]]
   [slouch.api :as sut]
            [clojure.test :as t]))


(sut/database test-feed-config)
