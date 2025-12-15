FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY services/orchestrator-api/stub/pom.xml pom.xml
COPY services/orchestrator-api/stub/src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /src/target/orchestrator-api-0.0.1.jar /app/app.jar
EXPOSE 8082
ENTRYPOINT ["java","-jar","/app/app.jar","--server.port=8082"]
