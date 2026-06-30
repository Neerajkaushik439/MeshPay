FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY relay-service-1/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
