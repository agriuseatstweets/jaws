(ns jaws.sheets-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer :all]
            [jaws.sheets :refer :all]))


(deftest test-format-urls
  (testing "gets urls and terms as we wish"
    (let [urls ["foo.co.uk" "bar.baz.com"]]
      (is (= (format-urls urls) ["foo co uk" "bar baz com"]))
      (is (= (format-urls ["baz.foo.com/bar"]) ["baz foo com bar"])))))

(deftest test-sort-terms
  (let [urls ["foo.com" "baz.com/qux"]
        tags ["#qux" "quux"]]
    (is (= (sort-terms tags urls) ["#qux" "quux" "foo com" "baz com qux"]))))

(deftest test-sort-terms-removes-longs
    (let [urls ["foo.com" "baz.com/qux/bar/baz/baz/too/long/for/you/so/walk/downtown/to/go/home"]
          tags ["#qux" "quux"]]
      (is (= (sort-terms tags urls) ["#qux" "quux" "foo com"]))))
