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

# Stage 2: Runtime image
FROM eclipse-temurin:17-jre

WORKDIR /app

# Install Chromium and dependencies for headless Selenium
RUN apt-get update && \
    apt-get install -y chromium chromium-driver \
    libnss3 libgdk-pixbuf2.0-0 libatk-bridge2.0-0 libgbm1 libasound2 \
    && rm -rf /var/lib/apt/lists/*

# Copy the JAR from the build stage
COPY --from=build /app/target/privacyanalyzer-0.0.1-SNAPSHOT.jar app.jar

# Set environment variable for Selenium to find Chromium
ENV CHROMIUM_PATH=/usr/bin/chromium

# Expose default Spring Boot port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
