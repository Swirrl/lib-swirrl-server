(ns swirrl-server.middleware.log-request
  (:require [clj-logging-config.log4j :as l4j]
            [clojure.tools.logging :as log]
            [swirrl-server.responses :as r]
            [ring.util.io :as ringio])
  (:import [java.util UUID]
           [org.apache.log4j MDC]
           [java.io PipedInputStream PipedOutputStream]))

(defn make-response-logger
  "Wrap the given function with one that logs response times and
  correctly establishes a swirrl-style log4j MDC logging context over
  the supplied function."
  [func]
  (let [log-ctx (into {} (MDC/getContext)) ;; copy the logging context onto the new thread/function
        start-time (MDC/get "start-time")]
    (fn [os]
      (l4j/with-logging-context log-ctx
        (log/info "Streaming result with function" func)
        (let [result (func os);; run the function
              total-time (when start-time (- (System/currentTimeMillis) start-time))]
          (log/info "RESPONSE finished." (when total-time (str " It took " (str total-time "ms") " to execute")))
          result)))))

(defn streaming-body [func]
  (let [wrapped-func (make-response-logger func)
        is (ringio/piped-input-stream wrapped-func)]
    is))

(defn log-request
  "A ring middleware that logs HTTP requests and responses in the
  Swirrl house-style.  It runs each request in a log4j MDC scope and
  sets a request id to every request.  Additionally it logs the
  time spent serving each request/response.

  Takes an optional map of parameters to scrub from logs, e.g. a map
  like this:

  {:param-a \"scrubbed\"
   :param-b \"hidden\"}

  Will ensure that :param-a is logged as \"scrubbed\" by this
  middleware and that the value at :param-b is replaced by the string
  \"hidden\"."
  ([handler]
   (log-request handler {}))
  ([handler scrub-map]
   (fn [req]
     (let [start-time (System/currentTimeMillis)]
       (l4j/with-logging-context {:reqId (str "req-" (-> (UUID/randomUUID) str (.substring 0 8)))
                                  :start-time start-time}
         (let [logable-params (reduce (fn [acc [k v]]
                                        (assoc acc k v))
                                      (:params req)
                                      scrub-map)]

           (log/info "REQUEST" (:uri req) (-> req :headers (get "accept")) logable-params))
         (let [resp (handler req)
               headers-time (- (System/currentTimeMillis) start-time)]
           (if (instance? java.io.InputStream (:body resp))
             (log/info "RESPONSE" (:status resp) "headers sent after" (str headers-time "ms") "streaming body...")
             (log/info "RESPONSE " (:status resp) "finished.  It took" (str headers-time "ms") "to execute"))

           resp))))))
