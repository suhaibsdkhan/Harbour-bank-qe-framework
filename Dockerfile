# Runtime image for the Harbour Bank app. Build the jar first:
#   ./mvnw -pl bank-app package -DskipTests
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 bank
WORKDIR /app
COPY bank-app/target/bank-app-*-exec.jar app.jar
USER bank
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --retries=20 CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
