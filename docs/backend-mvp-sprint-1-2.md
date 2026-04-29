# Backend MVP — Sprints 1 y 2

> Briefing de implementación del backend de Versus para los Sprints 1 y 2.
> Cubre: Fase 0 (preparación), Fase 1 (entidades), Fase 2 (módulos), Fase 3 (contratos), Fase 4 Sprint 1 (Auth + Users) y Fase 4 Sprint 2 (Questions + Game singleplayer + Stats básicos).

---

## Tarea

Eres el implementador del backend de **Versus**, un juego de preguntas multijugador. El frontend Angular ya existe y consume contratos definidos. El backend está vacío salvo `ApiApplication.java`. Tu trabajo es dejar funcional **autenticación + perfil + preguntas + dos modos singleplayer (Survival, Precision) + stats básicos**.

## Contexto que DEBES leer antes de empezar

Lee estos archivos del repo en este orden:

1. `CLAUDE.md` (raíz) — visión general
2. `docs/bd-scheme.md` — esquema DB original (lo vas a modificar, ver más abajo)
3. `docs/guia-de-coordinación-técnica.md` — **contrato de API canónico, fuente de verdad**
4. `backend/pom.xml` y `backend/src/main/resources/application.properties` — punto de partida
5. `docker-compose.yml` + `docker-compose.dev.yml` + `.env.example` — config Postgres y backend en Docker

No leas el frontend salvo si necesitas verificar un contrato.

## Stack y restricciones

- Java 25, Spring Boot 4.0.5, PostgreSQL 18, Maven
- Lombok ya disponible
- Base de datos: `appdb` / user `appuser` / password `changeme` (de `.env.example`)
- Si Spring Boot 4 + Java 25 da problemas de compatibilidad con alguna librería (JJWT, etc.), **detente y pregunta** antes de bajar versiones.
- Errores siempre con el formato `{ "error": "CODE", "message": "...", "status": N }` (sección "Códigos de error estándar" de la guía).
- DTOs separados de entidades. NUNCA exponer entidades JPA directamente en respuestas.
- Validación con `jakarta.validation` (`@NotBlank`, `@Email`, `@Min`, etc.) en todos los request DTOs.

## Cambios al esquema DB (respecto a `docs/bd-scheme.md`)

Aplica estos cambios al modelar las entidades:

1. **`match_players`**: clave compuesta con `@EmbeddedId(matchId UUID, userId UUID)`.
2. **`questions`**:
   - Añadir `unit VARCHAR(32)` (nullable, solo NUMERIC).
   - Renombrar `correct_answer` → `correct_value NUMERIC` (nullable, solo NUMERIC).
   - Añadir `tolerance_percent NUMERIC DEFAULT 5` (solo NUMERIC).
   - Para BINARY la respuesta correcta sigue marcándose en `question_options.is_correct`.
3. **Nueva tabla `refresh_tokens`**: `id UUID PK, user_id UUID FK, token_hash VARCHAR(255), expires_at TIMESTAMP, revoked BOOLEAN DEFAULT FALSE, created_at TIMESTAMP`.
4. **`match_answers`**: añadir `is_correct BOOLEAN`.
5. **`users`**: añadir `updated_at TIMESTAMP`, `is_active BOOLEAN DEFAULT TRUE`.
6. **Índices**:
   - UNIQUE en `users.email` y `users.username`
   - Compuesto `questions(status, type, category)`
   - `rankings(mode, score DESC)`
   - `matchmaking_queue(mode, entered_at)`
7. Cuando termines, **actualiza `docs/bd-scheme.md`** con el schema final (mismo formato Mermaid).

## Estructura de paquetes (obligatoria)

```
com.versus.api/
├── ApiApplication.java
├── config/         (SecurityConfig, CorsConfig, OpenApiConfig)
├── common/
│   ├── exception/  (ApiException, ErrorCode enum, GlobalExceptionHandler)
│   └── dto/        (ErrorResponse)
├── auth/           (controller, service, dto, JwtService, JwtAuthFilter, RefreshToken entity+repo)
├── users/          (controller, service, dto, domain/User, repo/UserRepository)
├── questions/      (controller, service, dto, domain/{Question,QuestionOption}, repo)
├── game/           (controller, service, dto — survival y precision)
└── stats/          (controller, service, dto, domain/{Ranking,PlayerStats}, repo)
```

Las entidades de `match/` (Match, MatchPlayer, MatchRound, MatchAnswer) se crean ahora porque las usa Stats e historial, pero **no implementes endpoints REST de match** ni WebSocket — eso es Sprint 3.

## Configuración (Fase 0)

Modifica `backend/pom.xml` para añadir:

- `spring-boot-starter-websocket` (lo dejas listo aunque no lo uses todavía)
- `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson` (versión compatible con Boot 4)
- `springdoc-openapi-starter-webmvc-ui`
- `com.h2database:h2` con `<scope>test</scope>`

Crea estos archivos de propiedades:

- `application.properties` — configuración base, lee de variables de entorno con defaults sensatos. Profile activo dependiente de `SPRING_PROFILES_ACTIVE`.
- `application-dev.properties` — `ddl-auto=create-drop`, logging SQL on, JWT secret hardcodeado de dev.
- `application-prod.properties` — `ddl-auto=validate`, sin logging SQL.

Variables a soportar: `DB_URL`, `DB_USER`, `DB_PASS`, `JWT_SECRET`, `JWT_EXPIRY` (segundos), `JWT_REFRESH_EXPIRY` (segundos), con los defaults que aparecen al final de la guía.

## Lo que tienes que implementar (orden)

### 1. Entidades JPA + repos (Fase 1)

Una entidad por tabla del schema (con los cambios indicados arriba). Todas con UUID generado, enums como `EnumType.STRING`, Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor @Entity`. Crea repos `JpaRepository<Entity, UUID>` para cada una.

Enums a crear (en el paquete del módulo correspondiente):

- `Role { PLAYER, MODERATOR, ADMIN }`
- `QuestionType { BINARY, NUMERIC }`
- `QuestionStatus { PENDING_REVIEW, ACTIVE, INACTIVE }`
- `GameMode { SURVIVAL, PRECISION, BINARY_DUEL, PRECISION_DUEL, SABOTAGE }`
- `MatchStatus { WAITING, IN_PROGRESS, FINISHED }`
- `MatchResult { WIN, LOSS, DRAW, ABANDONED }`
- `ReportStatus { PENDING, DISMISSED, RESOLVED }`
- `SpiderStatus { IDLE, RUNNING, FAILED }`

**Verificación:** levantar el backend con `docker compose -f docker-compose.yml -f docker-compose.dev.yml up db backend` debe crear todas las tablas sin errores.

### 2. Seed de datos (issue #44)

`CommandLineRunner` o `@PostConstruct` solo activo en perfil `dev`. Inserta:

- 3 usuarios: 1 PLAYER (`player@versus.com` / `player123`), 1 MODERATOR, 1 ADMIN. Passwords con BCrypt.
- ~15 preguntas BINARY con sus 2 options cada una, en categorías `football`, `geography`, `cinema`.
- ~10 preguntas NUMERIC con `correct_value`, `unit`, `tolerance_percent`.
- Todas con `status=ACTIVE`.

### 3. Common: errores

- `ErrorCode` enum con valores de la guía (UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT, VALIDATION_ERROR, INTERNAL_ERROR).
- `ApiException extends RuntimeException` con `ErrorCode` y mensaje.
- `GlobalExceptionHandler` con `@RestControllerAdvice` que captura `ApiException`, `MethodArgumentNotValidException`, `AccessDeniedException`, `Exception`, y devuelve siempre `ErrorResponse`.

### 4. Auth + Security + Users (Sprint 1 — issues #39, #84, #85)

- `JwtService`: generar accessToken (15 min), refreshToken persistido como hash en `refresh_tokens` (7 días). Métodos: `generateAccessToken(User)`, `generateRefreshToken(User)`, `validate(token)`, `extractUserId(token)`.
- `JwtAuthFilter extends OncePerRequestFilter`: lee `Authorization: Bearer ...`, valida, mete `UsernamePasswordAuthenticationToken` con authorities derivadas del rol (`ROLE_PLAYER`, etc.) en el `SecurityContext`.
- `SecurityConfig`: stateless, CORS habilitado para `http://localhost:4200`, BCrypt como `PasswordEncoder`. Rutas:
  - Públicas: `POST /api/auth/**`, `GET /api/questions/categories`, Swagger UI
  - Autenticadas: todo lo demás
- Endpoints auth (request/response **exactamente** como la guía):
  - `POST /api/auth/register` → crea User PLAYER, devuelve `AuthResponse`. 409 si email/username existe.
  - `POST /api/auth/login` → valida, emite tokens.
  - `POST /api/auth/refresh` → valida refresh token contra la tabla, rota (revoca el viejo, emite nuevo par).
  - `POST /api/auth/logout` → marca `revoked=true`.
- Endpoints users:
  - `GET /api/users/me` → perfil propio.
  - `PUT /api/users/me` → actualiza username/avatar (no email/password en MVP).
  - `GET /api/users/{id}` → perfil público (sin email).

**Tests requeridos** (mockMvc + h2): registro, login, refresh, acceso a `/users/me` con y sin token.

### 5. Questions (Sprint 2 — issues #41, #42, #43)

- `GET /api/questions/random?type=BINARY&category=football` — query params opcionales. Si no hay match, 404. Implementación: `findRandomActive(type, category)` con `ORDER BY random() LIMIT 1` (nativa) o `Pageable` aleatorio.
- `GET /api/questions/{id}` — devuelve la pregunta. **Nunca incluir `correct_value` ni `is_correct` en la respuesta.**
- `GET /api/questions/categories` — `SELECT DISTINCT category FROM questions WHERE status=ACTIVE`.

DTOs: usa una respuesta polimórfica con `type` discriminator, o dos DTOs separados (`QuestionBinaryResponse`, `QuestionNumericResponse`) y un `@JsonTypeInfo`.

### 6. Game Singleplayer (Sprint 2 — issues #53, #55, #56, #59, #60)

**Modelo de sesión:** una partida singleplayer = un `Match` con `mode=SURVIVAL` o `PRECISION`, status `IN_PROGRESS`, un único `MatchPlayer` con `lives_remaining` y `score`. Cada respuesta crea un `MatchRound` + `MatchAnswer`. Al terminar, `status=FINISHED`, `finished_at=now()`, `MatchPlayer.result=WIN/LOSS`.

#### Survival

- Vidas iniciales: 3
- `POST /api/game/survival/start` → crea Match, devuelve `{ sessionId, question }` (BINARY aleatoria).
- `POST /api/game/survival/answer` → valida `optionId` contra `question_options.is_correct`. Acierto: `lifeDelta=0`, suma streak × 50 al score, devuelve `nextQuestion`. Fallo: `lifeDelta=-1`. Si `livesRemaining==0` → `gameOver:true`, no `nextQuestion`, finaliza Match y actualiza `PlayerStats` (gamesPlayed++, currentStreak=0, bestStreak si procede).

#### Precision

- Vidas iniciales: 100
- `POST /api/game/precision/start` → crea Match, devuelve pregunta NUMERIC.
- `POST /api/game/precision/answer` → calcula:

  ```
  dev = |value - correctValue| / correctValue * 100
  if dev <= tolerance:        lifeDelta = +5
  elif dev <= 2*tolerance:    lifeDelta = 0
  else:                       lifeDelta = -min(50, round(dev))
  ```

  Marca `// TODO(#59): confirmar fórmula con el equipo`. Devuelve `correctValue`, `deviation`, `deviationPercent` (mismo valor en MVP), `lifeDelta`, `livesRemaining`, `nextQuestion` o `gameOver`.

**Importante**: `sessionId` en cada request debe corresponder al `Match.id` y validar que está `IN_PROGRESS` y pertenece al usuario autenticado.

### 7. Stats básicos (Sprint 2 — issue #77)

Al terminar cada partida singleplayer, upsert sobre `player_stats(user_id, mode)`:

- `gamesPlayed++`
- `gamesWon++` si Survival superó N rondas (define umbral, ej. 5) o el `MatchPlayer.result == WIN`
- `bestStreak = max(bestStreak, currentStreak)`
- `currentStreak` se acumula en partida y se resetea en fallo
- `avgDeviation` (solo PRECISION): media móvil de las desviaciones de la partida

Endpoint:

- `GET /api/stats/me` → todas las modalidades del usuario
- `GET /api/stats/me?mode=SURVIVAL` → una modalidad. Si no hay registro, devolver objeto con ceros.

`winRate = round(gamesWon/gamesPlayed * 100, 1)` calculado en el DTO.

## Lo que NO debes hacer

- No implementar WebSocket / match multijugador / matchmaking / sala privada → Sprint 3.
- No implementar ranking, history, scraping ni admin → fases siguientes.
- No tocar el frontend.
- No añadir librerías que no estén listadas arriba sin preguntar.
- No usar `ddl-auto=update` en prod (ya está en el plan, pero por si acaso).

## Entrega

Un solo PR (o commits agrupados por fase si prefieres). Al final:

1. Resumen de lo implementado y de los tests añadidos.
2. Lista de TODOs marcados en el código (especialmente la fórmula de Precisión).
3. Instrucciones para arrancar y probar manualmente:
   - `cp .env.example .env`
   - `docker compose -f docker-compose.yml -f docker-compose.dev.yml up`
   - Curl/Postman para los endpoints clave
4. `docs/bd-scheme.md` actualizado con el schema final.

## Cuando dudes

Pregunta. Especialmente sobre:

- Compatibilidad de versiones de librerías
- Cualquier divergencia con el contrato de la guía
- Decisiones de modelado que no estén cubiertas arriba
