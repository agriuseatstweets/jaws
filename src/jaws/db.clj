(ns jaws.db
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
           java.util.concurrent.TimeUnit
           (com.google.api.core ApiFuture ApiFutures ApiFutureCallback)))

(defn build-message
  [data]
  (-> (PubsubMessage/newBuilder)
      (.setData (ByteString/copyFromUtf8 data))
      (.build)))

(defn publish-message
  [publisher data exch]
  (let [future (.publish publisher (build-message data))]
    (try
      (log/debug (str "Published tweet: " @future))
      (catch Throwable e (go (>! exch e))))))

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


(defn writer [publisher queue exch]
  (doseq [n (range (Integer/parseInt (env :t-threads)))]
    (go-loop []
      (try
        (when-let [msg (.poll queue 500 TimeUnit/MILLISECONDS)]
          (do
            (log/debug (str "Queue size: " (.size queue)))
            (publish-message publisher msg exch)))
        (catch Exception e (>! exch e)))
      (recur))))
