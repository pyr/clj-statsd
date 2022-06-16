(defproject clj-statsd "0.4.1-SNAPSHOT"
  :description "statsd protocol client"
  :url         "https://github.com/shmish111/clj-statsd"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]]
  :profiles {:dev {:global-vars    {*warn-on-reflection* true}}}  
  :dependencies [[org.clojure/clojure "1.8.0"]])
