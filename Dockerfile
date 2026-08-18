# ===== Build =====
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package \
    && cp target/DATN-*.jar /app/app.jar

# ===== Runtime =====
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN mkdir -p /app/uploads \
    && groupadd -r spring \
    && useradd -r -g spring spring \
    && chown -R spring:spring /app

COPY --from=build --chown=spring:spring /app/app.jar /app/app.jar

USER spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
