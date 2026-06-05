# Stage 1 — build
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app

# Download dependencies first (separate layer — cached on pom.xml changes only)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build jar
COPY src ./src
RUN mvn clean package -DskipTests -q

# Stage 2 — runtime (slim JRE)
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
