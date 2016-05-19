(ns swirrl-server.async.status-routes
  "Ring routes for async jobs."
  (:require [compojure.core :refer [GET routes]]
            [ring.util.io :as rio]
            [ring.util.response :refer [not-found response]]
            [swirrl-server.responses :as api]
            [swirrl-server.async.jobs :as jobs]
            [schema.core :as s]
            [swirrl-server.util :refer [try-parse-uuid]])
  (:import [java.util UUID]))

(def RestartId s/Uuid)

(def SubmittedJobResponse
  (merge api/RingJSONResponse
         {(s/required-key :status) (s/eq 202)
          :body {:type (s/eq :ok)
                 :finished-job s/Str
                 :restart-id RestartId}}))

(def PendingJobResult
  (merge api/NotFoundObject
         {:restart-id RestartId}))

(defn job-response-schema [job-result]
  (merge api/RingJSONResponse
         {:body job-result}))

(def JobNotFinishedResponse (job-response-schema PendingJobResult))

(def JobStatusResult
  (s/either
   PendingJobResult
   jobs/FailedJobResult
   jobs/SuccessfulJobResult))

(def JobStatusResponse (job-response-schema JobStatusResult))

(defn finished-job-route
  ([job]
   (finished-job-route "" job))
  ([prefix-path job]
   (str prefix-path "/status/finished-jobs/" (:id job))))

(s/defn submitted-job-response :- SubmittedJobResponse
  ([job] (submitted-job-response "" job))
  ([prefix-path job]
   (api/json-response 202 {:type :ok
                          :finished-job (finished-job-route prefix-path job)
                          :restart-id jobs/restart-id})))

(s/defn job-not-finished-response :- JobNotFinishedResponse
  [restart-id :- s/Uuid]
  (api/json-response 404
                     {:type :not-found
                      :message "The specified job-id was not found"
                      :restart-id restart-id}))

(s/defn status-routes :- JobStatusResult
  [finished-jobs restart-id :- s/Str]
  (GET "/finished-jobs/:job-id" [job-id]
       (if-let [job-id (try-parse-uuid job-id)]
         (let [p (get @finished-jobs job-id)]
           (if p
             (api/json-response 200 (assoc @p :restart-id restart-id))
             (job-not-finished-response restart-id)))
         (job-not-finished-response restart-id))))
