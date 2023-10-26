(ns slouch.view
  (:require [slouch.http :as http]
            [cheshire.core :as json]
            [clojure.set :refer [rename-keys]]
            [slouch.registry :as reg]
            [slouch.snapshot :as snap])
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

(defn include-docs [registry rows]
  (vector
   (map (fn [row]
          (->> (:doc row)
               (reg/save registry)
               (snap/save))
          (assoc :doc )))))

(defn view
  ([db ddoc-view]
   (view db ddoc-view {}))
  ([db ddoc-view opts]
   (let [http (:http db)
         [ddoc view] (normalize-ddoc-view ddoc-view)
         opts (normalize-opts opts)
         include-docs? (contains? opts "include_docs")
         resp (http/get http (endpoint ddoc view)
                        {:query-params opts
                         :unexceptional-status #{200}})]
     (-> (:body resp)
         (rename-keys {:total_rows :total-rows})
         (cond-> include-docs? (assoc :rows "penis"))))))

(defn rows
  ([db ddoc-view]
   (rows db ddoc-view {}))
  ([db ddoc-view opts]
   (:rows (view db ddoc-view opts))))

(defn row
  ([db ddoc-view k]
   (row db ddoc-view k {}))
  ([db ddoc-view k opts]
   (:rows (view db ddoc-view (merge opts {:key k :limit 1})))))

(vector '(1 2 3 4))
(into [] '(1 2 3 4))
(vec '(1 2 3 4))

{;; Include conflicts information in response. Ignored if include-docs isn’t true. Default is false.
 :conflicts? false
 ;; Return the documents in descending order by key. Default is false.
 :descending? false
 ;; Stop returning records when the specified key is reached.
 :end-key {:name "zebra"}
 ;; Stop returning records when the specified document ID is reached. Ignored if end-key is not set.
 :end-key-doc-id "255ce80b1928875f253f5fca670d0599"
 ;; Group the results using the reduce function to a group or single row. Implies reduce is true and the maximum group-level. Default is false.
 :group? false
 ;; Specify the group level to be used. Implies group is true.
 :group-level 2
 ;; Include the associated document with each row. Default is false.
 :include-docs? false
 ;; Specifies whether the specified end key should be included in the result. Default is true.
 :inclusive-end? true
 ;; Return only documents that match the specified key.
 :key {:name "dog"}
 ;; Return only documents where the key matches one of the keys specified in the array.
 :keys [{:name "cow"} {:name "horse"} {:name "pig"}]
 ;; Limit the number of the returned documents to the specified number.
 :limit 20
 ;; Use the reduction function. Default is true when a reduce function is defined.
 :reduce? true
 ;; Skip this number of records before starting to return the results. Default is 0.
 :skip 0
 ;; Sort returned rows. Setting this to false offers a performance boost. The total-rows and offset fields are not available when this is set to false. Default is true.
 ;; See Sorting Returned Rows https://docs.couchdb.org/en/stable/api/ddoc/views.html#sorting-returned-rows
 :sorted? true
 ;; Whether or not the view results should be returned from a stable set of shards. Default is false.
 :stable? false
 ;; Return records starting with the specified key.
 :start-key {:name "aardvark"}
 ;; Return records starting with the specified document ID. Ignored if startkey is not set.
 :start-key-doc-id "255ce80b1928875f253f5fca670d3e15"
 ;; Whether or not the view in question should be updated prior to responding to the user. Supported values: true, false, :lazy. Default is true.
 :update true
 ;; Whether to include in the response an update-seq value indicating the sequence id of the database the view reflects. Default is false.
 :update-seq? false}
