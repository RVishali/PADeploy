# Start from official OpenJDK 17 image
FROM openjdk:17-jdk-slim

# Set environment variables
ENV DEBIAN_FRONTEND=noninteractive \
    CHROMIUM_BIN=/usr/bin/chromium-browser \
    CHROMEDRIVER_BIN=/usr/bin/chromedriver \
    JAVA_OPTS=""

# Install Chromium and dependencies
RUN apt-get update && \
    apt-get install -y chromium chromium-driver wget curl unzip gnupg && \
    rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy Maven built jar (replace 'privacy-analyzer.jar' with your jar name)
COPY target/privacy-analyzer.jar app.jar

# Expose port
EXPOSE 8080

# Run the app
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
