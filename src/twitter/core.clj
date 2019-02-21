(ns twitter.core
  (:gen-class)
  (:require [clojure.core.async :refer [>! <! <!! >!! chan go-loop go]]
            [twitter.db :as db]
            [twitter.twitter-client :as tw]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]])
  (:import java.util.concurrent.LinkedBlockingQueue))

(defn create-queue [num] (LinkedBlockingQueue. num))

(defn run [publishers queue exch terms followings]
  (try
    (do (db/writer publishers queue exch)
        (tw/connect-queue queue terms followings))
    (catch Exception e (>!! exch e))))

(defn -main [& args]
  ;; TODO get terms... reget on error and use error channel to restart...
  ;; (log/debug (str "Starting Main Process with terms: " terms))
  (let [terms ["tb" "yolo" "trump" "brexit"]
        followings [124690469 25073877]
        queue (create-queue (Integer/parseInt (env :t-queue-size)))
        exch (chan)
        publisher (db/make-publisher (env :jaws-topic))
        client (run publisher queue exch terms followings)
        error (<!! exch)]

    (do
      (log/error error "Error tweeting!")
      (.stop client)
      (throw error))))
