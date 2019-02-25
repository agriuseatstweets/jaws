(ns jaws.sheets
  (:gen-class)
  (:require [clojure.tools.logging :as log]
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
         (map clojure.string/trim))))

(defn sheet-id [] (env :jaws-sheet-id))
(defn get-users [] (get-range (sheet-id) "follows!A2:A"))
(defn get-terms [] (get-range (sheet-id) "follows!B2:B"))
