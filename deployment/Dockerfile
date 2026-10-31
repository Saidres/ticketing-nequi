# Build stage: compiles the app inside Docker so `docker compose up --build` is the only command needed
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace
COPY . .
# A Windows checkout may carry CRLF line endings, which break the wrapper script on Linux
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew :app-service:bootJar --no-daemon -x test

# Runtime stage: JRE only, non-root user
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S appuser -G app
WORKDIR /app
COPY --from=build /workspace/applications/app-service/build/libs/*.jar ticketing.jar
USER appuser
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70"
EXPOSE 8080
ENTRYPOINT ["/bin/sh", "-c", "exec java $JAVA_OPTS -jar /app/ticketing.jar"]
