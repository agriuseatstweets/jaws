(ns twitter.db
  (:require [cheshire.core :refer [parse-string]]
            [clojure.tools.logging :as log]
            [clojure.core.async :refer [>! <! <!! >!! chan go-loop go]]
            [clojure.java.io :refer [input-stream]]
            [environ.core :refer [env]])
  (:import (com.google.auth.oauth2 GoogleCredentials)
           (com.google.api.gax.core FixedCredentialsProvider)
           (com.google.pubsub.v1 PubsubMessage ProjectTopicName)
           (com.google.protobuf ByteString)
           (com.google.cloud.pubsub.v1 Publisher)
           (com.google.api.core ApiFuture ApiFutures ApiFutureCallback)))

(defn make-id [id] (str "tw:" id))
(defn get-status [json] (parse-string json true))
(defn get-user [status] (get-in status [:user :screen_name]))
(defn get-body [status] (:text status))
(defn get-date [status]
  (let [date-format (java.text.SimpleDateFormat. "EEE MMM dd HH:mm:ss Z yyyy")]
    (.parse  date-format (:created_at status))))

(defn build-message
  [data]
  (-> (PubsubMessage/newBuilder)
      (.setData (ByteString/copyFromUtf8 data))
      (.build)))

(defn publish-message
  [publisher data exch]
  (let [future (.publish publisher (build-message data))]
    (ApiFutures/addCallback
     future
     (reify ApiFutureCallback
       (onFailure [this throwable] (go (>! exch throwable)))
       (onSuccess [this id] (log/info (str "Published tweet: " id)))))))

(defn credentials-provider []
  (FixedCredentialsProvider/create 
   (GoogleCredentials/fromStream 
    (input-stream 
     (env :google-application-credentials)))))

(defn get-topic [topic] (ProjectTopicName/of (env :google-project-id) topic))

(defn make-publisher [topic]
  (->
   (Publisher/newBuilder (get-topic topic))
   (.setCredentialsProvider (credentials-provider))
   (.build)))

(defn writer [publishers ch exch]
  (doseq [n (range (Integer/parseInt (env :t-threads)))]
    (go-loop []
      (try
        (let [msg (<! ch)]
          (doall (map #(publish-message % msg exch) publishers)))
        (catch Exception e (>! exch e)))
      (recur))))
