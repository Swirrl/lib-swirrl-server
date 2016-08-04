(ns swirrl-server.async.jobs
  (:require [swirrl-server.responses :refer [api-response]]
            [swirrl-server.async.status-routes :refer [finished-job-route]])

  (:require [schema.core :as s]
            [clj-logging-config.log4j :as l4j]
            [clojure.tools.logging :as log])

  (:import (java.util UUID)
           (org.apache.log4j MDC)))

(defonce restart-id (UUID/randomUUID))

(defonce ^{:doc "Map of finished jobs to promises containing their results."}
  finished-jobs (atom {}))

(defrecord Job [id priority time function value-p])

(defn- wrap-logging-context
  "Preserve the jobId and requestId in log4j logs."
  [f]
  (let [request-id (MDC/get "reqId")] ;; copy reqId off calling thread
    (fn [{job-id :id :as job}]
      (l4j/with-logging-context {:jobId (str "job-" (.substring (str job-id) 0 8))
                                 :reqId request-id}
        (f job)))))

(defn create-job
  ([f] (create-job nil f))

  ([priority f]
   (let [id (UUID/randomUUID)]
     (->Job id
            priority
            (System/currentTimeMillis)
            (wrap-logging-context f)
            (promise)))))

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
