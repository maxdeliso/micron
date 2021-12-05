FROM maven:3.8-openjdk-17 AS builder
WORKDIR /usr/src/micron
COPY pom.xml .
COPY src src
RUN mvn package

FROM openjdk:17-alpine
COPY --from=builder /usr/src/micron/target/micron-jar-with-dependencies.jar /micron.jar
# https://logging.apache.org/log4j/2.x/manual/async.html
ENTRYPOINT ["java", "-Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector", "-jar", "/micron.jar"]
