# Etapa 1: compila el proyecto con Maven (esta imagen pesada no queda en el resultado final)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

# --mount=type=cache guarda las dependencias de Maven entre construcciones: solo se descargan la
# primera vez, aunque después cambie el pom.xml o el código
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

# Etapa 2: solo el JRE y el .jar (imagen mucho más liviana)
FROM eclipse-temurin:21-jre
WORKDIR /app

# El servicio no corre como root
RUN useradd --system --uid 1001 rentamovil
COPY --from=build /app/target/rtm-api-gateway-*.jar app.jar
USER rentamovil

EXPOSE 8080
# /health/live solo confirma que el gateway responde (no pregunta a los demás servicios)
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
    CMD curl -fs http://localhost:8080/health/live || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
