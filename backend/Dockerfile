FROM gradle:9.2.1-jdk17 AS build

WORKDIR /workspace
COPY settings.gradle build.gradle ./
COPY backend/build.gradle backend/build.gradle
COPY backend/src backend/src
RUN gradle :backend:bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build /workspace/backend/build/libs/backend-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
