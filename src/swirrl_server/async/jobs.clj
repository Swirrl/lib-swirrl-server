(ns swirrl-server.async.jobs
  (:require [schema.core :as s])
  (:import (java.util UUID)
           (java.util.concurrent PriorityBlockingQueue)
           (clojure.lang ExceptionInfo)))

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
  {:type (s/eq :error)
   :message s/Str
   :error-class s/Str
   (s/optional-key :details) {s/Any s/Any}})

(def SuccessfulJobResult
  {:type (s/eq :ok)
   (s/optional-key :details) {s/Any s/Any}})

(def JobResult
  (s/either FailedJobResult SuccessfulJobResult))

(defn- failed-job-result [ex details]
  (let [result {:type :error
                :message (.getMessage ex)
                :error-class (.getName (class ex))}]
    (if (some? details)
      (assoc result :details details)
      result)))

(defn job-failed!
  "Mark the given job as failed. If a details map is provided it will
  be associated with the job result under the :details key. If no map
  is provided and ex is an instance of ExceptionInfo the ex-data of
  the exception will be used as the details."
  ([job ex]
   (job-failed! job ex (when (instance? ExceptionInfo ex)
                        (ex-data ex))))
  ([job ex details]
   {:pre [(not (job-completed? job))]
    :post [(job-completed? job)]}
   (complete-job! job (failed-job-result ex details))))

(defn job-succeeded!
  "Complete's the job with complete-job! and sets it's response :type
  as \"ok\" indicating that it completed without error.  If a details
  value is provided it will be added to the job result map under
  the :details key."
  ([job]
   {:pre [(not (job-completed? job))]
    :post [(job-completed? job)]}
   (complete-job! job {:type :ok}))
  ([job details]
   {:pre [(not (job-completed? job))]
    :post [(job-completed? job)]}
   (complete-job! job {:type :ok :details details})))
