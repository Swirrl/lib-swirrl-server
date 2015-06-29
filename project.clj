(defproject swirrl/lib-swirrl-server "0.2.0"
  :description "Common library for code used in Swirrl servers"
  :url "https://github.com/Swirrl/lib-swirrl-server/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [ring "1.3.2"]
                 [compojure "1.3.4"]]

  :profiles {:dev {:dependencies [[ring-mock "0.1.5"]
                                  [ring/ring-devel "1.3.2"]]}})
