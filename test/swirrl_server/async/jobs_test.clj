(ns swirrl-server.async.jobs-test
  (:require [swirrl-server.async.jobs :refer :all]
            [clojure.test :refer :all]))

(deftest create-child-job-test
  (let [parent-fn #(println "parent")
        child-fn #(println "second")
        parent-job (create-job parent-fn)]
    (Thread/sleep 100)
    (let [child-job (create-child-job parent-job child-fn)]
      (is (= child-fn (:function child-job)) "Failed to update job function")
      (is (> (:time child-job) (:time parent-job)) "Failed to update job time")
      (is (= (:id parent-job) (:id child-job)) "Job id should not change from parent's"))))
