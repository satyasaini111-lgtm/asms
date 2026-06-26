FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Inject corporate CA cert so Confluent Maven repo is reachable
COPY corp-ca.pem /usr/local/share/ca-certificates/corp-ca.crt
RUN update-ca-certificates && \
    keytool -import -trustcacerts -alias corp-ca -noprompt -storepass changeit \
      -keystore "$JAVA_HOME/lib/security/cacerts" -file /usr/local/share/ca-certificates/corp-ca.crt 2>/dev/null || true
COPY pom.xml .
COPY user-service/pom.xml user-service/pom.xml
RUN mvn -N install -q
COPY user-service/src user-service/src
RUN mvn -f user-service/pom.xml package -DskipTests -q

FROM eclipse-temurin:21-jre AS layers
WORKDIR /app
COPY --from=build /build/user-service/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN groupadd --gid 10001 asmsgroup && \
    useradd --uid 10001 --gid asmsgroup --shell /bin/false asmsuser
COPY --from=layers /app/dependencies/ ./
COPY --from=layers /app/spring-boot-loader/ ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/ ./
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
