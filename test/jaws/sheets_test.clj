(ns jaws.sheets-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer :all]
            [jaws.sheets :refer :all]))

(testing "gets urls and terms as we wish"
(deftest test-format-urls
    (let [urls ["foo.com" "bar.baz.com"]]
      (is (= (format-urls urls) ["foo com" "bar baz com"]))
      (is (= (format-urls ["foo.com/bar"]) ["foo com/bar"])))))

(deftest test-sort-terms
    (let [urls ["foo.com" "baz.com"]
          tags ["#qux" "quux"]]
      (is (= (sort-terms tags urls) ["#qux" "quux" "foo com" "baz com"]))))
