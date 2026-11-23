FROM gradle:8.10.2-jdk17 AS build
WORKDIR /workspace
COPY settings.gradle.kts gradle.properties build.gradle.kts ./
COPY src ./src
RUN gradle buildFatJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache tzdata
ENV TZ=America/Sao_Paulo
WORKDIR /app
COPY --from=build /workspace/build/libs/*-all.jar /app/tecal-almoxarifado-bff.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=America/Sao_Paulo", "-jar", "/app/tecal-almoxarifado-bff.jar"]

