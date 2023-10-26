(ns slouch.api
  (:require [slouch.database :as db])
  (:import [clojure.lang IRef IAtom]
           [java.lang IllegalArgumentException AutoCloseable]
           [java.io Writer])
  (:refer-clojure :exclude [remove get]))

(defmacro ^:private assert-arg [pred x msg]
  `(when-not (~pred ~x)
     (throw (IllegalArgumentException. ~(str (pr-str x) " " msg)))))

(defprotocol Document
  (id [doc]
    "Returns the document id.")
  (rev [doc]
    "Returns the document revision or nil if the document no longer exists.")
  (exists? [doc]
    "Returns true if the document exists.")
  (value [doc]
    "Similar to (deref doc), but removes CouchDB metadata keys like :_rev and :_id"))

(defrecord CouchDocument [db id]
  Document
  (id [_] id)
  (rev [_] (db/get-rev-doc db id))
  (exists? [_] (db/exists? db id))
  (value [_] (db/value-doc db id))
  IRef
  (deref [_] (db/get-doc db id))
  IAtom
  (swap [_ f] (db/swap db id f))
  (swap [_ f arg] (db/swap db id f arg))
  (swap [_ f arg1 arg2] (db/swap db id f arg1 arg2))
  (swap [_ f x y args] (apply db/swap db id f x y args))
  (compareAndSet [_ old new]
  ;; TODO ASSERT ARGS OLD ID IS THE SAME AND REV EXISTS
    (db/compare-and-set db id old new))
  (reset [_ newval] (db/reset db id newval)))

(defn document [db id]
  (CouchDocument. db id))

(defmethod print-method CouchDocument [doc ^Writer writer]
  (doto writer
    (.write "#")
    (.write (.getName CouchDocument))
    (.write (pr-str (select-keys doc [:id])))))

(defprotocol Database
  (insert [db value] [db id value]
    "Inserts a document. Returns the inserted document. Throws if the provided `id` already exists.")
  (remove [db id] [db id rev]
    "Removes a document. Returns true if the document was deleted or no longer exists. Returns
    false if the provided rev is old.")
  (get [db id]
    "Gets a document if it exists, else returns nil.")
  (get-or-insert [db id value-fn]
    "Gets a document if it exists, else computes a value using value-fn and inserts a new one.
    Returns the existing or inserted document.")
  (view [db ddoc-view] [db ddoc-view opts]
    "Queries a view. Returns a map containing :offset, :rows, and :total-rows. Throws if the
    design doc or view does not exist.

   `ddoc-view` specifies the design document and view name. It can take one of two forms:
    keyword - :design-doc/view-name - For example, :users/by-id or (keyword \"users\" \"by-id\").
    vector - [\"design-doc\" \"view-name\"] - For example, [\"users\" \"by-id\"].

    `opts` change the query:
    :conflicts?       - Include conflicts information in response. Ignored if include-docs
                        isn’t true. Default is false.
    :descending?      - Return the documents in descending order by key. Default is false.
    :end-key          - Stop returning records when the specified key is reached.
    :end-key-doc-id   - Stop returning records when the specified document ID is reached.
                        Ignored if end-key is not set.
    :group?           - Group the results using the reduce function to a group or single row.
                        Implies reduce is true and the maximum group-level. Default is false.
    :group-level      - Specify the group level to be used. Implies group is true.
    :include-docs?    - Include the associated document with each row. Default is false.
    :inclusive-end?   - Specifies whether the specified end key should be included in the result.
                        Default is true.
    :key              - Return only documents that match the specified key.
    :keys             - Return only documents where the key matches one of the keys specified in
                        the array.
    :limit            - Limit the number of the returned documents to the specified number.
    :reduce?          - Use the reduction function. Default is true when a reduce function
                        is defined.
    :skip             - Skip this number of records before starting to return the results.
                        Default is 0.
    :sorted?          - Sort returned rows. Setting this to false offers a performance boost. The
                        total-rows and offset fields are not available when this is set to false.
                        Default is true.
    :stable?          - Whether or not the view results should be returned from a stable
                        set of shards. Default is false.
    :start-key        - Return records starting with the specified key.
    :start-key-doc-id - Return records starting with the specified document ID. Ignored if
                        startkey is not set.
    :update           - Whether or not the view in question should be updated prior to responding
                        to the user. Supported values: true, false, :lazy. Default is true.
    :update-seq?      - Whether to include in the response an update-seq value indicating the
                        sequence id of the database the view reflects. Default is false.")
  (row [db ddoc-view k] [db ddoc-view k opts]
    "Like `view`, but returns the first row matching key `k`, or nil if no matches exist.
    Throws if the design document or view does not exist.

    See `view` documentation for info on query options.")
  (rows [db view] [db view opts]
    "Like `view`, but only returns rows.
    Throws if the design document or view does not exist.

    See `view` documentation for info on query options."))

(defrecord CouchDatabase [config]
  AutoCloseable
  (close [db]
    (db/deinit db)))

(extend-type CouchDatabase
  Database
  (insert
    ([db value]
     (assert-arg map? value "must be a map")
     (document db (:_id (db/insert-doc db value))))
    ([db id value]
     (assert-arg string? id "must be a string")
     (assert-arg map? value "must be a map")
     (document db (:_id (db/insert-doc db id value)))))
  (remove
    ([db id]
     (assert-arg string? id "must be a string")
     (db/remove-doc db id))
    ([db id rev]
     (assert-arg string? id "must be a string")
     (assert-arg string? rev "must be a string")
     (db/remove-doc db id rev)))
  (get [db id]
    (assert-arg string? id "must be a string")
    (when-let [id (:_id (db/get-doc db id))]
      (document db id)))
  (get-or-insert [db id value-fn]
    (assert-arg string? id "must be a string")
    (assert-arg ifn? value-fn "must be a function")
    (document db (:_id (db/get-or-insert-doc db id value-fn)))))

(defmethod print-method CouchDatabase [db ^Writer writer]
  (doto writer
    (.write "#")
    (.write (.getName CouchDatabase))
    (.write (pr-str (select-keys (:config db) [:url :name])))))

(defn database
  "Configuration
  :url - URL to CouchDB instance
  :name - Name of the database
  :username - CouchDB username
  :password - CouchDB password
  :insecure? - Connect to CouchDB regardless of an unsafe HTTPS connection
               default: false
  :pool-threads - Max number of threads used to connect to CouchDB
                  default: 8
  :pool-timeout - Seconds to keep connections open before automatically closing them.
                  default: 60
  :connection-timeout - Milliseconds to wait before aborting a new connection attempt,
                        or 0, meaning no timeout
                        default: 5000
  :socket-timeout - Milliseconds of data silence to wait before abandoning an established connection,
                    or 0, meaning no timeout
                    default: 5000
  :session-auth-threshold - Seconds of session time remaining before reauthenticating
                            default: 60
  :session-timing-error - Seconds remaining before considering a session expired. At a minimum,
                          consider setting this value greater than socket-timeout + connection-timeout
                          default: 30
  :feed-refresh-interval - Milliseconds to keep a continuous connection open on /db/_changes to
                           watch for updates to documents stored in cache. A lower interval means
                           cache documents are added/removed to the watch more quickly to the watch list,
                           at the expense of reopening connections more frequently.
                           default: 10000
  :cache-doc-ttl - Minutes to keep documents stored in memory
                   default: 15
  "
  ^CouchDatabase
  [{:as config
    :keys [url name
           username password insecure?
           pool-threads pool-timeout
           connection-timeout socket-timeout
           session-auth-threshold session-timing-error
           feed-refresh-interval cache-doc-ttl]
    :or {insecure? false
         pool-threads 8 pool-timeout 60
         connection-timeout 5000 socket-timeout 5000
         session-auth-threshold 60 session-timing-error 30
         feed-refresh-interval 10000 cache-doc-ttl 15}}]
  (assert-arg string? url "must be a string")
  (assert-arg string? name "must be a string")
  (assert-arg string? username "must be a string")
  (assert-arg string? password "must be a string")
  (assert-arg boolean? insecure? "must be a boolean")
  (assert-arg pos? pool-threads "must be a postive number")
  (assert-arg pos? pool-timeout "must be a postive number")
  (assert-arg #(not (neg? %)) connection-timeout "must be a postive number or zero")
  (assert-arg #(not (neg? %)) socket-timeout "must be a postive number or zero")
  (assert-arg pos? session-auth-threshold "must be a postive number")
  (assert-arg pos? session-timing-error "must be a postive number")
  (assert-arg pos? feed-refresh-interval "must be a postive number")
  (assert-arg pos? cache-doc-ttl "must be a postive number")
  (assert-arg #(not (neg? %)) socket-timeout "must be a postive number or zero")
  (-> {:url url :name name :username username :password password
       :insecure? insecure? :pool-threads pool-threads
       :pool-timeout pool-timeout :connection-timeout connection-timeout
       :socket-timeout socket-timeout :session-auth-threshold session-auth-threshold
       :session-timing-error session-timing-error :feed-refresh-interval feed-refresh-interval
       :cache-doc-ttl cache-doc-ttl}
      (db/init)
      (map->CouchDatabase)))

(defmacro with-database
  "Same as (with-open [sym (database config)])"
  {:style/indent 1
   :clj-kondo/lint-as 'clojure.core/with-open}
  [binding & body]
  `(with-open [~(first binding) (database ~(second binding))]
     ~@body))
