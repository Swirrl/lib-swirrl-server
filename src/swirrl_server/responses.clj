(ns swirrl-server.responses
  (:require [clojure.string :as string]))

(def default-response-map {:type :ok})

(def default-error-map {:type :error :message "An unknown error occured"})

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
  (api-response 404 {:type :not-found :message message}))

(defn error-response
  [code map]
  (api-response code (merge default-error-map map)))

(defn bad-request-response
  "Returns a 'bad request' response from the given error message."
  [s]
  (error-response 400 {:type :error :message s}))

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
