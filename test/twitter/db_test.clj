(ns twitter.db-test
  (:require [clojure.test :refer :all]
            [twitter.db :refer :all]))

(deftest test-format-entry
  (let [body "foo"
        user "nandan"
        id "abc"
        date "dateformat"]
    (testing "Returns the proper format"
      (let [entry (format-entry body user id date)]
        (is (= (keys entry) (list :_id :published :added :content)))
        (is (= (keys (:content entry)) (list :link :author :body)))
        (is (= (keys (:content entry)) (list :link :author :body)))
        (is (= (get-in entry [:content :link]) "https://twitter.com/nandan/status/abc"))
        (is (instance? java.util.Date (:added entry)))))))

(deftest test-prepare-entry
  (testing "Full entry prepared"
    (let [json (slurp "test/resources/tweet.json")
          entry (prepare-entry json)]
      (is (= (:_id entry) "tw:853969706558074880"))
      (is (= (get-in entry [:content :author]) "RTE2fm"))
      (is (re-find #"live with @TracyClifford" (get-in entry [:content :body]))))))
