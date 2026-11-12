# One multi-stage Dockerfile for every service:
#   docker build --build-arg SERVICE=order-service -t orderflow/order-service .
# Build context is the repo root so the shared common-events module is available.

# ---------- build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl ${SERVICE} -am package -DskipTests \
 && cp ${SERVICE}/target/${SERVICE}-*-SNAPSHOT.jar /build/app.jar

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /build/app.jar app.jar
USER app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
