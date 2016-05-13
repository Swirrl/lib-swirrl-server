(ns swirrl-server.util-test
  (:require [clojure.test :refer :all]
            [swirrl-server.util :refer :all])
  (:import [java.util UUID]))

(deftest try-parse-uuid-test
  (are [invalid] (= nil (try-parse-uuid invalid))
    nil
    "not a uuid")
  (let [u (UUID/randomUUID)]
    (is (= u (try-parse-uuid (str u))))))
