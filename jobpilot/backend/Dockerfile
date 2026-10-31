# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
ARG MAVEN_OPTS=""
RUN --mount=type=secret,id=proxy_ca,required=false --mount=type=secret,id=maven_settings,required=false \
    if [ -f /run/secrets/proxy_ca ]; then \
      cp "$JAVA_HOME/lib/security/cacerts" /tmp/jobpilot-cacerts; \
      keytool -importcert -noprompt -trustcacerts -alias jobpilot-session-ca -file /run/secrets/proxy_ca -keystore /tmp/jobpilot-cacerts -storepass changeit; \
      export MAVEN_OPTS="$MAVEN_OPTS -Djavax.net.ssl.trustStore=/tmp/jobpilot-cacerts"; \
    fi; \
    set --; if [ -f /run/secrets/maven_settings ]; then set -- -s /run/secrets/maven_settings; fi; \
    mvn "$@" -B -DskipTests package && rm -f /tmp/jobpilot-cacerts
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && groupadd --system jobpilot && useradd --system --gid jobpilot jobpilot && mkdir -p /data && chown jobpilot:jobpilot /data
WORKDIR /app
COPY --from=build /app/target/jobpilot-0.1.0.jar app.jar
USER jobpilot
ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]
