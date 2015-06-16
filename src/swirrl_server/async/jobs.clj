(ns swirrl-server.async.jobs
  (:require [swirrl-server.responses :refer [api-response]]
            [swirrl-server.async.status-routes :refer [finished-job-route]])
  (:import (java.util UUID)
           (java.util.concurrent PriorityBlockingQueue)))

(defonce restart-id (UUID/randomUUID))

(defonce ^{:doc "Map of finished jobs to promises containing their results."}
  finished-jobs (atom {}))

(defrecord Job [id priority time function value-p])

(defn create-job
  ([f] (create-job nil f))

  ([priority f]
   (->Job (UUID/randomUUID)
          priority
          (System/currentTimeMillis)
          f
          (promise))))

(defn create-child-job
  "Creates a continuation job from the given parent."
  [job child-fn]
  (assoc job :function child-fn :time (System/currentTimeMillis)))

(defn complete-job!
  "Adds the job to the state map of finished-jobs and delivers the
  supplied result to the jobs promise, which will cause blocking jobs
  to unblock, and give job consumers the ability to receive the
  value."
  [job result]
  (let [{job-id :id promis :value-p} job]
    (deliver promis result)
    (swap! finished-jobs assoc job-id promis)
    result))

(defn submitted-job-response [job]
  (api-response 202 {:type :ok
                     :finished-job (finished-job-route job)
                     :restart-id restart-id}))
