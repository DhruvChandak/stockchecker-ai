FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN addgroup --system stockpilot && adduser --system --ingroup stockpilot stockpilot
COPY --from=build /workspace/target/stockpilot-ai-backend-0.1.0.jar app.jar
RUN mkdir -p /app/uploads && chown -R stockpilot:stockpilot /app
USER stockpilot
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
