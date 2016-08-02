(ns swirrl-server.resource.dataset-test
  (:require [swirrl-server.resource.dataset :as sut]
            [grafter.tabular :refer [test-dataset]]
            [ring.mock.request :refer [request]]
            [schema.core :refer [check] :as s]
            [clojure.test :refer :all]))

(def RingRequestMap (s/pred map? "is a Ring Request Map"))

(def LiberatorContext {:request RingRequestMap})

(def LiberatorRepresentation {:representation {:media-type s/Str}})

(def LiberatorContextWithRepresentation (merge LiberatorContext
                                               LiberatorRepresentation))

(def Nil (s/eq nil))

(defn schema-with-media-type [expected-media-type]
  (assoc-in LiberatorContextWithRepresentation
            [:representation :media-type] (s/eq expected-media-type)))

(def XlsxContext (schema-with-media-type "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))

(def XlsContext (schema-with-media-type "application/vnd.ms-excel"))

(def CsvContext (schema-with-media-type "text/csv"))

(def JsonContext (schema-with-media-type "application/json"))

(defn ->liberator-context [req]
  {:request req})

(defn request-path
  "Build a mock ring request to the specified file path."
  [file-path]
  (request :get file-path))

(defn accept-request [accept-header]
  (assoc-in (request :get "/")
            [:headers "accept"]
            accept-header))

(defn conforms-to-schema [sut-fn schema request error-msg]
  (let [context (sut-fn (->liberator-context request))]
    (is (not (check schema context))
        error-msg)))

(deftest tabular-format-from-path-available-test
  (let [conforms-to-schema (partial conforms-to-schema sut/tabular-format-from-path-available)]

    (conforms-to-schema XlsxContext (request-path "/foo.xlsx")
                        "Expected an .xlsx media type to be set in the liberator context.")

    (conforms-to-schema XlsContext (request-path "/foo/bar.xls")
                        "Expected an .xls media type to be set in the liberator context.")

    (conforms-to-schema CsvContext (request-path "/blah.csv")
                        "Expected a CSV media type to be set in the liberator context.")

    (conforms-to-schema JsonContext (request-path "/blah.json")
                        "Expected a JSON media type to be set in the liberator context.")

    (conforms-to-schema Nil (request-path "/no/file/extension")
                        "Expected nil to be returned as the context when the path format does not match a media type.")))

(deftest tabular-format-acceptable-test
  (let [conforms-to-schema (partial conforms-to-schema sut/tabular-format-acceptable)]

    (conforms-to-schema XlsxContext (accept-request "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        "Expected an .xlsx media type to be set in the liberator context.")

    (conforms-to-schema XlsContext (accept-request "application/vnd.ms-excel")
                        "Expected an .xls media type to be set in the liberator context.")

    (conforms-to-schema CsvContext (accept-request "text/csv")
                        "Expected an .xls media type to be set in the liberator context.")

    (conforms-to-schema JsonContext (accept-request "application/json")
                        "Expected a JSON media type to be set in the liberator context.")
    
    (conforms-to-schema Nil (accept-request "unrecognised/media-type")
                        "Expected a nil liberator context to be returned for an unrecognised media-type.")))

(defn assoc-headers
  "Assoc headers from ring request r2 into ring request r1."
  [r1 r2]
  (assoc r1 :headers (:headers r2)))

(deftest tabular-format-provided-test
  ;; Note this is the combination of both the tabular-format path and acceptable
  ;; behaviours (defined above).

  (let [conforms-to-schema (partial conforms-to-schema sut/tabular-format-provided)]

    (conforms-to-schema CsvContext (assoc-headers (request-path "/foo.csv")
                                                  (accept-request "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        "When there's an accepted format in the path prefer it to the accept header")

    (conforms-to-schema XlsxContext (assoc-headers (request-path "/no/format/in/path")
                                                  (accept-request "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        "When there's no format in the path prefer the accept header")

    (conforms-to-schema CsvContext (assoc-headers (request-path "/blah.unregistered-extension")
                                                  (accept-request "text/csv"))
                        "When there's an unregistered format in the path prefer the accept header")

    (conforms-to-schema XlsContext (assoc-headers (request-path "/blah.unregistered-extension")
                                                  (accept-request "text/csv;q=0.1,application/vnd.ms-excel;q=0.5,unregistered/media-type;q=1.0"))
                        "Do content negotiation when multiple formats are sent")

    (conforms-to-schema Nil (assoc-headers (request-path "/blah")
                                           (accept-request "unregistered/media-type"))
                        "When there's neither a file extension return a nil context")))

(def DatasetResponse {:status (s/eq 200)
                      :headers {(s/eq "Content-Type") (s/eq "text/csv")}
                      :body java.io.InputStream})

(deftest Dataset-as-response-test
  (let [ds (test-dataset 2 2)
        response (liberator.representation/as-response ds {:representation {:media-type "text/csv"}})]
    (is (s/validate
         DatasetResponse
         response)
        "Returns a 200 ok response")

    (is (= "a,b\n0,0\n1,1\n" (slurp (:body response)))
        "Has a CSV dataset in the body")))
