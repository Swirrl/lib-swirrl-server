(ns swirrl-server.responses-test
  (:require [clojure.test :refer :all]
            [swirrl-server.responses :refer :all]))

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
    (is (= 400 status))
    (is (= (body :message) "You must supply the parameters param1, param2")))))
