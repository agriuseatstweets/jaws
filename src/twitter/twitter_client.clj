(ns twitter.twitter-client
     (:require [environ.core :refer [env]])
     (:import (com.twitter.hbc
               ClientBuilder
               httpclient.auth.OAuth1
               core.Client
               core.Constants
               core.endpoint.StatusesFilterEndpoint
               core.processor.StringDelimitedProcessor)))

(defn create-auth []
  (OAuth1. (env :t-consumer-token)
           (env :t-consumer-secret)
           (env :t-access-token)
           (env :t-token-secret)))

(defn create-endpoint [terms followings]
  (-> (StatusesFilterEndpoint.)
      (.followings followings)
      (.trackTerms terms)))

(defn create-client
  "Creates unconnected Hosebird Client"
  [auth endpoint msg-queue]
  (-> (ClientBuilder.)
      (.hosts Constants/STREAM_HOST)
      (.endpoint endpoint)
      (.authentication auth)
      (.processor (StringDelimitedProcessor. msg-queue))
      ;; (.eventMessageQueue event-queue) ;; optional -- use this to process client messages
      (.build)))

(defn connect-client [client] (.connect client) client)

(defn connect-queue [queue terms followings]
  (let [auth (create-auth)
        endpoint (create-endpoint terms followings)
        client (create-client auth endpoint queue)]
    (connect-client client)))
