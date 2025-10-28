# Stage 1: Build the JAR
FROM maven:3.9.2-eclipse-temurin-17 AS build

WORKDIR /app

# Copy Maven config first (for caching dependencies)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the source code
COPY src ./src

# Build the Spring Boot JAR
RUN mvn clean package -DskipTests


# Use official Playwright Java image
FROM mcr.microsoft.com/playwright/java:v1.48.0-jammy

WORKDIR /app

# Copy your built Spring Boot JAR
COPY target/privacyanalyzer-0.0.1-SNAPSHOT.jar app.jar

# Expose Spring Boot port
EXPOSE 8080

# Run the app
ENTRYPOINT ["java", "-jar", "app.jar"]

