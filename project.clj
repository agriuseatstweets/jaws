(defproject twitter "0.1.0"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/core.async "0.4.490"]
                 [org.clojure/tools.logging "0.4.1"]
                 [ch.qos.logback/logback-classic "1.2.3"]
                 [com.twitter/hbc-core "2.2.0"]
                 [com.google.cloud/google-cloud-pubsub "1.59.0"]
                 [com.google.guava/guava "27.0.1-jre"]
                 [com.google.auth/google-auth-library-oauth2-http "0.13.0"]
                 [com.google.apis/google-api-services-sheets "v4-rev564-1.25.0"]
                 [cheshire "5.8.1"]
                 [metosin/compojure-api "2.0.0-alpha28"]
                 [metosin/reitit-core "0.2.13"]
                 [metosin/reitit-spec "0.2.13"]
                 [metosin/reitit-schema "0.2.13"]

                 [metosin/reitit-http "0.2.13"]
                 [metosin/reitit-interceptors "0.2.13"]
                 [metosin/reitit-sieppari "0.2.13"]
                 ;; ring helpers
                 [metosin/reitit-ring "0.2.13"]
                 [metosin/reitit-middleware "0.2.13"]
                 [ring-cors/ring-cors "0.1.1"]
                 [ring/ring-jetty-adapter "1.6.3"]
                 [ring/ring-defaults "0.3.2"]
                 [environ "1.1.0"]]
  :main ^:skip-aot jaws.core
  :resource-paths ["src/resources"]
  :plugins [[lein-environ "1.1.0"]]
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[midje "1.6.3"]]
                   :resource-paths ["test/resources"]}})
