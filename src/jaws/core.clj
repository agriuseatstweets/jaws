(ns jaws.core
  (:gen-class)
  (:require [clojure.core.async :refer [>! <! <!! >!! chan go-loop go timeout alts!]]
            [reitit.http :as http]
            [reitit.ring :as ring]
            [reitit.core :as r]
            [ring.adapter.jetty :refer [run-jetty]]
            [muuntaja.interceptor]
            [reitit.interceptor.sieppari]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
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


(defn runner [terms users exch]
  (go-loop []
    (<! (timeout 10000))
    (let [c1 (not= terms (sheets/get-terms))
          c2 (not= users (sheets/get-users))]
      (if (or c1 c2)
        (>! exch (Exception. "Change it up!"))))
    (recur)))


(defn -main [& args]
  (let [terms (sheets/get-terms)
        followings (sheets/get-users)
        queue (create-queue (Integer/parseInt (env :t-queue-size)))
        exch (chan)
        publisher (db/make-publisher (env :jaws-topic))
        client (run publisher queue exch terms followings)
        _ (runner terms followings exch)
        error (<!! exch)]

    (do
      (log/error error "Error tweeting!")
      (.stop client)
      (throw error))))
