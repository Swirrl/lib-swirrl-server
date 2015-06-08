(ns swirrl-server.async.status-routes
  "Ring routes for async jobs."

  (:require [compojure.core :refer [GET routes]]
            [ring.util.io :as rio]
            [ring.util.response :refer [not-found response]]
            [swirrl-server.responses :as api])
  (:import [java.util UUID]))

(defn finished-job-route [job]
  (str "/status/finished-jobs/" (:id job)))

(defn status-routes [finished-jobs restart-id]
  (GET "/finished-jobs/:job-id" [job-id]
       (let [p (get @finished-jobs (UUID/fromString job-id))]
         (if p
           (api/api-response 200 (assoc @p :restart-id restart-id))
           (->
             (api/not-found-response "The specified job-id was not found")
             (assoc-in [:body :restart-id] restart-id))))))