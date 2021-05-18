(ns jaws.core
  (:gen-class)
  (:require [clojure.core.async :refer [>! <! <!! >!! alts!! chan go-loop go timeout alts!]]
            [jaws.db :as pubsub]
            [jaws.kafka :as kafka]
            [jaws.utils :as u]
            [jaws.twitter-client :as tw]
            [jaws.sheets :as sheets]
            [clojure.tools.logging :as log]
            [clojure.java.io :refer [input-stream]]
            [environ.core :refer [env]]))

(defn log-terms [terms followings]
  (do
    (log/info "STARTING CLIENT WITH: ")
    (doseq [term terms]
      (log/info (str "TERM: " term)))
    (doseq [id followings]
      (log/info (str "USER: " id)))))

;; take writer
(defn run [queue exch terms followings locations]
  (do
      (log-terms terms followings)
      (tw/connect-queue queue terms followings locations exch)))

(defn refresh-interval [] (* 1000 (Integer/parseInt (env :jaws-refresh-interval))))

(defn writer [f queue exch]
  (let [threads (Integer/parseInt (env :t-threads))]
    (u/poller queue threads exch (partial f exch))))

(defn get-write-fn []
  (let [q (env :jaws-queue)]
    (cond
      (= q "kafka") (kafka/publish-fn)
      (= q "pubsub") (pubsub/publish-fn)
      :else (throw (RuntimeException. (str "We don't have a queue that matches: " q))))))

(defn -main []
  (let [terms (sheets/get-terms)
        followings (sheets/get-users)
        locations (sheets/get-locations)
        queue (u/create-queue (Integer/parseInt (env :t-queue-size)))
        exch (chan)
        rech (chan)
        _ (writer (get-write-fn) queue exch)
        client (run queue exch terms followings locations)
        _ (sheets/debounced-runner (refresh-interval) terms followings locations rech)
        [news ch] (alts!! [exch rech])]

    (do
      (.stop client)
      (condp = ch
        exch (do (log/error news "Error tweeting!") (throw news))
        rech (do (log/info news) (recur))))))
