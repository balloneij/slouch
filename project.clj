(defproject slouch "0.0.0"
  :description "An idiomatic Clojure api for CouchDB"
  :url "https://github.com/balloneij/slouch"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/core.async "1.6.681"]
                 [com.taoensso/timbre "6.1.0"]
                 [org.clojure/core.cache "1.0.225"]
                 [clojure.java-time "1.2.0"]
                 [clj-http "3.12.3"]
                 [cheshire "5.11.0"]]
  :repl-options {:init-ns slouch.core})
