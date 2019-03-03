(ns jaws.sheets
  (:gen-class)
  (:require [clojure.core.async :refer [>! <! <!! >!! alts!! chan go-loop go timeout alts! thread]]
            [clojure.tools.logging :as log]
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

(defn sheet-id [] (env :jaws-sheet-id))
(defn get-users [] (get-range (sheet-id) "follows!A2:A"))
(defn get-terms [] (get-range (sheet-id) "follows!B2:B"))

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
