# Plan de pruebas de integración del backend

> Documento de implementación paso a paso. El objetivo es que cualquier persona o LLM pueda implementar la suite completa siguiendo este orden, sin tener que tomar decisiones de diseño adicionales.

## 0. Objetivo y alcance

La estrategia general de QA está en [`estrategia.md`](estrategia.md) y la lista de casos en [`plan-de-pruebas.md`](plan-de-pruebas.md). Este documento es **el cómo**: define la infraestructura, la organización de los tests, las herramientas y el orden de implementación de las pruebas de integración del **backend Spring Boot** (no cubre frontend ni E2E con Playwright).

Una prueba de integración aquí significa:

- Arrancar el `AppModule` real (o un slice equivalente) contra una **PostgreSQL 18 real** (no H2).
- Atravesar la cadena completa: filtro JWT → controller → service → repository → SQL.
- Validar contrato HTTP (códigos, forma del JSON de error), invariantes de BD y reglas de negocio observables desde la frontera.

Lo que queda fuera:

- Tests unitarios puros (`@ExtendWith(MockitoExtension.class)`) — ya existen para cada `*Service`.
- E2E con navegador (Playwright) — planificados pero fuera del alcance.
- Pruebas de carga / rendimiento.

---

## 1. Por qué este enfoque (y no otro)

### 1.1 PostgreSQL real, no H2

El proyecto ya tiene `src/test/resources/application.properties` apuntando a H2 con `MODE=PostgreSQL`. Eso vale para tests unitarios y `@DataJpaTest` triviales, pero **no para integración**:

| Caso | H2 | PostgreSQL real |
|---|---|---|
| `random()` en `QuestionRepository.findRandomActive` | No idéntico | ✅ |
| `BigDecimal` con `precision=20, scale=4` | Comportamiento divergente | ✅ |
| Indexes parciales / `unique` compuestos | Tolera diferencias | ✅ |
| Enums Postgres con `@Enumerated(STRING)` | Mapea a `VARCHAR` (no enum nativo) | ✅ |
| Tipos `TIMESTAMPTZ` | Promociona a `TIMESTAMP` | ✅ |

Se levantan `postgres:18.1-alpine` en `docker-compose.test.yml` y corren los integration tests contra ese contenedor.

### 1.2 docker compose externo + Failsafe, no Testcontainers in-process

Hay dos opciones razonables para arrancar Postgres en tests Spring Boot:

| Opción | Pros | Contras |
|---|---|---|
| **Testcontainers** (`@Container` en cada test) | Auto-gestiona el ciclo; cero estado entre runs | +1 dependencia pesada; cada `./mvnw verify` paga el arranque de Postgres (3-8 s) |
| **`docker compose -f docker-compose.test.yml up -d`** | Mismo contenedor entre runs locales → tests más rápidos; reutilizable por scripts ad-hoc; idéntico al pattern del monorepo hermano | Hay que recordar `down -v` al cambiar el esquema; requiere docker en CI |

**Elegimos compose externo** porque:

1. El proyecto **ya depende de docker compose** (`docker-compose.yml`, `.dev.yml`, `.prod.yml`). Añadir un compose más es coherente.
2. En desarrollo iterativo el segundo `./mvnw verify` solo paga ~200 ms para conectar al Postgres ya caliente. Con Testcontainers paga el reinicio del contenedor en cada run.
3. CI (GitHub Actions) puede declarar Postgres como `services:` y reutilizar el mismo `application-test.properties`.

> Si más adelante alguien prefiere Testcontainers, la migración es local: solo cambia `DataSource` y el `@DynamicPropertySource`. Los tests no se reescriben.

### 1.3 Maven Failsafe, no Surefire

Surefire corre `*Test.java`. Failsafe corre `*IT.java` (integration tests) en una fase distinta (`integration-test`/`verify`). Esto nos da:

- `./mvnw test` → solo unitarios (rápido, sin docker).
- `./mvnw verify` → unitarios + integración (lo que corre la CI).
- `./mvnw failsafe:integration-test -DskipUnitTests` → solo integración (debug local).

### 1.4 Limpieza entre tests: TRUNCATE, no `@Transactional` rollback

Spring Boot tradicionalmente recomienda `@Transactional` en tests para rollback automático. **No funciona aquí**:

- El servidor HTTP corre en otro hilo que el test (MockMvc lo evita, pero los WebSocket tests no).
- `GameService`, `MatchService`, `AchievementService` usan `@Transactional` propagado — un test que envuelva todo en una transacción ve commits anidados que distorsionan el comportamiento real.
- `MatchService` mantiene estado **en memoria** (`liveMatches`) que no se "deshace" con rollback.

Helper `TestDatabaseCleaner` que ejecuta `TRUNCATE TABLE … RESTART IDENTITY CASCADE` en `@BeforeEach`. 10-50× más rápido que recrear el esquema y deja la BD en un estado conocido sin tocar transacciones de negocio.

---

## 2. Tecnologías y dependencias

Añadir al `backend/pom.xml` (sección `<dependencies>`):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <version>5.5.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <version>4.2.2</version>
    <scope>test</scope>
</dependency>
```

> `spring-boot-starter-test` ya está incluido y trae JUnit 5, AssertJ, Mockito, MockMvc y Jackson. No añadir Testcontainers.

Añadir Failsafe en `<build><plugins>`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <version>3.5.2</version>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <includes>
            <include>**/*IT.java</include>
            <include>**/it/**/*Test.java</include>
        </includes>
    </configuration>
</plugin>
```

### Por qué RestAssured y no MockMvc

`MockMvc` es síncrono y no arranca servidor real: bien para validar contratos HTTP estáticos, mal para WebSocket (no escucha en un puerto) y para flujos que usan `@AuthenticationPrincipal` con un `JwtAuthFilter` que parsea cabeceras reales.

Usaremos `@SpringBootTest(webEnvironment = RANDOM_PORT)` + RestAssured. Para los tests de capa 6 ("contrato HTTP") un `MockMvc` slice también es válido — se usará puntualmente.

### Por qué Awaitility

`MatchService.beginCountdown` programa el `MATCH_START` con un `ScheduledExecutorService` propio. Los tests de WebSocket necesitan esperar eventos asíncronos sin `Thread.sleep`. Awaitility hace exactamente eso (`await().atMost(2, SECONDS).untilAsserted(...)`).

---

## 3. Infraestructura: ficheros nuevos

### 3.1 `docker-compose.test.yml` (en la raíz del repo)

Override que se **combina** con `docker-compose.yml` — mismo stack `versus`,
misma `app-network`, mismo volumen `maven_cache` (compartido con el entorno
de desarrollo). Los servicios están bajo el perfil `test` para no
contaminar el `up` normal.

```yaml
services:

  db-test:
    image: postgres:18-alpine
    profiles: ["test"]
    environment:
      POSTGRES_DB: versus_test
      POSTGRES_USER: versus_test
      POSTGRES_PASSWORD: versus_test_pass
    ports:
      - "5433:5432"        # 5433 host → 5432 contenedor (evita choque con dev)
    networks:
      - app-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U versus_test -d versus_test"]
      interval: 3s
      timeout: 2s
      retries: 20

  backend-test:
    profiles: ["test"]
    build:
      context: ./backend
      dockerfile: Dockerfile
      target: development
    depends_on:
      db-test:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db-test:5432/versus_test
      SPRING_DATASOURCE_USERNAME: versus_test
      SPRING_DATASOURCE_PASSWORD: versus_test_pass
    volumes:
      - ./backend:/app
      - maven_cache:/root/.m2
    working_dir: /app
    networks:
      - app-network
    command: >
      ./mvnw verify -Dsurefire.skip=true --batch-mode -Dstyle.color=never

volumes:
  maven_cache:
```

Dos modos de uso:

```bash
# 1) Suite completa dentro de contenedores (idéntico a CI)
docker compose -f docker-compose.test.yml \
  --profile test up --build --abort-on-container-exit \
  --exit-code-from backend-test backend-test

# 2) Solo Postgres en contenedor, tests desde IDE/host (rápido para iterar)
docker compose -f docker-compose.test.yml \
  --profile test up -d db-test
cd backend && ./mvnw verify -Dsurefire.skip=true
```

`application-it.properties` deja `localhost:5433` como default (modo 2). En el
modo 1, las env vars `SPRING_DATASOURCE_*` del compose sobrescriben para
apuntar a `db-test:5432`.

Notas:
- Sin volumen persistente — cada `down -v` limpia.
- Imagen `postgres:18-alpine` igual que el `docker-compose.yml` de producción/dev.

### 3.2 `backend/src/test/resources/application-it.properties`

Perfil dedicado a integration tests. Spring Boot lo cargará cuando los tests anoten `@ActiveProfiles("it")`.

```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/versus_test
spring.datasource.username=versus_test
spring.datasource.password=versus_test_pass
spring.datasource.driver-class-name=org.postgresql.Driver

spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.show-sql=false
spring.jpa.open-in-view=false

# Sin seed dev — los datos los crean los factories de cada test.
versus.seed.enabled=false

# JWT con claves predecibles para poder firmar tokens en helpers de test.
versus.jwt.secret=integration-test-secret-integration-test-secret-aaaaaaaaaaaaaaaaaaaaaaaaaaaa
versus.jwt.access-expiry=900
versus.jwt.refresh-expiry=604800

# Matchmaking más rápido para no esperar 1 s entre tests.
versus.match.start-countdown-seconds=1

# Storage local en directorio temporal — los tests de media no deben tocar R2.
versus.storage.provider=local
versus.storage.local-root=target/test-storage
```

> **No tocar** `src/test/resources/application.properties` (H2). Sigue siendo el perfil por defecto para los `*Test.java` unitarios existentes que ya pasan.

### 3.3 `backend/src/test/java/com/versus/api/it/support/PostgresLifecycle.java`

Verifica que el contenedor está vivo antes de empezar y aborta con un mensaje útil si no.

```java
package com.versus.api.it.support;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Falla rápido con un mensaje claro si el contenedor de Postgres
 * de integración no está corriendo. Evita el típico stacktrace
 * confuso de "Connection refused" que sale del pool de Hikari.
 */
public class PostgresLifecycle implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        ApplicationContext ctx = SpringExtension.getApplicationContext(context);
        DataSource ds = ctx.getBean(DataSource.class);
        try (Connection c = ds.getConnection()) {
            if (!c.isValid(2)) throw new IllegalStateException("DataSource inválido");
        } catch (Exception e) {
            throw new IllegalStateException("""

                ════════════════════════════════════════════════════════════
                 No se puede conectar a Postgres de tests en localhost:5433.
                 Arranca el contenedor desde la raíz del repo:

                   docker compose -f docker-compose.test.yml \\
                     --profile test up -d db-test

                ════════════════════════════════════════════════════════════
                """, e);
        }
    }
}
```

### 3.4 `backend/src/test/java/com/versus/api/it/support/IntegrationTest.java`

Anotación compuesta que cada test de integración usa.

```java
package com.versus.api.it.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@ExtendWith(PostgresLifecycle.class)
public @interface IntegrationTest {
}
```

### 3.5 `backend/src/test/java/com/versus/api/it/support/TestDatabaseCleaner.java`

Trunca todas las tablas entre tests (orden de hojas a raíz). Inyectable como `@Component` solo en el perfil `it`.

```java
package com.versus.api.it.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Profile("it")
public class TestDatabaseCleaner {

    /**
     * Orden hojas → raíces. TRUNCATE CASCADE en una sola llamada resetea
     * todas las secuencias y borra todas las filas en una transacción.
     */
    private static final List<String> TABLES = List.of(
            "user_achievements",
            "achievements",
            "match_answers",
            "match_rounds",
            "match_players",
            "matchmaking_queue",
            "matches",
            "question_reports",
            "question_options",
            "questions",
            "spider_runs",
            "spiders",
            "rankings",
            "player_stats",
            "media_assets",
            "refresh_tokens",
            "users"
    );

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public void truncateAll() {
        String sql = "TRUNCATE TABLE " + String.join(", ", TABLES) + " RESTART IDENTITY CASCADE";
        em.createNativeQuery(sql).executeUpdate();
    }
}
```

> **Importante**: si más adelante se añaden tablas nuevas, hay que añadirlas a `TABLES`. Si una tabla no existe todavía (módulo en construcción), no falla — `TRUNCATE` ignora errores cuando se usa con `IF EXISTS`; si quieres ser estricto, valida la lista contra `information_schema.tables` en el constructor.

### 3.6 `backend/src/test/java/com/versus/api/it/support/HttpTestClient.java`

Provee `req()` (RestAssured), `tokenFor(user)` y `expiredToken(user)`.

```java
package com.versus.api.it.support;

import com.versus.api.auth.JwtService;
import com.versus.api.users.Role;
import com.versus.api.users.domain.User;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.Date;
import javax.crypto.SecretKey;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;

@Component
@org.springframework.context.annotation.Profile("it")
public class HttpTestClient {

    @LocalServerPort int port;
    private final JwtService jwtService;
    private final SecretKey key;

    public HttpTestClient(JwtService jwtService,
                          @Value("${versus.jwt.secret}") String secret) {
        this.jwtService = jwtService;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Specification base con baseUri/port ya configurados. */
    public RequestSpecification req() {
        return RestAssured.given()
                .baseUri("http://localhost")
                .port(port)
                .contentType("application/json");
    }

    /** Genera un access token firmado para el usuario indicado. */
    public String tokenFor(User user) {
        return jwtService.generateAccessToken(user);
    }

    /** Spec ya con el header Authorization listo. */
    public RequestSpecification reqAs(User user) {
        return req().header("Authorization", "Bearer " + tokenFor(user));
    }

    /** Devuelve un access token con exp en el pasado (para tests de 401). */
    public String expiredAccessToken(User user) {
        Instant past = Instant.now().minusSeconds(60);
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("type", "access")
                .issuedAt(Date.from(past.minusSeconds(60)))
                .expiration(Date.from(past))
                .signWith(key)
                .compact();
    }
}
```

### 3.7 `backend/src/test/java/com/versus/api/it/support/Factories.java`

Crea usuarios/preguntas/partidas en BD con valores por defecto razonables y permite overrides.

```java
package com.versus.api.it.support;

import com.versus.api.questions.QuestionStatus;
import com.versus.api.questions.QuestionType;
import com.versus.api.questions.domain.Question;
import com.versus.api.questions.domain.QuestionOption;
import com.versus.api.questions.repo.QuestionRepository;
import com.versus.api.users.Role;
import com.versus.api.users.UserStatus;
import com.versus.api.users.domain.User;
import com.versus.api.users.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Profile("it")
@RequiredArgsConstructor
public class Factories {

    public static final String DEFAULT_PASSWORD = "Test1234!";

    private final UserRepository users;
    private final QuestionRepository questions;
    private final PasswordEncoder encoder;
    private final AtomicInteger seq = new AtomicInteger();

    public User user() { return user(b -> {}); }
    public User admin() { return user(b -> b.role(Role.ADMIN)); }
    public User moderator() { return user(b -> b.role(Role.MODERATOR)); }

    /** Crea y persiste un usuario; el lambda permite ajustar el builder. */
    public User user(java.util.function.Consumer<User.UserBuilder> custom) {
        int n = seq.incrementAndGet();
        User.UserBuilder b = User.builder()
                .username("user" + n + "_" + UUID.randomUUID().toString().substring(0, 6))
                .email("user" + n + "@versus.test")
                .passwordHash(encoder.encode(DEFAULT_PASSWORD))
                .role(Role.PLAYER)
                .status(UserStatus.ACTIVE)
                .isActive(true);
        custom.accept(b);
        return users.save(b.build());
    }

    /** Pregunta BINARY con 2 opciones (la primera correcta). */
    public Question binaryQuestion() {
        int n = seq.incrementAndGet();
        Question q = Question.builder()
                .text("Binary question #" + n)
                .type(QuestionType.BINARY)
                .category("general")
                .status(QuestionStatus.ACTIVE)
                .textHash("hash-bin-" + n)
                .build();
        q.setOptions(List.of(
                QuestionOption.builder().question(q).text("Yes").isCorrect(true).build(),
                QuestionOption.builder().question(q).text("No").isCorrect(false).build()
        ));
        return questions.save(q);
    }

    /** Pregunta NUMERIC con valor 100 y tolerancia 5%. */
    public Question numericQuestion() {
        int n = seq.incrementAndGet();
        return questions.save(Question.builder()
                .text("Numeric question #" + n)
                .type(QuestionType.NUMERIC)
                .category("general")
                .status(QuestionStatus.ACTIVE)
                .correctValue(new BigDecimal("100"))
                .unit("kg")
                .tolerancePercent(new BigDecimal("5"))
                .textHash("hash-num-" + n)
                .build());
    }
}
```

> El nombre exacto de constructores de `QuestionOption` puede requerir ajustar setters: revisar `QuestionOption.java` antes de copiar/pegar y adaptar los `.text()` / `.isCorrect()` a la API real de la entidad.

### 3.8 `backend/src/test/java/com/versus/api/it/support/AbstractIT.java`

Clase base que todos los tests de integración heredan.

```java
package com.versus.api.it.support;

import io.restassured.RestAssured;
import io.restassured.config.ObjectMapperConfig;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
public abstract class AbstractIT {

    @Autowired protected TestDatabaseCleaner cleaner;
    @Autowired protected HttpTestClient http;
    @Autowired protected Factories factories;

    @BeforeEach
    final void resetState() {
        cleaner.truncateAll();
        RestAssured.config = RestAssured.config()
                .objectMapperConfig(new ObjectMapperConfig().defaultObjectMapperType(
                        io.restassured.mapper.ObjectMapperType.JACKSON_2));
    }
}
```

---

## 4. Organización de los tests: 7 capas

Cada capa es un fichero (o varios) bajo `backend/src/test/java/com/versus/api/it/`:

```
src/test/java/com/versus/api/it/
├── support/                          # (sección 3)
├── capa0/SchemaConstraintsIT.java
├── capa1/AuthFlowIT.java
├── capa2/UserProfileIT.java
├── capa3/QuestionsAndModerationIT.java
├── capa4/SurvivalGameIT.java
├── capa4/PrecisionGameIT.java
├── capa5/MatchmakingAndLobbyIT.java
├── capa5/WebSocketLobbyIT.java
├── capa6/HttpContractIT.java
└── capa7/EndToEndJourneyIT.java
```

Cada capa cubre un nivel de abstracción. **No mezclar**: si un test necesita HTTP no es de capa 0; si necesita WebSocket no es de capa 4. La separación facilita el debug — si capa 0 falla, no tiene sentido investigar capa 4.

### 4.1 Capa 0 — Invariantes de esquema (`SchemaConstraintsIT`)

Valida que las restricciones JPA se traducen a constraints reales en Postgres. Estos tests no llaman a controllers; usan los repositorios directamente.

Casos mínimos (mapeados al esquema actual del proyecto):

| # | Constraint | Cómo se verifica |
|---|---|---|
| C1 | `users.email` único | Persistir 2 usuarios con el mismo email → `DataIntegrityViolationException` |
| C2 | `users.username` único | Mismo patrón |
| C3 | `questions.text_hash` único | Persistir 2 preguntas con el mismo `textHash` |
| C4 | `matchmaking_queue.user_id` único | Un user no puede tener 2 entradas |
| C5 | `match_players` PK compuesta `(match_id, user_id)` | No se permite duplicado del mismo par |
| C6 | `refresh_tokens` borra en cascada al borrar `user` | Crear user + token, borrar user, verificar token desaparece |
| C7 | `question_options` se borran en cascada al borrar `question` | Mismo patrón |

### 4.2 Capa 1 — Identidad (`AuthFlowIT`)

Cubre todo el flujo de `/api/auth/**`. **Es la primera capa que arranca el server HTTP completo** (no `@DataJpaTest`).

Casos (numerados según `plan-de-pruebas.md`):

- A1 Registro 201 + tokens + user en respuesta
- A2 Registro con email duplicado → 409 `CONFLICT`
- A3/A4 Validación del body inválido → 400 `VALIDATION_ERROR`
- A6 Login válido → 200 + tokens
- A7 Login con contraseña incorrecta → 401 `UNAUTHORIZED`
- A8 Login con email inexistente → 401 (mismo mensaje que A7 — no leak de qué falló)
- Extra: login con `isActive=false` → 401 `Account disabled`
- Extra: login con `status=DELETED` → 401
- A10 Refresh con token válido → 200, **el token viejo queda revocado** (segundo intento → 401)
- A11 Refresh con token expirado → 401
- A13 Logout invalida el refresh token (uso posterior → 401)
- Extra: petición a `/api/users/me` sin Authorization → 401 con shape `{error, message, status}`
- Extra: petición con `Authorization: Bearer` (sin token) → 401
- Extra: petición con `Authorization: Bearer xxx.invalid.xxx` → 401
- Extra: petición con access token expirado (`http.expiredAccessToken(user)`) → 401
- Extra: petición a `/api/admin/spiders` con rol `PLAYER` → 403 `FORBIDDEN`

> Patrón concreto del test de refresh con rotación:
>
> ```java
> var tokens = login(user);                     // RestAssured POST /api/auth/login
> var nuevos = refresh(tokens.refreshToken());  // POST /api/auth/refresh
> // El viejo ya no debe servir
> http.req().body(Map.of("refreshToken", tokens.refreshToken()))
>     .post("/api/auth/refresh")
>     .then().statusCode(401);
> ```

### 4.3 Capa 2 — Usuarios (`UserProfileIT`)

- U1 `GET /api/users/me` devuelve el perfil del autenticado (sin campos sensibles como `passwordHash`).
- U2 `PUT /api/users/me` actualiza `username` y persiste.
- U3 `PUT /api/users/me` con un username ya tomado por otro user → 409 `CONFLICT`.
- U4 `GET /api/users/{id}` devuelve `UserPublicResponse` **sin email** ni `passwordHash`.
- U5 `GET /api/users/{id}` con UUID inexistente → 404 `NOT_FOUND`.
- Extra: `PUT /api/users/me/password` con `currentPassword` incorrecta → 401.
- Extra: `DELETE /api/users/me` deja al user en estado `DELETED` y `isActive=false`; siguiente login → 401.
- Extra: `PUT /api/users/me/avatar` JSON acepta una URL válida y la guarda.
- Extra: `PUT /api/users/me/avatar` multipart con un PNG válido sube el fichero, persiste el `avatarUrl` y el endpoint público `/media-files/{file}` lo sirve.

### 4.4 Capa 3 — Preguntas y moderación

#### `QuestionsAndModerationIT`

Preguntas (Q*):

- Q1 `GET /api/questions/random` (auth) devuelve una pregunta en estado `ACTIVE`.
- Q2 Una pregunta `BINARY` devuelve `options[]` **sin** `isCorrect`.
- Q3 Una pregunta `NUMERIC` devuelve `unit` **sin** `correctValue` ni `tolerancePercent`.
- Q4 `?type=BINARY` filtra correctamente; `?type=NUMERIC` también.
- Q5 Las preguntas con `status` `INACTIVE`, `PENDING_REVIEW` o `FLAGGED` **no** se devuelven.
- Q6 `GET /api/questions/categories` (público, según `SecurityConfig`) devuelve las categorías distintas de preguntas `ACTIVE`.

Moderación (M*):

- M1 `POST /api/questions/{id}/report` (player) crea reporte `PENDING`.
- Extra: reportar 2 veces la misma pregunta como el mismo user → segundo intento 409.
- M2 5 reportes distintos sobre la misma pregunta → status pasa a `FLAGGED` automáticamente.
- M3 `GET /api/moderation/reports` con rol `PLAYER` → 403; con `MODERATOR` o `ADMIN` → 200.
- M4 `PUT /api/moderation/reports/{id}/resolve` con `action=DISMISS` → reporte `DISMISSED`, pregunta sigue `ACTIVE`.
- M5 `…/resolve` con `action=DELETE_QUESTION` → reporte `RESOLVED`, pregunta `INACTIVE`.
- M6 `…/resolve` con rol PLAYER → 403.

### 4.5 Capa 4 — Juego singleplayer

#### `SurvivalGameIT`

Setup común: `factories.user()`, varias `factories.binaryQuestion()` (≥ 6), login y guardar token.

- G1 `POST /api/game/survival/start` → 200, `livesRemaining=3`, `score=0`, devuelve `matchId` y la primera pregunta sin `isCorrect`.
- G2 Respuesta correcta (`selectedOptionId` = la opción correcta de la pregunta actual) → `correct=true`, `lifeDelta=0`, `streak=1`, `score=50`, devuelve `nextQuestion`.
- G3 Respuesta incorrecta → `correct=false`, `lifeDelta=-1`, `livesRemaining=2`, `streak=0`.
- G4 Tres aciertos consecutivos → `score` final = `50+100+150 = 300`.
- G5 Tres fallos → `gameOver=true`, no `nextQuestion`. El `match` en BD pasa a `FINISHED`. `match_answers` tiene 3 filas. `player_stats` para `SURVIVAL` se ha actualizado (`gamesPlayed++`).
- G6 Answer con `sessionId` inexistente → 404 `NOT_FOUND`.
- Extra: answer con `sessionId` que pertenece a otro user → 403 `FORBIDDEN`.
- Extra: answer con `sessionId` de un match `PRECISION` desde el endpoint `/survival/answer` → 400 `VALIDATION_ERROR`.
- Extra: answer con `optionId` que no pertenece a la `questionId` → 400.

#### `PrecisionGameIT`

Setup: `factories.numericQuestion()` (varias, correctValue=100, tolerance=5).

- P1 `POST /api/game/precision/start` → `livesRemaining=100`.
- P2 Answer con `value=100` (deviation 0%) → `lifeDelta=+5`, `livesRemaining=105`, `correct=true`, `streak=1`.
- P3 Answer con `value=110` (deviation 10%, dentro del rango "no daño no bonus" hasta `2×tolerance`) → `lifeDelta=0`, `correct=false`, `streak=0`.
- Extra: Answer con `value=200` (deviation 100%) → `lifeDelta=-50` (cap por la fórmula), `livesRemaining` decrementa.
- P4 Ir respondiendo `value=300` repetidamente hasta `livesRemaining=0` → `gameOver=true`. Match `FINISHED`, `player_stats` actualizado, `avgDeviation` no nulo.

> **Nota TODO #59:** la fórmula exacta de Precision puede cambiar. Los valores `+5`, `0`, `-min(50, dev)` están sacados de `GameService.answerPrecision`. Si la fórmula cambia, **los asserts numéricos exactos también**. Mantener cada test focal — un test por umbral de la fórmula, no asserts en cadena.

### 4.6 Capa 5 — Matchmaking, lobby y WebSocket

#### `MatchmakingAndLobbyIT` (solo REST)

- `POST /api/matchmaking/queue` con `{mode: BINARY_DUEL}` → 204; verifica fila en `matchmaking_queue`.
- `POST` segunda vez con el mismo modo → idempotente, sigue habiendo 1 fila.
- `POST` con un modo distinto → reemplaza la fila anterior.
- `POST` con `mode: SURVIVAL` → 400 `VALIDATION_ERROR` ("single-player").
- `DELETE /api/matchmaking/queue` → 204; fila desaparece.
- `POST /api/matches` con `mode: BINARY_DUEL` → 201 con `matchId`, `roomCode` (6 chars), creator añadido al lobby.
- `POST /api/matches` con `mode: SURVIVAL` → 400.
- `POST /api/matches/{id}/join` con un segundo jugador → 200 `LobbyStateDto` con ambos players.
- `POST /…/join` con sala llena → 409.
- `GET /api/matches/{id}/lobby` devuelve snapshot consistente.
- `DELETE /api/matches/{id}/abandon` del último jugador → 204, sala pasa a `FINISHED`, `requireLive` posterior → 404.

#### `WebSocketLobbyIT`

Aquí se valida el flujo en tiempo real. Usar `WebSocketStompClient` de Spring + `SockJsClient` opcional. Esqueleto:

```java
StompSession session = client.connectAsync(
        "ws://localhost:" + port + "/ws",
        new WebSocketHttpHeaders(),
        stompHeaders("Authorization", "Bearer " + http.tokenFor(user)),
        new StompSessionHandlerAdapter() {}
).get(5, TimeUnit.SECONDS);

BlockingQueue<MatchEventEnvelope> events = new LinkedBlockingQueue<>();
session.subscribe("/topic/match/" + matchId, new StompFrameHandler() {
    @Override public Type getPayloadType(StompHeaders h) { return MatchEventEnvelope.class; }
    @Override public void handleFrame(StompHeaders h, Object payload) {
        events.add((MatchEventEnvelope) payload);
    }
});
```

Casos (todos numerados a partir de W*):

- W1 STOMP `CONNECT` con `Authorization: Bearer <token>` válido → conexión establecida.
- W2 STOMP `CONNECT` sin header → conexión rechazada (timeout o `ConnectionLostException`).
- W3 STOMP `CONNECT` con token expirado → rechazada.
- W4 STOMP `CONNECT` con header `Bearer no-es-un-jwt` → rechazada.
- W5 Dos clientes (A y B) conectados, A crea match y se une, B `POST /join`. A recibe `PLAYER_JOINED` con datos de B (subscribir antes del POST).
- W6 A envía `SEND /app/match/ready {matchId}` → ambos clientes reciben `PLAYER_READY {userId: A, ready: true}`.
- W7 Tras `B` también ready, ambos reciben primero `MATCH_STARTING {countdownSeconds: 1}` y, ≤ 2 s después, `MATCH_START {matchId, mode}`. El `match` en BD pasa a `IN_PROGRESS`.
- W8 Antes del `MATCH_START`, A envía `SEND /app/match/abandon` → B recibe `PLAYER_LEFT {userId: A}`. Si solo quedaba A, el `match` en BD pasa a `FINISHED`.
- W9 `MATCH_FOUND` por `/user/queue/match` — meter A y B en cola del mismo modo y esperar a que el scheduler (`@Scheduled(fixedDelay=1000)`) los empareje. Awaitility con timeout 3 s.

> **Truco para W9:** el scheduler corre cada 1 s. En vez de esperar, exponer `MatchmakingService::pollAndMatch` como `@VisibleForTesting` o llamar al bean directamente desde el test (`matchmakingService.pollAndMatch()`) tras hacer los POST. Más determinista.

> **Concurrencia:** `JwtChannelInterceptor` lanza `MessageDeliveryException` si la cabecera falla. STOMP por defecto reintenta; los tests de W2/W3/W4 deben verificar que después de N intentos el `StompSession` está desconectado, **no** esperar un mensaje claro de "401".

### 4.7 Capa 6 — Contrato HTTP (`HttpContractIT`)

Validar la **frontera HTTP**, no la lógica.

- Body mal formado (JSON inválido) en cualquier POST → 400 con shape `{error: "VALIDATION_ERROR", message, status: 400}`.
- Body válido pero campos requeridos faltantes (`@NotNull`/`@NotBlank`) → 400 + mensaje con el nombre del campo.
- Path con UUID mal formado (`/api/users/not-a-uuid`) → 400.
- Cualquier ruta protegida sin `Authorization` → 401 con shape `{error: "UNAUTHORIZED", message, status: 401}`.
- Cualquier ruta protegida con JWT firmado con otra clave → 401.
- Acceso a `/api/admin/spiders` con `PLAYER` → 403.
- `GET /api/users/{uuid-aleatorio}` → 404.
- `PUT /api/users/me` con un campo extra (`{username: "x", evil: true}`): documentar comportamiento real (Spring/Jackson lo ignora por defecto a menos que se active `FAIL_ON_UNKNOWN_PROPERTIES`). Es un test de regresión, no un cambio de comportamiento.

> Estos tests pueden usar `@WebMvcTest` slices para algunos casos puros (validación de DTOs), pero el resto se hace contra el servidor real. Se prefiere el patrón unificado: todos extienden `AbstractIT`.

### 4.8 Capa 7 — Journeys end-to-end (`EndToEndJourneyIT`)

Cada test atraviesa varios módulos para validar que se componen bien.

- **J1 — Vida de un jugador**: register → login → jugar Survival hasta gameOver → consultar `GET /api/stats/me` y verificar `gamesPlayed=1`, `gamesWon` y `bestStreak` coherentes → consultar `GET /api/achievements` y verificar al menos `FIRST_GAME` desbloqueado.
- **J2 — Moderación**: 5 players reportan la misma pregunta → la pregunta queda `FLAGGED` → un MODERATOR la resuelve con `DELETE_QUESTION` → la pregunta ya no aparece en `GET /api/questions/random`.
- **J3 — Multiplayer feliz**: 2 players → cola → match found → lobby → ambos ready → countdown → `MATCH_START` → `match.status=IN_PROGRESS` en BD.
- **J4 — Multiplayer roto**: 2 players → cola → match found → lobby → uno abandona antes del countdown → el otro recibe `PLAYER_LEFT` → match `FINISHED`.
- **J5 — Refresh + ruta protegida**: login → access token expira (`expiredAccessToken`) → `/api/users/me` falla con 401 → cliente hace refresh → reintenta `/api/users/me` con el nuevo access token → 200. Validamos el contrato que el frontend espera.

---

## 5. Orden de implementación (paso a paso)

Cada paso debe acabar **verde** antes de pasar al siguiente. No saltar.

| # | Paso | Fichero(s) clave | Comando que debe pasar |
|---|---|---|---|
| 1 | Crear `docker-compose.test.yml` (mismo proyecto `versus`) y arrancar `db-test` | `docker-compose.test.yml` | `docker compose -f docker-compose.test.yml --profile test up -d db-test` |
| 2 | Añadir `application-it.properties` | sección 3.2 | `psql -h localhost -p 5433 -U versus_test versus_test -c '\dt'` (responde tras paso 6) |
| 3 | Añadir deps al `pom.xml` (RestAssured, Awaitility) y plugin Failsafe | sección 2 | `./mvnw -q dependency:tree | grep -i restassured` |
| 4 | Añadir `IntegrationTest`, `PostgresLifecycle`, `HttpTestClient`, `TestDatabaseCleaner`, `Factories`, `AbstractIT` | sección 3.3–3.8 | Compila: `./mvnw -q compile test-compile` |
| 5 | Smoke test: un `IT` vacío que solo `@Autowired` Spring y verifique `cleaner.truncateAll()` no falla | `it/SmokeIT.java` | `./mvnw -q failsafe:integration-test -Dit.test=SmokeIT` |
| 6 | Implementar **capa 0** completa | `it/capa0/SchemaConstraintsIT.java` | `./mvnw -q verify -Dit.test=SchemaConstraintsIT` |
| 7 | Implementar **capa 1** | `it/capa1/AuthFlowIT.java` | `./mvnw -q verify -Dit.test=AuthFlowIT` |
| 8 | Implementar **capa 2** | `it/capa2/UserProfileIT.java` | `./mvnw -q verify -Dit.test=UserProfileIT` |
| 9 | Implementar **capa 3** | `it/capa3/QuestionsAndModerationIT.java` | `./mvnw -q verify -Dit.test=QuestionsAndModerationIT` |
| 10 | Implementar **capa 4** Survival, después Precision | `it/capa4/*` | `./mvnw -q verify -Dit.test='*GameIT'` |
| 11 | Implementar **capa 5** REST primero | `it/capa5/MatchmakingAndLobbyIT.java` | `./mvnw -q verify -Dit.test=MatchmakingAndLobbyIT` |
| 12 | Implementar **capa 5** WebSocket | `it/capa5/WebSocketLobbyIT.java` | `./mvnw -q verify -Dit.test=WebSocketLobbyIT` |
| 13 | Implementar **capa 6** | `it/capa6/HttpContractIT.java` | idem |
| 14 | Implementar **capa 7** | `it/capa7/EndToEndJourneyIT.java` | idem |
| 15 | Activar Failsafe en CI | `.github/workflows/*.yml` | Verde en CI |
| 16 | Marcar casos ✅ en `plan-de-pruebas.md` | docs/qa | Revisión manual |

### Comandos resumen

```bash
# Suite completa dentro de contenedores (idéntico a CI)
docker compose -f docker-compose.test.yml \
  --profile test up --build --abort-on-container-exit \
  --exit-code-from backend-test backend-test

# Modo iterativo: BD en contenedor, tests desde IDE/host
docker compose -f docker-compose.test.yml \
  --profile test up -d db-test
cd backend
./mvnw test                                # solo unitarios
./mvnw verify -Dsurefire.skip=true         # solo integración
./mvnw verify                              # todo

# Limpiar al cambiar el esquema JPA
docker compose -f docker-compose.test.yml \
  --profile test down -v
```

> Nota: invocar `failsafe:integration-test` directamente **no** compila las
> clases. Usa siempre la fase `verify` (con `-Dsurefire.skip=true` si quieres
> saltarte unitarios). El compose ya lo hace así dentro de `backend-test`.

---

## 6. CI: GitHub Actions

CI **usa el mismo compose** que en local — paridad total. El job de integración
en `.github/workflows/ci.yml` se reduce a:

```yaml
test-integration:
  name: Tests de integración
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - name: Run integration tests (docker compose)
      run: |
        docker compose -f docker-compose.test.yml \
          --profile test up --build --abort-on-container-exit \
          --exit-code-from backend-test backend-test
    - name: Upload Failsafe reports
      if: always()
      uses: actions/upload-artifact@v4
      with:
        name: backend-failsafe-reports
        path: backend/target/failsafe-reports/
```

No hay `services:` ni `setup-java`: todo corre dentro de `backend-test`, que se
construye desde `backend/Dockerfile` (stage `development`) — el mismo
contenedor que ya usa el devcontainer. Los reports aparecen en el host porque
`backend-test` monta `./backend:/app`.

---

## 7. Convenciones y pitfalls

### 7.1 Nomenclatura

- Clase: `<Cosa>IT.java` (capta el Failsafe). El sufijo `IT` distingue de `*Test.java` (unitarios → Surefire).
- Método: en español o inglés, pero descriptivo. Ej. `loginConCredencialesCorrectas_devuelveTokensYUser()`.
- Una sola aserción de negocio por test cuando sea posible; agrupar con `Nested` por feature.

### 7.2 Datos: factories, no fixtures globales

No mantener un script SQL de "fixture" — cada test crea lo que necesita con `factories.user()`, `factories.binaryQuestion()`, etc. Ventaja: borrar/añadir tablas no rompe tests no relacionados; el test es legible sin saltar a otro fichero.

### 7.3 BD limpia antes de cada test, no después

`AbstractIT.resetState()` corre en `@BeforeEach`. Si un test falla deja la BD en un estado inspeccionable (`psql -h localhost -p 5433 …`). Si se hiciera en `@AfterEach`, se perdería información de debug.

### 7.4 Mocks/Fakes

- **No mockear** repositorios JPA en integración (eso es para unitarios).
- **Sí mockear**: `R2StorageService` se evita activando `versus.storage.provider=local` (sin AWS). El proceso `scrapy` del `SpiderService` se evita inyectando un `scraperWorkingDir` que no exista o, mejor, abstraer la creación del `ProcessBuilder` y mockearla. Hasta entonces, los IT de scraping deben **omitirse** o marcar `@Disabled` con motivo explícito.

### 7.5 WebSocket: timeouts y orden de eventos

- Suscribirse **antes** de disparar la acción que provoca el evento. Si no, se pierde.
- Usar `BlockingQueue<MatchEventEnvelope>` con `poll(2, SECONDS)`. Nunca `take()` sin timeout.
- Para verificar que **no** llega un evento: `assertThat(events.poll(500, MILLISECONDS)).isNull()`.

### 7.6 `matchService` con estado en memoria

Hay un `liveMatches` `ConcurrentHashMap` en `MatchService`. **Entre tests de integración, ese mapa no se limpia automáticamente** — el bean es singleton y vive durante todo el `SpringBootTest`. Soluciones:

1. Hacer que `MatchService` exponga un método `@VisibleForTesting void clearLiveMatchesForTest()`.
2. O usar `@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)` en `WebSocketLobbyIT` (más lento porque reinicia Spring entre tests).

Recomendación: **(1)** y llamarlo desde `AbstractIT.resetState()` cuando el bean exista (`@Autowired(required = false) Optional<MatchService>`).

### 7.7 Seguridad de tests: no commits con `JWT_SECRET` real

`application-it.properties` define una `versus.jwt.secret` ficticia. **Nunca** copiar el secret de producción ahí. Está incluido en el repo a propósito (los tokens solo valen contra la BD de test).

### 7.8 Migraciones / esquema

El proyecto usa `ddl-auto=create-drop` también en tests, lo cual es válido mientras no haya Flyway/Liquibase. Cuando se introduzcan migraciones:

- Cambiar `application-it.properties` a `ddl-auto=validate`.
- Aplicar migraciones en el `BeforeAll` (o un `globalSetup` ejecutable).

---

## 8. Cobertura objetivo

Métrica orientativa (no bloqueante en PRs iniciales):

| Módulo | % líneas mínimas tras capa correspondiente |
|---|---|
| `auth` | 80% tras capa 1 |
| `users` | 75% tras capa 2 |
| `questions` + `moderation` | 70% tras capa 3 |
| `game` | 75% tras capa 4 |
| `match` (sin contar `LiveMatchState`) | 70% tras capa 5 |
| `common.exception` | 90% tras capa 6 |
| **Total backend** | **≥ 70%** tras capa 7 |

Reportar con `jacoco-maven-plugin`:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution><goals><goal>prepare-agent</goal></goals></execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
        </execution>
    </executions>
</plugin>
```

---

## 9. Checklist final por capa

Antes de declarar una capa "completa":

- [ ] Todos los tests verdes (`./mvnw -q verify -Dit.test=<Clase>IT`)
- [ ] Cada caso del `plan-de-pruebas.md` correspondiente está marcado ✅ con el nombre del test que lo cubre.
- [ ] Los tests corren sin `Thread.sleep` (usar Awaitility).
- [ ] Ningún test depende del orden de ejecución de otro.
- [ ] El `TestDatabaseCleaner.TABLES` incluye todas las tablas que el módulo escribe.
- [ ] Para módulos nuevos: actualizar `Factories` con los helpers necesarios.

---

