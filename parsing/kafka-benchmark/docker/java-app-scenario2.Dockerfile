FROM eclipse-temurin:17-jre

WORKDIR /app

COPY scenarios/02-avro-reader-schema/target/avro-reader-schema-app.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
