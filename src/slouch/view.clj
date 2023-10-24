(ns slouch.view
  (:require [cheshire.core :as json]))

(defn- option [opts-out opts-in k aliases xf]
  (if-let [opt (first (map #(find opts-in %) aliases))]
    (assoc opts-out k (xf (val opt)))
    opts-out))

(defn- stale-opt [opt-val]
  (condp contains? opt-val
    #{:ok "ok"} "ok"
    #{:update-after :update_after "update_after"} "update_after"))

(defn- update-opt [opt-val]
  (if (#{:lazy "lazy"} opt-val)
    "lazy"
    (boolean opt-val)))

(defn normalize-opts [opts]
  (-> {}
      (option opts "conflicts" [:conflicts? :conflicts] boolean)
      (option opts "descending" [:descending? :descending] boolean)
      (option opts "endkey" [:end-key :endkey :end_key] identity)
      (option opts "endkey_docid" [:end-key-doc-id :endkey-docid
              opts                 :end_key_doc_id :endkey_docid] identity)
      (option opts "group" [:group? :group] boolean)
      (option opts "group_level" [:group-level :group_level] long)
      (option opts "include_docs" [:include-docs? :include-docs :include_docs] boolean)
      (option opts "attachments" [:attachments? :attachments] boolean)
      (option opts "att_encoding_info" [:att-encoding-info? :att-encoding-info :att_encoding_info] boolean)
      (option opts "inclusive_end" [:inclusive-end? :inclusive-end :inclusive_end] boolean)
      (option opts "key" [:key] identity)
      (option opts "keys" [:keys] vec)
      (option opts "limit" [:limit] long)
      (option opts "reduce" [:reduce? :reduce] boolean)
      (option opts "skip" [:skip] long)
      (option opts "sorted" [:sorted? :sorted] boolean)
      (option opts "stable" [:stable? :stable] boolean)
      (option opts "stale" [:stale] stale-opt)
      (option opts "startkey" [:start-key :startkey :start_key] identity)
      (option opts "startkey_docid" [:start-key-doc-id :startkey-docid
              opts                   :start_key_doc_id :startkey_docid] identity)
      (option opts "update" [:update] update-opt)
      (option opts "update_seq" [:update-seq? :update-seq :update_seq] boolean)))

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
