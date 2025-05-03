# 1) BUILD STAGE
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app

# Copy everything (including gradlew + wrappers)
COPY . .

# Make sure gradlew is executable
RUN chmod +x ./gradlew

# Build the fat jar
RUN ./gradlew bootJar --no-daemon

# 2) RUNTIME STAGE
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app

# Copy the jar from the builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose the application port
EXPOSE 8080

# Run the jar
ENTRYPOINT ["java","-jar","app.jar"]