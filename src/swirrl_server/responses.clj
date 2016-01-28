(ns swirrl-server.responses
  (:require [clojure.string :as string]
            [schema.core :as s]))

(def SwirrlResponse {:type (s/enum :ok :error)})

(def SwirrlError (merge SwirrlResponse {:type (s/eq :error)
                                        :error s/Keyword
                                        :message s/Str}))

(def OkResponse {:type (s/eq :ok)})

(def default-response-map {:type :ok})

(def default-error-map {:type :error
                        :error :unknown-error
                        :message "An unknown error occured"})

(defn api-response
  "Return a HTTP response of type code, with a JSON content type, and the given
  hash-map as a body."
  [code map]
  {:status code
   :headers {"Content-Type" "application/json"}
   :body (merge default-response-map map)})

(def ^{:doc "Returns a 200 ok response, with a JSON message body containing
{:type :ok}"} ok-response (api-response 200 {:type :ok}))

(defn not-found-response [message]
  (api-response 404 {:type :error
                     :error :not-found
                     :message message}))

(s/defn error-response :- SwirrlError
  "Build a ring response containing a JSON error object."
  ([]
   (error-response 500))

  ([code]
   (error-response code :unknown-error))

  ([code error-type]
   (error-response code error-type (:message default-error-map)))

  ([code error-type msg]
   (error-response code error-type msg {}))

  ([code error-type msg map]
   (api-response code (assoc (merge default-error-map map)
                             :error error-type
                             :message msg))))

(s/defn bad-request-response :- SwirrlError
  "Returns a 'bad request' response from the given error message."
  [s]
  (error-response 422 :invalid-parameters s))

(defmacro when-params
  "Simple macro that takes a set of paramaters and tests that they're
  all truthy.  If any are falsey it returns an appropriate ring
  response with an error message.  The error message assumes that the
  symbol name is the same as the HTTP parameter name."
  [params & form]
  `(if (every? identity ~params)
     ~@form
     (let [missing-params# (string/join (interpose ", " (quote ~params)))
           message# (str "You must supply the parameters " missing-params#)]
       (swirrl-server.responses/bad-request-response message#))))
