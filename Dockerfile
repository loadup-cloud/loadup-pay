FROM eclipse-temurin:25-jre
WORKDIR /app
ARG JAR_FILE
COPY --chown=10001:10001 ${JAR_FILE} app.jar
RUN chmod 0444 app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
