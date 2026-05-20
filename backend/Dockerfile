# ---------------------------------------------------------------------------
# AgentOps Firewall — backend Dockerfile.
# Multi-stage build: Maven build stage + slim JRE runtime stage.
# ---------------------------------------------------------------------------

# ---- build stage ----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

# ---- runtime stage --------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root for safety.
RUN addgroup -S agentops && adduser -S agentops -G agentops
USER agentops:agentops

COPY --from=build /workspace/target/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
