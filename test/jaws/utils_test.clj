(ns jaws.core-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer :all]
            [jaws.utils :refer :all]))

(deftest test-poller
  (testing "Poller works with synchronous functions"
    (let [q (create-queue 10)

          f #()]
      (map #(.put queue %) '(1 2 3 4))
      (poller 2 (chan) f)
      (Thread/sleep 10)
      ;; function should have been called
      ())))
