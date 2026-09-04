# Stage 1: Build JAR using Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -pl common-library,relay-service-2 -am -DskipTests

# Stage 2: Minimal Runtime Image
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/relay-service-2/target/*.jar app.jar
ENV PORT=8083
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
