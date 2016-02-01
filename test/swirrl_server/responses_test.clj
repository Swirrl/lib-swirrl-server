(ns swirrl-server.responses-test
  (:require [clojure.test :refer :all]
            [swirrl-server.responses :refer :all]
            [swirrl-server.errors :refer [ex-swirrl]]
            [schema.core :as s]
            [schema.test :refer [validate-schemas]]))

(use-fixtures :each validate-schemas)


(deftest when-params-test
  (testing "All params present"
    (let [param1 :value1
          param2 "value2"
          expected {:status :ok}
          result (when-params [param1 param2]
                   expected)]
      (is (= expected result))))

  (testing "Missing params"
    (let [param1 'value1
          param2 nil
          {:keys [status body] :as response} (when-params [param1 param2]
                                                          {:status :ok})]
    (is (= 422 status))
    (is (= (body :message) "You must supply the parameters param1, param2")))))

(deftest bad-request-response-test
  (is (s/validate (merge RingSwirrlErrorResponse
                         {:status (s/eq 422)
                          :body {:type (s/eq :error)
                                 :error (s/eq :invalid-parameters)
                                 :message (s/eq "foo")}})
                  (bad-request-response "foo"))))


(deftest error-response-test
  ;; can just call function because of validate-schemas
  (is (error-response 413))

  (testing "Takes :error value from ex-swirrl Exceptions"
    (is (s/validate (-> RingSwirrlErrorResponse
                        (assoc :status (s/eq 412))
                        (assoc-in [:body :message] (s/eq "My message"))
                        (assoc-in [:body :error] (s/eq :my-error)))
                    (error-response 412 (ex-swirrl :my-error "My message")))))

  (testing "Takes message from Throwables"
    (is (s/validate (-> RingSwirrlErrorResponse
                        (assoc :status (s/eq 412))
                        (assoc-in [:body :message] (s/eq "My message")))
                    (error-response 412 (RuntimeException. "My message")))))

  (testing "Sets parameters in the right places"
    (is (s/validate (-> RingSwirrlErrorResponse
                        (assoc :status (s/eq 422))
                        (assoc-in [:body :message] (s/eq "foobar"))
                        (assoc-in [:body :error] (s/eq :foobar)))

                    (error-response 422 :foobar "foobar")))))
