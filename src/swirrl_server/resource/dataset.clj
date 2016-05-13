(ns swirrl-server.resource.dataset
  (:require [grafter.tabular :refer [write-dataset]]
            [ring.util.io :refer [piped-input-stream]]
            [clout.core :refer [route-matches]]
            [liberator.core :refer [defresource resource]]
            [liberator.representation :refer [Representation as-response]]
            [liberator.conneg :refer [best-allowed-content-type stringify]])
  (:import incanter.core.Dataset))

(def format-extension->mime-type {"csv" "text/csv"
                                  "json" "application/json"
                                  "xls" "application/vnd.ms-excel"
                                  "xlsx" "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                  "html" "text/html"})

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

(defn- dispatch-on-media-type
  "Get the media-type from the second argument to dispatch on.  For
  use with the multimethods below."
  [_dataset {:keys [representation] :as ctx}]
  (get representation :media-type))

(defmulti render-dataset
  "Convert an incanter Dataset into another type, e.g. an EDN tree for
  json, or a string of HTML.

  Multimethod to coerce a dataset object into a ring response
  containing the content negotiated media-type.  Extend it to content
  types you wish to support."

  dispatch-on-media-type)

(defmulti present-dataset
  "Multimethod hook to present a Dataset for display depending upon
  the neogitated media-type.  It should return an incanter Dataset.
  This allows different views opportunity to reorganise the dataset
  before finally calling render-dataset."

  dispatch-on-media-type)

(defmethod present-dataset :default [dataset ctx]
  ;; by default just a pass through
  dataset)

(defmethod render-dataset "text/html" [dataset ctx]
  (str "<html><body>"
       "<h1>Dataset</h1>"
       "<p>Override <pre>swirrl-server.resource.dataset/render-dataset</pre> for a better representation</p>"
       "<pre>"
       (pr-str dataset)
       "</pre>"
       "</body></html>"))

(defmethod render-dataset :default [dataset {:keys [representation] :as ctx}]
  ;; all going well we shouldn't need to fall back to something as everything
  ;; should be negotiated well before this but if
  ;; we do text/csv should make debugging easier.
  (let [media-type (get representation :media-type)
        input-stream (piped-input-stream
                      (fn [ostream]
                        (write-dataset ostream dataset :format media-type)))]
    input-stream))


(extend-protocol Representation
  Dataset
  (as-response [dataset {:keys [representation] :as ctx}]
    {:status 200
     :headers {"Content-Type" (get representation :media-type)}
     :body (-> dataset
               (present-dataset ctx)
               (render-dataset ctx))}))
