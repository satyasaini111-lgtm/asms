# Multi-stage build — layered Spring Boot image
FROM eclipse-temurin:21-jre AS deps
WORKDIR /app
COPY target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN groupadd --gid 10001 asmsgroup && \
    useradd --uid 10001 --gid asmsgroup --shell /bin/false asmsuser

COPY --from=deps /app/dependencies/ ./
COPY --from=deps /app/spring-boot-loader/ ./
COPY --from=deps /app/snapshot-dependencies/ ./
COPY --from=deps /app/application/ ./

USER asmsuser
EXPOSE 8081

HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
  CMD curl -sf http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=200", \
  "-Xss128k", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "org.springframework.boot.loader.launch.JarLauncher"]
