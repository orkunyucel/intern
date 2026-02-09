FROM eclipse-temurin:17-jre

WORKDIR /app

COPY scenarios/03-protobuf-selective-parse/target/protobuf-selective-app.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
