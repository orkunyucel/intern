FROM eclipse-temurin:17-jre

WORKDIR /app

COPY scenarios/01-oldschool-pipe/target/oldschool-pipe-app.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
