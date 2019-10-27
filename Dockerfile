FROM maven:3-jdk-13 AS builder
WORKDIR /usr/src/micron
COPY checkstyle.xml .
COPY pom.xml .
COPY src src
RUN mvn package

FROM openjdk:13-alpine
COPY --from=builder /usr/src/micron/target/micron-jar-with-dependencies.jar /micron.jar
ENTRYPOINT ["java", "-jar", "/micron.jar"]
