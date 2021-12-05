FROM maven:3.8-openjdk-17 AS builder
WORKDIR /usr/src/micron
COPY pom.xml .
COPY src src
RUN mvn package

FROM openjdk:17-alpine
RUN addgroup micron
RUN adduser -G micron -S micron
WORKDIR /srv
COPY --from=builder --chown=micron:micron /usr/src/micron/target/micron-jar-with-dependencies.jar /srv/micron.jar
RUN chmod 770 /srv
RUN chown micron:micron -R /srv
USER micron
# https://logging.apache.org/log4j/2.x/manual/async.html
ENTRYPOINT ["java", "-Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector", "-jar", "/srv/micron.jar"]
