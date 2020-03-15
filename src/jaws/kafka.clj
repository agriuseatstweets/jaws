(ns jaws.kafka
  (:require [clojure.tools.logging :as log]
            [kinsky.client :as kc]
            [clojure.core.async :refer [chan close!]]
            [environ.core :refer [env]])
  (:import (org.apache.kafka.clients.producer Callback)))


(defn get-producer []
  (let [p (kc/producer {:bootstrap.servers (env :kafka-brokers)}
                     (kc/string-serializer)
                     (kc/string-serializer))]
    p))

(defn make-callback [ch exch]
  (reify Callback
    (onCompletion [this md throwable]
      (if (nil? throwable)
        (do
          (log/debug (str
                      "Success with record offset: "
                      (.offset md)
                      ". partition: "
                      (.partition md)))
          (close! ch))
        (do

          ;; send to exch if we want to break and restart
          (log/error throwable)
          (close! ch))))))

(defn publish-message [p topic exch data]
  (let [ch (chan nil)
        cb (make-callback ch exch)]
    (do
      (.send (deref p) (kc/->record {:value data :topic topic}) cb)
      ch)))

(defn publish-fn []
  (partial publish-message (get-producer) (env :jaws-topic)))
