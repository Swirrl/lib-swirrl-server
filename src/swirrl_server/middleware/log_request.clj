(ns swirrl-server.middleware.log-request
  (:require [clj-logging-config.log4j :as l4j]
            [clojure.tools.logging :as log]
            [swirrl-server.responses :as r]
            [ring.util.io :as ringio])
  (:import [java.util UUID]
           [org.apache.log4j MDC]
           [java.io PipedInputStream PipedOutputStream]))

(defn- make-response-streamer [func request-id start-time]
  (fn [os]
    (l4j/with-logging-context {:reqId request-id}
      (log/info "Streaming result with function" func)
      (let [result (func os);; run the function
            total-time (when start-time (- (System/currentTimeMillis) start-time))]
        (log/info "RESPONSE finished." (when total-time (str " It took " (str total-time "ms") " to execute")))
        result))))

(defn streaming-body [func]
  (let [request-id (MDC/get "reqId")
        start-time (MDC/get "start-time") ;; this is for logging only and is set in the log4
        wrapped-func (make-response-streamer func request-id start-time)
        is (ringio/piped-input-stream wrapped-func)]
    is))

(defn log-request
  "A ring middleware that logs HTTP requests and responses in the
  Swirrl house-style.  It runs each request in a log4j MDC scope and
  sets a request id to every request.  Additionally it logs the
  time spent serving each request/response."
  [handler]
  (fn [req]
    (let [start-time (System/currentTimeMillis)]
      (l4j/with-logging-context {:reqId (str "req-" (-> (UUID/randomUUID) str (.substring 0 8)))
                                 :start-time start-time}
        (log/info "REQUEST" (:uri req) (-> req :headers (get "accept")) (:params req))
        (let [resp (handler req)
              headers-time (- (System/currentTimeMillis) start-time)]
          (if (instance? java.io.InputStream (:body resp))
            (log/info "RESPONSE" (:status resp) "headers sent after" (str headers-time "ms") "streaming body...")
            (log/info "RESPONSE " (:status resp) "finished.  It took" (str headers-time "ms") "to execute"))

          resp)))))
