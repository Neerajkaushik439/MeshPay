FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY transaction-service/target/*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
