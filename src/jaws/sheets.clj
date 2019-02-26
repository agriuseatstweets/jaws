(ns jaws.sheets
  (:gen-class)
  (:require [clojure.core.async :refer [>! <! <!! >!! alts!! chan go-loop go timeout alts!]]
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

(defn runner [interval og-terms og-users rech]

  ;; Use a debounced version of the channel
  (let [ch (debounce rech (* 3 interval))]
    (go-loop [terms og-terms 
              users og-users]
      (<! (timeout interval))
      (let [new-terms (get-terms)
            new-users (get-users)]
        (if (or (not= new-terms terms) (not= new-users users))
          (>! ch "Change it up!"))
        (recur new-terms new-users)))))
