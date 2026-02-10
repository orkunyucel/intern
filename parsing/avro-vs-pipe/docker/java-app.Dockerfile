FROM eclipse-temurin:17-jre

WORKDIR /app
COPY app/target/avro-vs-pipe-app.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
