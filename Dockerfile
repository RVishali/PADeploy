# =========================
# Stage 1: Build the JAR
# =========================
FROM maven:3.9.2-eclipse-temurin-17 AS build

WORKDIR /app

# Copy only pom.xml first to cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code
COPY src ./src

# Build the Spring Boot JAR
RUN mvn clean package -DskipTests

# =========================
# Stage 2: Runtime
# =========================
# Use Playwright Java image (includes Node.js, browsers, etc.)
FROM mcr.microsoft.com/playwright/java:v1.48.0-jammy

WORKDIR /app

# Copy the JAR from the build stage
COPY --from=build /app/target/privacyanalyzer-0.0.1-SNAPSHOT.jar app.jar

# Expose Spring Boot port
EXPOSE 8080

# Set environment variable if you need custom paths (Playwright uses its own browsers)
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
