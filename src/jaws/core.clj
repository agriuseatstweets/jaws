(ns jaws.core
  (:gen-class)
  (:require [clojure.core.async :refer [>! <! <!! >!! alts!! chan go-loop go timeout alts!]]
            [jaws.db :as db]
            [jaws.twitter-client :as tw]
            [jaws.sheets :as sheets]
            [clojure.tools.logging :as log]
            [clojure.java.io :refer [input-stream]]
            [environ.core :refer [env]])
  (:import java.util.concurrent.LinkedBlockingQueue))

(defn create-queue [num] (LinkedBlockingQueue. num))

(defn run [publishers queue exch terms followings]
  (try
    (do (db/writer publishers queue exch)
        (tw/connect-queue queue terms followings))
    (catch Exception e (>!! exch e))))

(def refresh-interval (* 1000 (Integer/parseInt (env :jaws-refresh-interval))))

(defn -main []
  (let [terms (sheets/get-terms)
        followings (sheets/get-users)
        queue (create-queue (Integer/parseInt (env :t-queue-size)))
        exch (chan)
        rech (chan)
        publisher (db/make-publisher (env :jaws-topic))
        client (run publisher queue exch terms followings)
        _ (sheets/runner refresh-interval terms followings rech)
        [news ch] (alts!! [exch rech])]

    (do
      (.stop client)
      (condp = ch
        exch (do (log/error news "Error tweeting!") (throw news))
        rech (do (log/info news) (recur))))))
