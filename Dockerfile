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

RUN apt-get update && \
    apt-get install -y chromium chromium-driver && \
    ln -sf /usr/bin/chromium-browser /usr/bin/chromium && \
    rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/privacyanalyzer-0.0.1-SNAPSHOT.jar app.jar

ENV CHROMIUM_PATH=/usr/bin/chromium-browser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
