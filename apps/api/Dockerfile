# syntax=docker/dockerfile:1.7
FROM maven:3.9.11-eclipse-temurin-21-alpine AS build

WORKDIR /workspace
COPY apps/api/pom.xml ./pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline

COPY apps/api/src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S talon && adduser -S talon -G talon
WORKDIR /app
COPY --from=build /workspace/target/talon-api-*.jar app.jar
USER talon

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
