(ns jaws.utils
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :refer [>! <! <!! >!! chan go-loop go timeout]])
  (:import java.util.concurrent.LinkedBlockingQueue))

(defn create-queue [num] (LinkedBlockingQueue. num))

(defn poller [queue threads exch f]
  (doseq [n (range threads)]
    (go-loop []
      (try
        (let [msg (.poll queue)]
          (if (nil? msg)
            (<! (timeout 100))
            (do
              (log/debug (str "Queue size: " (.size queue)))
              (f msg))))
        (catch Exception e (>! exch e)))
      (recur))))
