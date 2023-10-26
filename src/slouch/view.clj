(ns slouch.view
  (:require [slouch.http :as http]
            [cheshire.core :as json]
            [clojure.set :refer [rename-keys]])
  (:import [com.fasterxml.jackson.core JsonGenerationException]))

(defn- boolean-opt [_ value]
  (boolean value))

(defn- long-opt [k value]
  (try
    (long value)
    (catch ClassCastException _
      (throw (IllegalArgumentException. (str k " must be a number"))))))

(defn- string-opt [_ value]
  (str value))

(defn- json-opt [k value]
  (try
    (json/generate-string value)
    (catch JsonGenerationException e
      (throw (IllegalArgumentException. (str k " value is not json serializable") e)))))

(defn- json-array-opt [k value]
  (->> (try
         (apply vector value)
         (catch IllegalArgumentException e
           (throw (IllegalArgumentException. (str k " must be an array") e))))
         (json-opt k)))

(defn- stale-opt [k value]
  (condp contains? value
    #{:ok "ok"} "ok"
    #{:update-after :update_after "update_after"} "update_after"
    (throw (IllegalArgumentException. (str k " value should be :ok or :update-after")))))

(defn- update-opt [k value]
  (if (#{:lazy "lazy"} value)
    "lazy"
    (boolean-opt k value)))

(defn- option [opts-out opts-in k aliases xf]
  (if-let [[alias-k value] (first (map #(find opts-in %) aliases))]
    (assoc opts-out k (xf alias-k value))
    opts-out))

(defn normalize-opts [opts]
  (-> {}
      (option opts "conflicts" [:conflicts? :conflicts] boolean-opt)
      (option opts "descending" [:descending? :descending] boolean-opt)
      (option opts "endkey" [:end-key :endkey :end_key] json-opt)
      (option opts "endkey_docid" [:end-key-doc-id :endkey-docid
              opts                 :end_key_doc_id :endkey_docid] string-opt)
      (option opts "group" [:group? :group] boolean-opt)
      (option opts "group_level" [:group-level :group_level] long-opt)
      (option opts "include_docs" [:include-docs? :include-docs :include_docs] boolean-opt)
      (option opts "attachments" [:attachments? :attachments] boolean-opt)
      (option opts "att_encoding_info" [:att-encoding-info? :att-encoding-info :att_encoding_info] boolean-opt)
      (option opts "inclusive_end" [:inclusive-end? :inclusive-end :inclusive_end] boolean-opt)
      (option opts "key" [:key] json-opt)
      (option opts "keys" [:keys] json-array-opt)
      (option opts "limit" [:limit] long-opt)
      (option opts "reduce" [:reduce? :reduce] boolean-opt)
      (option opts "skip" [:skip] long-opt)
      (option opts "sorted" [:sorted? :sorted] boolean-opt)
      (option opts "stable" [:stable? :stable] boolean-opt)
      (option opts "stale" [:stale] stale-opt)
      (option opts "startkey" [:start-key :startkey :start_key] json-opt)
      (option opts "startkey_docid" [:start-key-doc-id :startkey-docid
              opts                   :start_key_doc_id :startkey_docid] string-opt)
      (option opts "update" [:update] update-opt)
      (option opts "update_seq" [:update-seq? :update-seq :update_seq] boolean-opt)))

(defn normalize-ddoc-view [ddoc-view]
  (cond
    (keyword? ddoc-view)
    (let [ddoc (namespace ddoc-view)
          view (name ddoc-view)]
      (if (some? ddoc)
        [ddoc view]
        (throw (IllegalArgumentException. "ddoc-view keywords must have a namespace, like :design-doc/view-name"))))

    (seq ddoc-view)
    (let [[ddoc view] ddoc-view]
      (if (and (some? ddoc) (some? view))
        [(str ddoc) (str view)]
        (throw (IllegalArgumentException. "ddoc-view must be a vector, like [\"design-doc\" \"view-name\"]"))))

    :else
    (throw (IllegalArgumentException. "ddoc-view must be a namespaced keyword or a vector"))))

(defn endpoint [ddoc view]
  (str "/_design/" ddoc "/_view/" view))

(defn query [http ddoc view opts]
  (let [resp (http/get http (endpoint ddoc view)
                       {:query-params opts
                        :unexceptional-status #{200}})]
    (-> (:body resp)
        (rename-keys {:total_rows :total-rows}))))
