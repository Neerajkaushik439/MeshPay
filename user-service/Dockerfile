# Stage 1: Build JAR using Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -pl common-library,user-service -am -DskipTests

# Stage 2: Minimal Runtime Image
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/user-service/target/*.jar app.jar
ENV PORT=8081
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
