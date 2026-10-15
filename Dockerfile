# syntax=docker/dockerfile:1.7
# One build stage compiles the whole multi-module project once; each service is a small
# runtime target:  docker build --target risk-engine -t fraudshield/risk-engine .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY contracts/pom.xml contracts/
COPY services/gateway/pom.xml services/gateway/
COPY services/order-service/pom.xml services/order-service/
COPY services/inventory-service/pom.xml services/inventory-service/
COPY services/dispatch-service/pom.xml services/dispatch-service/
COPY contracts/src contracts/src
COPY services services
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN useradd --system --uid 10001 app
WORKDIR /app
USER 10001
# Size the heap from the container's memory limit, and fail fast instead of limping on after OOM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime AS gateway
COPY --from=build /src/services/gateway/target/gateway-1.0.0.jar /app/app.jar
EXPOSE 8080

FROM runtime AS order-service
COPY --from=build /src/services/order-service/target/order-service-1.0.0.jar /app/app.jar
EXPOSE 8091

FROM runtime AS inventory-service
COPY --from=build /src/services/inventory-service/target/inventory-service-1.0.0.jar /app/app.jar
EXPOSE 8092

FROM runtime AS dispatch-service
COPY --from=build /src/services/dispatch-service/target/dispatch-service-1.0.0.jar /app/app.jar
EXPOSE 8093
