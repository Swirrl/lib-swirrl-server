(ns swirrl-server.async.status-routes
  "Ring routes for async jobs."
  (:require [compojure.core :refer [GET routes]]
            [ring.util.io :as rio]
            [ring.util.response :refer [not-found response]]
            [swirrl-server.responses :as api]
            [schema.core :as s])
  (:import [java.util UUID]))


(def JobNotFinishedResponse (merge api/RingSwirrlErrorResponse
                                   {:body (merge api/NotFoundObject
                                                 {:restart-id s/Uuid})}))

(s/defn job-not-finished-response :- JobNotFinishedResponse
  [restart-id :- s/Uuid]
  (-> (api/not-found-response "The specified job-id was not found")
      (assoc-in [:body :restart-id] restart-id)))

(defn finished-job-route
  ([job]
   (finished-job-route "" job))
  ([prefix-path job]
   (str prefix-path "/status/finished-jobs/" (:id job))))

(defn- try-parse-uuid
  "Tries to parse a String into a UUID and returns nil if the
  parse failed."
  [s]
  (when s
    (try
      (UUID/fromString s)
      (catch IllegalArgumentException ex
        nil))))

(defn status-routes
  [finished-jobs restart-id]
  (GET "/finished-jobs/:job-id" [job-id]
       (if-let [job-id (try-parse-uuid job-id)]
         (let [p (get @finished-jobs job-id)]
           (if p
             (api/json-response 200 (assoc @p :restart-id restart-id))
             (job-not-finished-response restart-id)))
         (job-not-finished-response restart-id))))
