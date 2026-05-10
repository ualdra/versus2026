# Backend — Documentación técnica

> **Filosofía:** esta carpeta sigue un modelo *docs-as-code*. Cada módulo tiene su propio fichero Markdown que crece junto al código. Cuando cambias una clase, actualiza el diagrama. Cuando abres una PR, la documentación va en el mismo diff.

## Índice de módulos

| Módulo | Fichero | Estado |
|--------|---------|--------|
| Arquitectura general | [architecture.md](architecture.md) | ✅ Sprint 1-2 |
| Infraestructura común | [common.md](common.md) | ✅ Sprint 1-2 |
| Autenticación & JWT | [modules/auth.md](modules/auth.md) | ✅ Sprint 1-2 |
| Usuarios | [modules/users.md](modules/users.md) | ✅ Sprint 1-2 |
| Preguntas | [modules/questions.md](modules/questions.md) | ✅ Sprint 1-2 |
| Juego singleplayer | [modules/game.md](modules/game.md) | ✅ Sprint 1-2 |
| WebSocket multijugador | [modules/websocket.md](modules/websocket.md) | ✅ Sprint 3 (PR #89) |
| Partidas & matchmaking | [modules/match.md](modules/match.md) | 🚧 Sprint 3 |
| Estadísticas & ranking | [modules/stats.md](modules/stats.md) | ✅ Sprint 1-2 |
| Logros | [modules/achievements.md](modules/achievements.md) | ✅ Sprint 3 |
| Moderación | [modules/moderation.md](modules/moderation.md) | 🚧 Sprint 4 |
| Scraping | [modules/scraping.md](modules/scraping.md) | 🚧 Sprint 4 |
| Multimedia y almacenamiento | [modules/media.md](modules/media.md) | ✅ Dependencia #121 |

Leyenda: ✅ implementado · 🚧 entidades definidas, lógica pendiente · ❌ sin implementar

---

## Stack técnico

| Componente | Versión |
|---|---|
| Java | 25 |
| Spring Boot | 4.0.5 |
| Spring Security | incluido en Boot |
| Spring Data JPA / Hibernate | incluido en Boot |
| Spring WebSocket (STOMP) | incluido en Boot |
| PostgreSQL driver | incluido en Boot |
| JWT (jjwt) | 0.12.6 |
| Lombok | última estable |
| springdoc-openapi | 2.7.0 |
| H2 (test) | incluido en Boot |

## Arranque rápido

```bash
# Variables de entorno mínimas
DB_URL=jdbc:postgresql://localhost:5432/appdb
DB_USER=appuser
DB_PASS=changeme
JWT_SECRET=<cadena-larga>
SPRING_PROFILES_ACTIVE=dev   # activa el seeder de datos

# Con Maven Wrapper
./mvnw spring-boot:run

# Swagger UI (solo en dev)
http://localhost:8080/swagger-ui.html
```

## Convenciones de código

- Cada módulo tiene su propio paquete: `com.versus.api.<módulo>`, con sub-paquetes `domain`, `dto`, `repo`.
- Las excepciones de negocio se lanzan siempre como `ApiException` con el `ErrorCode` correspondiente.
- Los controladores no contienen lógica: delegan completamente en el Service.
- Los DTOs usan anotaciones Jakarta Validation (`@NotBlank`, `@Email`, etc.) y Lombok `@Data` / `@Builder`.
- Las transacciones de escritura llevan `@Transactional`; las de sólo lectura, `@Transactional(readOnly = true)`.
