(ns jaws.twitter-client
     (:require [environ.core :refer [env]]
               [jaws.utils :as u]
               [clojure.core.async :refer [>! <! <!! >!! chan go-loop go timeout alts!]])
     (:import (com.twitter.hbc
               ClientBuilder
               httpclient.auth.OAuth1
               core.Client
               core.Constants
               core.endpoint.StatusesFilterEndpoint
               core.processor.StringDelimitedProcessor)
              com.twitter.hbc.core.event.EventType))

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
  [auth endpoint msg-queue event-queue]
  (-> (ClientBuilder.)
      (.hosts Constants/STREAM_HOST)
      (.endpoint endpoint)
      (.authentication auth)
      (.processor (StringDelimitedProcessor. msg-queue))
      (.eventMessageQueue event-queue)
      (.build)))

(defn event-handler [exch event]
  (condp = (.getEventType event)
    EventType/STOPPED_BY_ERROR (if-let [e (.getUnderlyingException event)]
                                 (>!! exch e)
                                 (>!! exch (Exception. (.getMessage event))))
    nil))

(defn eventer [queue exch]
  (u/poller queue 2 exch #(event-handler exch %)))

(defn connect-client [client] (.connect client) client)

(defn connect-queue [queue terms followings exch]
  (let [auth (create-auth)
        endpoint (create-endpoint terms followings)
        event-queue (u/create-queue 100)
        client (create-client auth endpoint queue event-queue)]
    (do
      (eventer event-queue exch)
      (connect-client client))))
