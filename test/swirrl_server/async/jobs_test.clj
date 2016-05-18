(ns swirrl-server.async.jobs-test
  (:require [swirrl-server.async.jobs :refer :all]
            [clojure.test :refer :all]
            [schema.core :as s])
  (:import [clojure.lang ExceptionInfo]))

(deftest create-child-job-test
  (let [parent-fn #(println "parent")
        child-fn #(println "second")
        parent-job (create-job parent-fn)]
    (Thread/sleep 100)
    (let [child-job (create-child-job parent-job child-fn)]
      (is (= child-fn (:function child-job)) "Failed to update job function")
      (is (> (:time child-job) (:time parent-job)) "Failed to update job time")
      (is (= (:id parent-job) (:id child-job)) "Job id should not change from parent's"))))

(defn- get-job-result [job]
  @(:value-p job))

(defn- assert-failure-result [job expected-message expected-class expected-details]
  (let [{:keys [message error-class details] :as result} (get-job-result job)]
    (s/validate FailedJobResult result)
    (is (= expected-message message))
    (is (= (.getName expected-class) error-class))
    (is (= expected-details details))))

(deftest job-completed?-test
  (testing "Completed"
    (let [{:keys [value-p] :as job} (create-job (fn []))]
      (deliver value-p "completed")
      (is (= true (job-completed? job)))))
  (testing "Not completed"
    (let [job (create-job (fn []))]
      (is (= false (job-completed? job))))))

(deftest job-failed-test
  (testing "Java exception"
    (let [msg "Failed :("
          ex (IllegalArgumentException. msg)]
      (testing "without details"
        (let [job (create-job (fn []))]
          (job-failed! job ex)
          (assert-failure-result job msg IllegalArgumentException nil)))

      (testing "with details"
        (let [job (create-job (fn []))
              details {:more :info}]
          (job-failed! job ex details)
          (assert-failure-result job msg IllegalArgumentException details)))))

  (testing "ExceptionInfo"
    (let [msg "Job failed"
          ex-details {}
          ex (ex-info msg ex-details)]

      (testing "without details"
        (let [job (create-job (fn []))]
          (job-failed! job ex)
          (assert-failure-result job msg ExceptionInfo ex-details)))

      (testing "with other details"
        (let [job (create-job (fn []))
              details {:other :info}]
          (job-failed! job ex details))))))

(deftest job-succeeded-test
  (testing "With details"
    (let [job (create-job (fn []))
          details {:foo :bar}]
      (job-succeeded! job details)
      (let [result (get-job-result job)]
        (s/validate SuccessfulJobResult result)
        (is (= details (:details result))))))

  (testing "Without details"
    (let [job (create-job (fn []))]
      (job-succeeded! job)
      (let [result (get-job-result job)]
        (s/validate SuccessfulJobResult result)
        (is (= false (contains? result :details)))))))
