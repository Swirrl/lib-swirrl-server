(defproject swirrl/lib-swirrl-server "0.6.3-SNAPSHOT"
  :description "Common library for code used in Swirrl servers"
  :url "https://github.com/Swirrl/lib-swirrl-server/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [ring "1.4.0" :exclusions [org.clojure/java.classpath]]
                 [org.clojure/tools.logging "0.3.1"]
                 [clj-logging-config "1.9.12"]
                 [compojure "1.4.0"]
                 [liberator "0.14.1"]
                 [prismatic/schema "1.0.4"]]

  :profiles {:dev {:dependencies [[ring-mock "0.1.5"]
                                  [grafter "0.7.4"]
                                  [prismatic/schema "1.0.4"]
                                  [ring/ring-devel "1.3.2"]]}})
