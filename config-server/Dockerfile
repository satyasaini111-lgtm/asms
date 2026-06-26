FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY config-server/pom.xml config-server/pom.xml
RUN mvn -N install -q
COPY config-server/src config-server/src
RUN mvn -f config-server/pom.xml package -DskipTests -q

FROM eclipse-temurin:21-jre AS layers
WORKDIR /app
COPY --from=build /build/config-server/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN groupadd --gid 10001 asmsgroup && useradd --uid 10001 --gid asmsgroup --shell /bin/false asmsuser
COPY --from=layers /app/dependencies/ ./
COPY --from=layers /app/spring-boot-loader/ ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/ ./
USER asmsuser
EXPOSE 8888
HEALTHCHECK --interval=15s --timeout=5s --retries=3 CMD curl -sf http://localhost:8888/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:+UseG1GC", "-Djava.security.egd=file:/dev/./urandom", "org.springframework.boot.loader.launch.JarLauncher"]
