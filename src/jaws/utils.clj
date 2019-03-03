(ns jaws.utils
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :refer [>! <! <!! >!! chan go-loop go timeout]])
  (:import java.util.concurrent.LinkedBlockingQueue
           (clojure.core.async.impl.channels ManyToManyChannel)))

(defn create-queue [num] (LinkedBlockingQueue. num))

(defn poller [queue workers exch f]
  (doseq [n (range workers)]
    (go-loop []
      (try
        (let [msg (.poll queue)]
          (if (nil? msg)
            (<! (timeout 100))
            (let [res (f msg)]
              (if (instance? ManyToManyChannel res)
                (<! res)))))
        (catch Exception e (>! exch e)))
      (recur))))
