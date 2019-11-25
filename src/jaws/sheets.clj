(ns jaws.sheets
  (:gen-class)
  (:require [clojure.core.async :refer [>! <! <!! >!! alts!! chan go-loop go timeout alts! thread]]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [clojure.java.io :refer [input-stream]]
            [environ.core :refer [env]])
  (:import com.google.api.client.http.javanet.NetHttpTransport
           com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
           com.google.api.client.json.jackson2.JacksonFactory
           com.google.api.services.sheets.v4.Sheets$Builder
           com.google.api.services.sheets.v4.SheetsScopes
           com.google.api.client.googleapis.auth.oauth2.GoogleCredential))

(defn transport [] (GoogleNetHttpTransport/newTrustedTransport))
(defn jackson [] (JacksonFactory/getDefaultInstance))

(defn creds []
  (-> (GoogleCredential/fromStream (input-stream (env :google-application-credentials)))
      (.createScoped [(SheetsScopes/SPREADSHEETS_READONLY)])))

(defn build-sheets []
  (->
   (Sheets$Builder. (transport) (jackson) (creds))
   (.setApplicationName "agrius-jaws")
   (.build)))

(defn range-request [sheet-id ranges]
  (-> (build-sheets)
      (.spreadsheets)
      (.values)
      (.batchGet sheet-id)
      (.setRanges ranges)
      (.execute)
      (get "valueRanges")))

(defn get-range [sheet-id range]
  (let [ranges (filter (complement str/blank?) (map str/trim (str/split range #",")))
        res (range-request sheet-id ranges)]
    (->> res
         (map #(get % "values"))
         (map #(map seq %))
         (flatten)
         (remove nil?)
         (map str/trim))))

(defn too-long? [term] (> (alength (.getBytes term "utf-8")) 60))
(defn format-url [url]  (str/join " " (str/split url #"\.|\/")))
(defn format-urls [urls] (map format-url urls))

(defn sort-terms [tags urls]
  (->>
   (concat tags (format-urls urls))
   (remove too-long?)))

(defn sheet-id [] (env :jaws-sheet-id))

(defn get-hashtags [] (get-range (sheet-id) (env :jaws-sheet-hashtags)))
(defn get-urls [] (get-range (sheet-id) (env :jaws-sheet-urls)))
(defn get-users [] (get-range (sheet-id) (env :jaws-sheet-users)))

(defn clean-location [s]
  (->> (str/split s #",")
       (map str/trim)
       (map #(Float/parseFloat %))))

(defn get-locations []
  (let [locs (get-range (sheet-id) (env :jaws-sheet-locations))]
    (map clean-location locs)))

(defn get-terms []
  (sort-terms (get-hashtags) (get-urls)))

(defn debounce
  ([out ms] (debounce (chan) out ms))
  ([in out ms]
   (go-loop [val (<! in)]
     (log/info "Debouncing changes from Google Sheets")
     (let [timer (timeout ms)
           [new-val ch] (alts! [in timer])]
       (condp = ch
         timer (do (>! out val) (recur (<! in)))
         in (if new-val (recur new-val)))))
   in))

(defn get-new-terms-and-users [terms users locations]
  (thread
    (try
      [(get-terms) (get-users) (get-locations)]
      (catch java.io.IOException e (do
                                     (log/error e "Error Fetching Sheet")
                                     [terms users])))))

(defn runner [interval og-terms og-users og-locations ch]
  (go-loop [terms og-terms
            users og-users
            locations og-locations]
    (<! (timeout interval))
    (let [[new-terms new-users new-locations] (<! (get-new-terms-and-users terms users locations))]
      (if (or (not= new-terms terms) (not= new-users users) (not= new-locations locations))
        (>! ch "Change it up!"))
      (recur new-terms new-users new-locations))))

(defn debounced-runner [interval og-terms og-users og-locations rech]
  (runner interval og-terms og-users og-locations (debounce rech (* 3 interval))))
