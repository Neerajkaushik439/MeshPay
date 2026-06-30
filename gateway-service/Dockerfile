FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY gateway-service/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
