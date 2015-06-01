(ns swirrl-server.responses-test
  (:require [clojure.test :refer :all]
            [swirrl-server.responses :refer :all]))

(deftest when-params-test
  (testing "All params present"
    (let [p1 :param1
          p2 "param2"
          expected {:status :ok}
          result (when-params [p1 p2]
                   expected)]
      (is (= expected result))))

  (testing "Missing params"
    (let [p1 'param1
        p2 nil
        {:keys [status body] :as response} (when-params [p1 p2]
                                                        {:status :ok})]
    (is (= 400 status))
    (is (contains? body :message)))))
