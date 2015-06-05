(ns swirrl-server.async.status-routes-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :refer [request]]
            [swirrl-server.async.status-routes :refer :all]
            [swirrl-server.async.jobs :refer [restart-id create-job]])
  (:import [java.util UUID]))

(def job-return-value {:type :ok})

(defn create-finished-job []
  (let [job (create-job :batch-write (fn []))
        p (:value-p job)]
    (deliver p job-return-value)
    job))

(defn create-failed-job [ex]
  (let [job (create-job :batch-write (fn []))]
    (deliver (:value-p job) {:type :error :exception ex})
    job))

(def job (create-finished-job))

(defn status-routes-handler [jobs-map]
  (status-routes (atom jobs-map) restart-id))

(defn finished-job-id-path [id] (str "/finished-jobs/" id))

(def finished-job-path (comp finished-job-id-path :id))

(def finished-jobs (atom {(:id job) (:value-p job)}))

(deftest finished-jobs-test
  (testing "GET /finished-jobs"
    (testing "with a valid finished job"

      (let [job-path (str "/finished-jobs/" (:id job))
            status-route (status-routes finished-jobs restart-id)
            {:keys [body status]} (status-route (request :get job-path))]

          (is (= 200 status))
          (is (= {:type :ok
                  :restart-id restart-id} body))))

    (testing "with a failed job"
      (let [msg "job failed"
            {:keys [id value-p] :as job} (create-failed-job (RuntimeException. msg))
            status-route (status-routes-handler {id value-p})
            {:keys [body status]} (status-route (request :get (finished-job-path job)))]
        (is (= 200 status)
            (= msg (get-in body ["exception" "message"])))))

    (testing "with an unknown job"
      (let [job-path (finished-job-id-path (UUID/randomUUID))
            status-route (status-routes-handler {})
            status (status-route (request :get job-path))]
        (is (= 404
               (:status status)))
        (is (= restart-id
               (get-in status [:body :restart-id])))))))

