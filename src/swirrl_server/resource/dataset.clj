(ns swirrl-server.resource.dataset
  (:require [grafter.tabular :refer [write-dataset]]
            [grafter.tabular.common :refer [mapply]]
            [ring.util.io :refer [piped-input-stream]]
            [clout.core :refer [route-matches]]
            [liberator.core :refer [defresource resource]]
            [liberator.representation :refer [Representation as-response]]
            [liberator.conneg :refer [best-allowed-content-type stringify]])
  (:import incanter.core.Dataset))

(extend-protocol Representation
  Dataset
  (as-response [dataset ctx]
    (let [{:keys [representation]} ctx]
      (let [media-type (get representation :media-type "text/csv")
            input-stream (piped-input-stream
                          (fn [ostream]
                            (write-dataset ostream dataset :format media-type)))]
        {:status 200
         :headers {"Content-Type" media-type}
         :body input-stream}))))

(def format-extension->mime-type {"csv" "text/csv"
                                  "xls" "application/vnd.ms-excel"
                                  "xlsx" "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"})

(def supported-tabular-formats (set (vals format-extension->mime-type)))

(defn tabular-format-from-path-available [{:keys [request] :as ctx}]
  (when-let [media-type-from-path (->> request
                                       (route-matches "*.:format")
                                       :format
                                       format-extension->mime-type)]
    (assoc-in ctx [:representation :media-type] media-type-from-path)))

(defn tabular-format-acceptable
  "A function suitable for use with liberator's :media-type-available?
  that supports both content negotiation via the accept header and
  format negotiation via a format file extension at the end of the URL
  path segment.

  In the case that both are provided the format specified by the path
  is prefered.

  This function will return a liberator context with the
  selected [:representation :media-type] assoc'd in it."
  [{:keys [request] :as ctx}]
  (let [acceptable-content-types (get-in request [:headers "accept"])]
    (when-let [accepted-format (best-allowed-content-type acceptable-content-types
                                                          supported-tabular-formats)]
      (assoc-in ctx [:representation :media-type] (stringify accepted-format)))))

(defn tabular-format-provided
  "A function suitable for use with liberator's :media-type-available?
  that supports both content negotiation via the accept header and
  format negotiation via a format file extension at the end of the URL
  path segment.

  In the case that both are provided the format specified by the path
  is prefered.

  This function will return a liberator context with the
  selected [:representation :media-type] assoc'd in it."
  [{:keys [request] :as ctx}]
  (merge (tabular-format-acceptable ctx)
         (tabular-format-from-path-available ctx)))

(def ^{:doc
       "Define some defaults for a liberator resource that returns and content
 negotiates on an incanter Dataset.

You can use these to build a libertor Dataset resource of your own e.g.

(defresource my-resource dataset-resource-defaults [args]
  :handle-ok (fn [ctx] (test-dataset 5 5)))"

       } dataset-resource-defaults
  {:available-media-types supported-tabular-formats
   :media-type-available? tabular-format-provided})

(comment
  ;; This is probable not necessary... The app can just build the resource itself... e.g.

  (defresource my-resource dataset-resource-defaults [args]
    :handle-ok (fn [ctx] (test-dataset 5 5)))

  ;; or something like

  (apply resource (assoc dataset-resource-defaults :handle-ok my-fn))

  (defn ->dataset-resource [dataset-fn]
    (apply resource
           (assoc dataset-resource-defaults :handle-ok dataset-fn))))
