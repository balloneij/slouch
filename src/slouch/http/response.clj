(ns slouch.http.response
  "Functions commandeered from ring.util.response and
  slightly modified, if need be, to fit clj-http responses")

(defn find-header [resp ^String header-name]
  (->> (:headers resp)
       (filter #(.equalsIgnoreCase header-name (key %)))
       (first)))

(defn get-header [resp header-name]
  (some-> resp (find-header header-name) val))

(defn set-cookie
  "Sets a cookie on the response. Requires the handler to be wrapped in the
  wrap-cookies middleware."
  [resp name value & [opts]]
  (assoc-in resp [:cookies name] (merge {:value value} opts)))

