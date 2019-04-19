FROM maven:3-jdk-12 AS builder
WORKDIR /usr/src/micron
COPY pom.xml .
RUN mvn dependency:go-offline
COPY . .
RUN mvn package

FROM openjdk:12-oracle
COPY --from=builder /usr/src/micron/target/micron-jar-with-dependencies.jar /micron.jar
CMD ["/usr/bin/java", "-jar", "/micron.jar"]