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
        (tw/connect-queue queue terms followings)
        (log/info "CONNECTED TO TWITTER"))
    
    (catch Exception e (>!! exch e))))

;; if not use error channel, what else can be used?
;; pubsub channel?

(defn deny []
  {:status 403 :body "Access Denied"})

(defn authorize [ctx]
  (if-let [pass (get-in ctx [:headers "authorization"])]
    (if (= pass (env :sheetsy-secret))
      ctx
      (deny))
    (deny)))

(defn debounce
  ([in ms] (debounce in (chan) ms))
  ([in out ms]
   (go-loop [val (<! in)]
     (let [timer (timeout ms)
           [new-val ch] (alts! [in timer])]
       (condp = ch
         timer (do (>! out val) (recur (<! in)))
         in (if new-val (recur new-val)))))
   out))

(defn handle [ch a]
  (go 
    (<! (timeout 1000))
    (>! ch (Exception. "Restart Jaws."))
    (log/info "HERE")
    {:status 200 :body "Great"}))

(defn make-routes [ch]
  (http/ring-handler
    (http/router
     ["/"
      {:get {:handler #(handle ch %1)}}])

    (ring/create-default-handler)

    {:executor reitit.interceptor.sieppari/executor
     :interceptors [{:enter authorize}]}))

(defn serve [exch]
  (run-jetty (make-routes exch) {:port 3000 :async? true :join? false}))


(defn -main [& args]
  ;; TODO get terms... reget on error and use error channel to restart...
  ;; (log/debug (str "Starting Main Process with terms: " terms))
  (let [terms (sheets/get-terms)
        followings (sheets/get-users)
        queue (create-queue (Integer/parseInt (env :t-queue-size)))
        exch (chan)
        server (serve exch)
        ;; publisher (db/make-publisher (env :jaws-topic))
        ;; client (run publisher queue exch terms followings)
      ]
    (do
      (log/info "LISTENING FOR ERRORS")
      (let [error (<!! exch)]
        (log/error error "Error tweeting!")
        ;; (.stop client)
        (throw error)))
))
