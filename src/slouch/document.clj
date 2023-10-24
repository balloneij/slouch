(ns slouch.document
  (:require [slouch.http :as http]
            [slouch.http.response :refer [get-header]])
  (:refer-clojure :exclude [get remove]))

(def metadata-keys #{:_id :_rev :_deleted
                     :_attachments :_conflicts
                     :_deleted_conflicts :_local_seq
                     :_revs_info :_revisions})

(defn- endpoint [id]
  (str "/" id))

(defn- etag [rev]
  (str "\"" rev "\""))

(defn- parse-etag-revision [etag]
  (when etag
    (subs etag 1 (dec (count etag)))))

(defn get
  "Returns latest doc, or nil if doc does not exist"
  [http id]
  (let [resp (http/get http (endpoint id)
                       {:unexceptional-status #{200 404}})
        {:keys [status body]} resp]
    (case (int status)
      200 body
      404 nil)))

(defn rev
  "Returns latest rev, or nil if doc does not exist"
  [http id]
  (let [resp (http/head http (endpoint id)
                        {:unexceptional-status #{200 404}})
        {:keys [status]} resp]
    (case (int status)
      200 (parse-etag-revision (get-header resp "ETag"))
      404 nil)))

(defn modified? [http id rev]
  (let [resp (http/head http (endpoint id)
                        {:headers {"If-None-Match" (etag rev)}
                         :unexceptional-status #{200 304}})
        {:keys [status]} resp]
    (case (int status)
      200 true
      304 false)))

(defn insert [http id value]
  (let [value (apply dissoc value metadata-keys)
        resp (http/put http (endpoint id)
                       {:form-params value
                        :unexceptional-status #{201 409}})
        {status :status {rev :rev} :body} resp]
    (case (int status)
      201 (assoc value :_id id :_rev rev)
      409 nil)))

(defn remove [http id rev]
  (let [resp (http/delete http (endpoint id)
                          {:headers {"If-Match" (etag rev)}
                           :unexceptional-status #{200 404 409}})
        {:keys [status]} resp]
    (case (int status)
      200 true
      404 true
      409 false)))

(defn try-replace
  "Returns latest doc if successful, or nil if the rev is out-of-date"
  [http id rev new-value]
  (let [new-value (apply dissoc new-value metadata-keys)
        resp (http/put http (endpoint id)
                       {:headers {"If-Match" (etag rev)}
                        :form-params new-value
                        :unexceptional-status #{201 409}})
        {status :status {rev :rev} :body} resp]
    (case (int status)
      201 (assoc new-value :_id id :_rev rev)
      409 nil)))

