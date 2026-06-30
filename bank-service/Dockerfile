FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY bank-service/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
