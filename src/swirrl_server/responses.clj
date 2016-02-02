(ns swirrl-server.responses
  (:require [clojure.string :as string]
            [schema.core :as s]))

(def SwirrlObject {:type (s/enum :ok :error)})

(def SwirrlError (merge SwirrlObject {:type (s/eq :error)
                                      :error s/Keyword
                                      :message s/Str}))

(def RingHeaders {s/Str (s/either s/Str [s/Str])})

(def RingResponse {:status s/Int
                   :headers RingHeaders
                   :body s/Any})

(def RingJSONResponse (merge RingResponse
                             {:headers (merge RingHeaders {(s/required-key "Content-Type") (s/eq "application/json")})}))

(def RingSwirrlErrorResponse (merge RingJSONResponse
                                    {:body SwirrlError}))

(def OkObject {:type (s/eq :ok)})

(def NotFoundObject (merge SwirrlError {:error (s/eq :not-found)}))

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

(defn not-found-response [message] :- RingSwirrlErrorResponse
  (api-response 404 {:type :error
                     :error :not-found
                     :message message}))

(def ^:private KeywordOrThrowable (s/either s/Keyword java.lang.Throwable))

(s/defn error-response :- RingSwirrlErrorResponse
  "Build a ring response containing a JSON error object.

   The intention is that you can use this with encode-error to override the JSON
   rendering of specific exceptions with specific ring responses.

   For example,

   (error-response 412 :my-error) ;; returns ex-info's of :error type :my-error
   as 412's.

   It can also coerce exceptions:

   (error-response 422 (RuntimeException. \"a message\"))

   And you can override messages held within the exception with something more
   bespoke:

   (error-response 422 (RuntimeException. \"ignore this message\") \"Use this message\")"

  ([]
   (error-response 500))

  ([code :- s/Int]
   (error-response code :unknown-error))

  ([code :- s/Int
    error-type :- KeywordOrThrowable]
   (error-response code error-type nil))

  ([code :- s/Int
    error-type :- KeywordOrThrowable
    msg :- (s/maybe s/Str)]
   (error-response code error-type msg {}))

  ([code :- s/Int
    error-type :- KeywordOrThrowable
    msg :- (s/maybe s/Str)
    data :- {s/Any s/Any}]

   (let [error-obj (cond
                     (keyword? error-type) {:error error-type :message msg}
                     (instance? java.lang.Throwable error-type) (let [error-keyword (-> error-type ex-data :error)]
                                                                  {:error error-keyword
                                                                   :message (.getMessage error-type)}))
         retain-by-identity #(if %1 (if %2 %2 %1) %2)

         ;; define a priority order for overriding :error and :message
         ;; we prefer data the least (to prevent collisions) and args the most
         ;; the retain-by-identity function above keeps overrides the preferred
         ;; value unless the value is nil.
         args {:error (when (keyword? error-type) error-type) :message msg}
         priority [data default-error-map error-obj args]]

     (api-response code
                   (apply merge-with retain-by-identity
                          priority)))))

(s/defn bad-request-response :- RingSwirrlErrorResponse
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
