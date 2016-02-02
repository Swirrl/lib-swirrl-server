(ns swirrl-server.errors
  (:require [swirrl-server.responses :as r]
            [swirrl-server.responses :refer [SwirrlError]]
            [clojure.tools.logging :as log]
            [schema.core :as s]))

(defmulti encode-error
  "Convert an Exception into an appropriate API error response object.

  Dispatches on either the exceptions class or if it's a
  clojure.lang.ExceptionInfo the value of its :error key."
  (s/fn [err :- (s/either Throwable {s/Any s/Any})]
    (cond
      (instance? clojure.lang.ExceptionInfo err) (if-let [error-type (-> err ex-data :error)]
                                                   error-type
                                                   (class err))
      (instance? Exception err) (class err))))

(defmethod encode-error Throwable [ex]
  ;; The generic catch any possible exception case
  (r/error-response 500 :unknown-error (.getMessage ex)))

(defmethod encode-error clojure.lang.ExceptionInfo [ex]
  ;; Handle ex-info errors that don't define one of our :error keys
  (r/error-response 500 :unknown-error (.getMessage ex)))

(defmethod encode-error :default [ex]
  (log/error ex "An unhandled error raised")
  (if (instance? clojure.lang.ExceptionInfo ex)
    (let [error-type (:error (ex-data ex))]
      (assert error-type "Because the defmulti dispatches clojure.lang.ExceptionInfo's as :keywords we should never have a nil error-type here" )
      (r/error-response 500 error-type (.getMessage ex)))
    (r/error-response 500 :unhandled-error (str "Unknown error: " (.getMessage ex)))))

(defn wrap-encode-errors
  "A ring middleware to handle and render Exceptions and ex-swirrl
  style errors.  You should mix this into your middlewares as high up
  as you can.

  We don't currently do any content negotiation here, so this will
  return all exceptions as JSON."
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable ex
        (log/error ex "There was an unknown error.  Returning 500")
        (encode-error ex)))))

(defn ex-swirrl
  "Returns a clojure ex-info Exception object which can be thrown, but
  one that follows the Swirrl error object standard.  Where the type
  of error is indicated by an :error key."

  ([error-type-keyword] (ex-swirrl error-type-keyword ""))
  ([error-type-keyword msg]
   (ex-swirrl error-type-keyword msg nil))

  ([error-type-keyword msg data]

   (ex-info msg (assoc data
                       :error error-type-keyword))))
