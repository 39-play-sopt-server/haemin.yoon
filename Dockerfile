FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace
COPY gradle gradle
COPY gradlew settings.gradle build.gradle ./
COPY src src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system spring && useradd --system --gid spring --home-dir /app spring
WORKDIR /app
COPY --from=builder --chown=spring:spring /workspace/build/libs/*.jar app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
