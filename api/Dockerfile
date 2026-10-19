# Build: compila o jar com Maven (os testes rodam fora daqui, pois precisam de Docker/Testcontainers)
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package && cp target/reciclathe-api-*.jar app.jar

# Execução: só o JRE, com usuário sem privilégios
FROM eclipse-temurin:21-jre
RUN groupadd --system reciclathe && useradd --system --gid reciclathe --home-dir /app reciclathe \
    && mkdir -p /app /dados/fotos && chown -R reciclathe:reciclathe /app /dados
WORKDIR /app
COPY --from=build --chown=reciclathe:reciclathe /build/app.jar app.jar
USER reciclathe
ENV FOTOS_DIR=/dados/fotos \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
VOLUME /dados/fotos
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
