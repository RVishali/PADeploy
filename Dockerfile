# ------------------------
# Stage 1: Build
# ------------------------
FROM maven:3.9.5-eclipse-temurin-17 AS build

# Set working directory
WORKDIR /app

# Copy Maven config first for caching
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build Spring Boot JAR (skip tests for speed)
RUN mvn clean package -DskipTests

# ------------------------
# Stage 2: Runtime
# ------------------------
FROM eclipse-temurin:17-jre

# Install Chrome (headless)
RUN apt-get update && \
    apt-get install -y wget unzip gnupg2 && \
    wget -q -O - https://dl.google.com/linux/linux_signing_key.pub | apt-key add - && \
    sh -c 'echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" >> /etc/apt/sources.list.d/google-chrome.list' && \
    apt-get update && \
    apt-get install -y google-chrome-stable && \
    rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy the Spring Boot JAR from build stage
COPY --from=build /app/target/privacyanalyzer-0.0.1-SNAPSHOT.jar app.jar

# Expose default Spring Boot port
EXPOSE 8080

# Set environment variable for headless Chrome
ENV CHROME_BIN=/usr/bin/google-chrome

# Run the Spring Boot app
ENTRYPOINT ["java","-jar","app.jar"]
