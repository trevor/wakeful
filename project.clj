(defproject wakeful "0.2.11"
  :description "restful routing alternative"
  :dependencies [[clojure "1.3.0"]
                 [useful "0.7.5-alpha3"]
                 [clj-json "0.4.3"]
                 [compojure "0.6.5"]
                 [ego "0.1.7"]
                 [hiccup "0.3.6"]
                 [classlojure "0.6.2"]
                 [org.clojure/tools.namespace "0.1.1"
                  :exclusions [org.clojure/java.classpath]]
                 [org.clojure/java.classpath "0.2.0"]]
  :dev-dependencies [[ring "1.0.0-RC4"]])
