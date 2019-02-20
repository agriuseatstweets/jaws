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
  (let [terms ["tb" "yolo" "trump" "brexit"]
        followings [124690469 25073877]]
    (loop []
      ;; We restart everything on an error.
      (log/debug (str "Starting Main Process with terms: " terms))
      (let [queue (create-queue (Integer/parseInt (env :t-queue-size)))
            exch (chan)

            publishers [(db/make-publisher "agrius-tweethouse-test")
                        (db/make-publisher "agrius-tweetdash-test")]
            client (run publishers queue ch exch terms followings)
            error (<!! exch)]
        (do
          (log/error error "Error in channel!")
          (.stop client)))
      (recur))))
