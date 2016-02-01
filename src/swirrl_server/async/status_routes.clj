(ns swirrl-server.async.status-routes
  "Ring routes for async jobs."
  (:require [compojure.core :refer [GET routes]]
            [ring.util.io :as rio]
            [ring.util.response :refer [not-found response]]
            [swirrl-server.responses :as api]
            [schema.core :as s])
  (:import [java.util UUID]))


(def JobNotFinished (merge api/NotFoundObject
                           {:restart-id s/Uuid}))

(s/defn job-not-finished-response :- JobNotFinished
  [restart-id :- s/Uuid]
  (-> (api/not-found-response "The specified job-id was not found")
      (assoc-in [:body :restart-id] restart-id)))

(defn finished-job-route [job]
  (str "/status/finished-jobs/" (:id job)))

(defn status-routes [finished-jobs restart-id]
  (GET "/finished-jobs/:job-id" [job-id]
       (let [p (get @finished-jobs (UUID/fromString job-id))]
         (if p
           (api/api-response 200 (assoc @p :restart-id restart-id))
           (job-not-finished-response restart-id)))))
