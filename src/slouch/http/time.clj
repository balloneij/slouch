(ns slouch.http.time
  "Commandeered functions from ring.util.time
  for dealing with time and dates in HTTP requests."
  (:require [clojure.string :as str])
  (:import [java.text ParseException SimpleDateFormat]
           [java.util Locale TimeZone]))

(def ^:no-doc http-date-formats
  {:rfc1123 "EEE, dd MMM yyyy HH:mm:ss zzz"
   :rfc1036 "EEEE, dd-MMM-yy HH:mm:ss zzz"
   :asctime "EEE MMM d HH:mm:ss yyyy"})

(defn- formatter ^SimpleDateFormat [format]
  (doto (SimpleDateFormat. ^String (http-date-formats format) Locale/US)
    (.setTimeZone (TimeZone/getTimeZone "GMT"))))

(defn- attempt-parse [date format]
  (try
    (.parse (formatter format) date)
    (catch ParseException _ nil)))

(defn- trim-quotes [s]
  (str/replace s #"^'|'$" ""))

(defn parse-date
  "Attempt to parse a HTTP date. Returns nil if unsuccessful."
  [http-date]
  (->> (keys http-date-formats)
       (map (partial attempt-parse (trim-quotes http-date)))
       (remove nil?)
       (first)))

(defn format-date
  "Format a date as RFC1123 format."
  [^java.util.Date date]
  (.format (formatter :rfc1123) date))
