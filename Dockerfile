# =============================================
# Multi-stage build for Garbigo Auth Service
# =============================================

# Stage 1: Build the application
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Copy Maven files first (for better caching)
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw mvnw
RUN chmod +x mvnw

# Download dependencies (cache layer)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime image (smaller and secure)
FROM eclipse-temurin:21-jre

WORKDIR /app

# Create non-root user for security
RUN useradd -r -s /bin/false springuser

# Copy the built JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Change ownership to non-root user
RUN chown springuser:springuser app.jar

# Switch to non-root user
USER springuser

# Expose port (default Spring Boot port)
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]