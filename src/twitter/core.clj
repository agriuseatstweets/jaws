(ns twitter.core
  (:gen-class)
  (:require [clojure.core.async :refer [>! <! <!! >!! chan go-loop go]]
            [twitter.db :as db]
            [twitter.twitter-client :as tw]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]])
  (:import java.util.concurrent.LinkedBlockingQueue))

(defn create-queue [num] (LinkedBlockingQueue. num))

(defn consumer [queue ch exch]
  (go-loop []
    (try
      (when-let [status (.poll queue)]
        (>! ch status))
      (catch Exception e (>! exch e)))
    (recur)))

(defn run [publishers queue ch exch terms followings]
  (try
    (do (consumer queue ch exch)
        (db/writer publishers ch exch)
        (tw/connect-queue queue terms followings))
    (catch Exception e (>!! exch e))))


(defn -main [& args]
  ;; TODO get terms... reget on error and use error channel to restart...
  (let [terms ["tb" "yolo" "trump" "brexit"]
        followings [124690469 25073877]]
    (loop []
      ;; We restart everything on an error.
      (log/debug (str "Starting Main Process with terms: " terms))
      (let [queue (create-queue (Integer/parseInt (env :t-threads)))
            exch (chan)
            ch (chan)

            ;; TODO - Test CPU usage and possibly split
            ;; so that publishers of dif topics in dif apps
            publishers [(db/make-publisher "agrius-tweethouse-test")
                        (db/make-publisher "agrius-tweetdash-test")]
            client (run publishers queue ch exch terms followings)
            error (<!! exch)]
        (do
          (log/error error "Error in channel!")
          (.stop client)))
      (recur))))
