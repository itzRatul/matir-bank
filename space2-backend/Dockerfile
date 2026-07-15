FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
# HuggingFace Spaces requires port 7860
EXPOSE 7860
ENTRYPOINT ["java", "-Dserver.port=7860", "-jar", "app.jar"]
