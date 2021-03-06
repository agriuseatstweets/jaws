(ns jaws.db
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :refer [>! <! <!! >!! chan go-loop go timeout put! close! thread]]
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

(defn add-callback [exch future]
  (let [ch (chan nil)]
    (ApiFutures/addCallback
     future
     (reify ApiFutureCallback
       (onFailure [this throwable] (do
                                     (log/error throwable)
                                     (close! ch)))
       (onSuccess [this id] (do
                              (log/debug (str "Success in Pub/Sub: " id))
                              (close! ch)))))
    ch))




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

(defn publish-message [publisher exch data]
  (add-callback exch (.publish publisher (build-message data))))

(defn publish-fn []
  (let [publisher (make-publisher (env :jaws-topic))]
    (partial publish-message publisher)))
