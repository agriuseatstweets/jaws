FROM clojure:openjdk-11-lein

RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app

COPY project.clj .
RUN lein deps
COPY . /usr/src/app

RUN mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" app-standalone.jar

EXPOSE 9010
EXPOSE 8080

CMD ["java", \
        "-Dcom.sun.management.jmxremote", \
        "-Dcom.sun.management.jmxremote.port=9010", \
        "-Dcom.sun.management.jmxremote.rmi.port=9010", \
        "-Dcom.sun.management.jmxremote.local.only=false", \
        "-Dcom.sun.management.jmxremote.authenticate=false", \
        "-Dcom.sun.management.jmxremote.ssl=false", \
        "-Djava.rmi.server.hostname=127.0.0.1", \
        "-javaagent:./jmx_prometheus_javaagent-0.11.0.jar=8080:prometheus.yaml", \
        "-jar", "app-standalone.jar"]
