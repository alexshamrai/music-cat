# ---- build ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY . .
# Gradle downloads its own Node for the frontend build (Task 13), so no node stage needed
RUN ./gradlew --no-daemon :backend:bootJar -x test

# ---- runtime ----
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/backend/build/libs/*.jar app.jar
COPY catalog.json /app/catalog.json
# Cold-start tuning for Cloud Run: C1-only JIT, small stacks, 75% of the 1 GiB cap
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+TieredCompilation -XX:TieredStopAtLevel=1 -Xss256k"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
