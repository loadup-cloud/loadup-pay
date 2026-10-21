FROM eclipse-temurin:25-jre
WORKDIR /app
ARG APP
COPY ${APP}/${APP}-bootstrap/target/${APP}-bootstrap-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
