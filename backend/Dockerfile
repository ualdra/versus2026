# ─────────────────────────────────────────────
#  STAGE: development
# ─────────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS development

WORKDIR /app

# Instalar Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Exponer puerto de la app y puerto de debug remoto
EXPOSE 8080 5005

CMD ["mvn", "spring-boot:run", \
     "-Dspring-boot.run.jvmArguments=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"]


# ─────────────────────────────────────────────
#  STAGE: builder  (solo para producción)
# ─────────────────────────────────────────────
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /app

RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Cachear dependencias antes de copiar todo el código
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B


# ─────────────────────────────────────────────
#  STAGE: production
# ─────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine AS production

WORKDIR /app

RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
