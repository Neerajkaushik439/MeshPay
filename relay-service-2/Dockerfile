FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY relay-service-2/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
