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

(defn get-range [sheet-id range]
  (let [res (-> (build-sheets)
                (.spreadsheets)
                (.values)
                (.get sheet-id range)
                (.execute)
                (get "values"))]
    (->> res
         (map seq)
         (flatten)
         (remove nil?)
         (map clojure.string/trim))))

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

(defn get-new-terms-and-users [terms users]
  (thread
    (try
      [(get-terms) (get-users)]
      (catch java.io.IOException e (do
                                     (log/error e "Error Fetching Sheet")
                                     [terms users])))))

(defn runner [interval og-terms og-users ch]
  (go-loop [terms og-terms
            users og-users]
    (<! (timeout interval))
    (let [[new-terms new-users] (<! (get-new-terms-and-users terms users))]
      (if (or (not= new-terms terms) (not= new-users users))
        (>! ch "Change it up!"))
      (recur new-terms new-users))))

(defn debounced-runner [interval og-terms og-users rech]
  (runner interval og-terms og-users (debounce rech (* 3 interval))))
