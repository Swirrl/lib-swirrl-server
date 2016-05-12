(ns swirrl-server.async.jobs
  (:require [swirrl-server.responses :refer [api-response not-found-response OkObject NotFoundObject]]
            [swirrl-server.async.status-routes :refer [finished-job-route]]
            [schema.core :as s])
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

(defn job-completed?
  "Whether the given job has been completed"
  [job]
  (realized? (:value-p job)))

(defn create-child-job
  "Creates a continuation job from the given parent."
  [job child-fn]
  (assoc job :function child-fn :time (System/currentTimeMillis)))

(defn- complete-job!
  "Adds the job to the state map of finished-jobs and delivers the
  supplied result to the jobs promise, which will cause blocking jobs
  to unblock, and give job consumers the ability to receive the
  value."
  [job result]
  (let [{job-id :id promis :value-p} job]
    (deliver promis result)
    (swap! finished-jobs assoc job-id promis)
    result))

(def FailedJobResult
  {:type (s/eq "error")
   :message s/Str
   :error-class s/Str
   (s/optional-key :details) {s/Any s/Any}})

(def SuccessfulJobResult
  {:type (s/eq "ok")
   (s/optional-key :details) {s/Any s/Any}})

(def JobResult
  (s/either FailedJobResult SuccessfulJobResult))

(defn- failed-job-result [ex]
  (let [result {:type "error"
                :message (.getMessage ex)
                :error-class (.getName (class ex))}]
    (if (instance? clojure.lang.ExceptionInfo ex)
      (assoc result :details (ex-data ex))
      result)))

(defn job-failed!
  "Mark the given job as failed"
  [job ex]
  {:pre [(not (job-completed? job))]
   :post [(job-completed? job)]}
  (complete-job! job (failed-job-result ex)))

(defn job-succeeded!
  "Marks the given job as succeeded. If a details value is provided it
  will be added to the job result map under the :details key."
  ([job]
   {:pre [(not (job-completed? job))]
    :post [(job-completed? job)]}
   (complete-job! job {:type "ok"}))
  ([job details]
   {:pre [(not (job-completed? job))]
    :post [(job-completed? job)]}
   (complete-job! job {:type "ok" :details details})))

(def JobSubmittedResponse (merge OkObject
                                 {:finished-job s/Str
                                  :restart-id s/Uuid}))

(defn submitted-job-response
  ([job] (submitted-job-response "" job))
  ([prefix-path job]
   (api-response 202 {:type :ok
                      :finished-job (finished-job-route prefix-path job)
                      :restart-id restart-id})))
