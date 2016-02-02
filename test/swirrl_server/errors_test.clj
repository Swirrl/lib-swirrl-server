(ns swirrl-server.errors-test
  (:require [swirrl-server.errors :refer :all]
            [swirrl-server.responses :refer :all]
            [clojure.test :refer :all]
            [schema.core :as s]
            [schema.test :refer [validate-schemas]]))

(use-fixtures :each validate-schemas)

(defmethod encode-error :encode-error-test-registered-handler [ex]
  (error-response 413 :i-am-a-teapot))

(deftest encode-error-test
  (testing "Unregistered error handlers return ring errors and report their :error type"
    (is (s/validate (assoc-in RingSwirrlErrorResponse
                              [:body :error] (s/eq :an-unregistered-error-handler))
                    (encode-error (ex-swirrl :an-unregistered-error-handler "Test error" {:some :data})))))

  (testing "Registered error handlers return the errors you want"
    (is (s/validate (-> RingSwirrlErrorResponse
                        (assoc-in [:body :error] (s/eq :i-am-a-teapot))
                        (assoc :status (s/eq 413)))
                    (encode-error (ex-swirrl :encode-error-test-registered-handler "Test error" {:some :data}))))))
