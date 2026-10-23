# syntax=docker/dockerfile:1
# Contexto de build: raiz do repositório (projeto Maven multi-módulo)

FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /build
COPY pom.xml .
COPY debeatur-core debeatur-core
COPY debeatur-indices debeatur-indices
COPY debeatur-api debeatur-api
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q package -pl debeatur-api -am -DskipTests

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S debeatur && adduser -S debeatur -G debeatur
WORKDIR /app
COPY --from=build /build/debeatur-api/target/debeatur-api-*.jar app.jar
USER debeatur
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
