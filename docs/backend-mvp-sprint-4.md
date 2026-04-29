# Backend MVP — Sprint 4

> Briefing de implementación del backend de Versus para el Sprint 4.
> Cubre: **historial y ranking, moderación de preguntas, panel de administración, y endpoints de gestión de scraping**. Es el último sprint del MVP backend.

---

## Tarea

Eres el implementador del backend de **Versus**. Sprints 1, 2 y 3 están entregados: Auth, Users, Questions, Game singleplayer, Stats básicos y Match multijugador funcionan. Falta cerrar el MVP con todo lo no-jugable: historial detallado, ranking global, reportes de preguntas, panel de admin, y endpoints REST para que un futuro orquestador de scrapers pueda consultarlos.

## Contexto que DEBES leer antes de empezar

Lee estos archivos del repo en este orden:

1. `CLAUDE.md` (raíz) — visión general
2. `docs/guia-de-coordinación-técnica.md` — **contrato de API canónico**, secciones "Módulo 6 — STATS & RANKING", "Módulo 7 — SCRAPING", "Módulo 8 — ADMIN & MODERACIÓN"
3. `docs/backend-mvp-sprint-1-2.md` y `docs/backend-mvp-sprint-3.md` — patrones y estructura ya establecida
4. `docs/bd-scheme.md` — schema final tras Sprints 1-3
5. Código existente: `backend/src/main/java/com/versus/api/stats/` (servicios básicos a extender), `admin/domain/` (entidades de reports y spiders ya creadas en Sprint 1)

## Stack y restricciones

- Mismas que sprints anteriores (Java 25, Spring Boot 4.0.5, Postgres 18)
- **Roles:** `PLAYER`, `MODERATOR`, `ADMIN`. Usar `@PreAuthorize("hasRole('MODERATOR')")` o `hasAnyRole('MODERATOR','ADMIN')` según corresponda. `ADMIN` hereda permisos de `MODERATOR`.
- Errores en formato estándar
- Paginación: usar `Pageable` de Spring Data, exponer `?page=0&size=20&sort=field,desc`. Respuesta como `Page<T>` serializada estándar (content + pageable + totalElements).

## Cambios al esquema DB

1. **`question_reports`**: añadir `resolved_by UUID` (FK a `users`, nullable), `resolved_at TIMESTAMP` (nullable), `resolution_note VARCHAR(500)` (nullable).
2. **Nueva tabla `audit_log`** (opcional pero recomendada para acciones de admin):
   - `id UUID PK, actor_id UUID FK, action VARCHAR(64), target_type VARCHAR(64), target_id UUID, details JSONB, created_at TIMESTAMP`
   - Se inserta cada vez que un admin/mod hace cambios destructivos (cambio de rol, eliminar usuario, desactivar pregunta, resolver reporte).
3. **`spiders`**: añadir `enabled BOOLEAN DEFAULT TRUE`, `cron_expression VARCHAR(64)` (nullable, para futura programación automática — ahora solo lectura).
4. Índice: `question_reports(status, created_at)` para listado de pendientes.
5. Cuando termines, **actualiza `docs/bd-scheme.md`**.

## Estructura de paquetes (extender la existente)

```
com.versus.api/
├── stats/
│   ├── controller/
│   │   ├── StatsController.java       (extender — añadir /history)
│   │   └── RankingController.java     (NUEVO)
│   ├── service/
│   │   ├── StatsService.java          (extender — añadir buildHistory)
│   │   └── RankingService.java        (NUEVO — recálculo de posiciones)
│   └── dto/                           (HistoryItemResponse, RankingEntryResponse)
├── admin/
│   ├── controller/
│   │   ├── ReportController.java      (PLAYER reporta, MOD lista/resuelve)
│   │   ├── AdminUserController.java   (ADMIN gestiona usuarios)
│   │   └── AdminQuestionController.java (ADMIN/MOD gestiona preguntas)
│   ├── service/
│   │   ├── ReportService.java
│   │   ├── AdminUserService.java
│   │   ├── AdminQuestionService.java
│   │   └── AuditLogService.java
│   ├── dto/                           (ReportRequest, ReportResolveRequest, RoleChangeRequest, etc.)
│   └── domain/                        (entidades ya existen — extender QuestionReport con campos nuevos; añadir AuditLog)
└── scraping/
    ├── controller/
    │   └── SpiderController.java      (ADMIN lista/lanza spiders)
    ├── service/
    │   └── SpiderService.java         (en MVP solo registra runs manualmente — la ejecución real es Sprint futuro Scrapy)
    ├── dto/                           (SpiderResponse, SpiderRunResponse)
    └── domain/                        (entidades ya existen)
```

## Endpoints

### Stats e historial

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| `GET` | `/api/stats/me/history?page=0&size=20&mode=SURVIVAL` | PLAYER | Historial paginado de partidas del usuario. `mode` opcional. Incluye singleplayer + multiplayer. Cada item: `{ matchId, mode, finishedAt, result, score, opponentUsername (si pvp), durationSeconds, roundsPlayed }`. |
| `GET` | `/api/ranking/{mode}?page=0&size=100` | PLAYER | Top de un modo. Cada entry: `{ position, userId, username, avatarUrl, score, gamesPlayed }`. |
| `GET` | `/api/ranking/{mode}/me` | PLAYER | `{ position, score, total }` — posición del usuario autenticado. Si no tiene registro, devolver `{ position: null, score: 0, total: N }`. |

**Cálculo del ranking:** la tabla `rankings` ya almacena `score` y `position` por usuario y modo. La `position` se recalcula en un job:

- `RankingService.recomputePositions(mode)` — `ROW_NUMBER() OVER (ORDER BY score DESC)` para todo el modo y `UPDATE` masivo.
- Llamar a este método **al finalizar cada partida** (sync, en transacción) sobre el modo correspondiente. Si en pruebas se vuelve lento, mover a `@Async` o `@Scheduled` cada 1 min.

**Construcción del historial:** consulta sobre `match_players` filtrando por `user_id`, JOIN con `matches`, LEFT JOIN con el otro `match_player` para sacar oponente. Ordenar por `matches.finished_at DESC`.

### Reportes (PLAYER reporta, MODERATOR resuelve)

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| `POST` | `/api/questions/{id}/report` | PLAYER | Body: `{ "reason": "Pregunta ambigua, ambas respuestas valen" }`. Crea `QuestionReport` con `status=PENDING`. 409 si el mismo usuario ya tiene un reporte pendiente sobre la misma pregunta. |
| `GET` | `/api/mod/reports?status=PENDING&page=0&size=20` | MODERATOR+ | Lista paginada. `status` opcional. Incluye snapshot de la pregunta para que el mod no tenga que hacer otra request. |
| `PUT` | `/api/mod/reports/{id}` | MODERATOR+ | Body: `{ "resolution": "DISMISS" \| "DEACTIVATE", "note": "..." }`. Marca el reporte como `DISMISSED`/`RESOLVED`, registra `resolved_by`, `resolved_at`. Si `DEACTIVATE`, además pone la pregunta en `status=INACTIVE`. Inserta entrada en `audit_log`. |

### Admin

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| `GET` | `/api/admin/users?page=0&size=20&search=raul` | ADMIN | Listado paginado, búsqueda opcional por username/email (LIKE case-insensitive). Devuelve perfil completo + `isActive`. |
| `PUT` | `/api/admin/users/{id}/role` | ADMIN | Body: `{ "role": "MODERATOR" }`. 403 si intenta cambiar su propio rol. Audit log. |
| `DELETE` | `/api/admin/users/{id}` | ADMIN | **Soft delete:** `is_active=false`, no borra histórico. 403 si intenta borrarse a sí mismo. Audit log. |
| `PUT` | `/api/admin/questions/{id}/status` | ADMIN/MOD | Body: `{ "status": "ACTIVE" \| "INACTIVE" }`. Transiciones permitidas: `PENDING_REVIEW → ACTIVE/INACTIVE`, `ACTIVE → INACTIVE`, `INACTIVE → ACTIVE`. Audit log. |

### Scraping (ADMIN)

> En este sprint solo implementamos los endpoints REST de gestión. La ejecución real de los spiders Python se conectará después; aquí, "lanzar" un spider crea un `SpiderRun` con `status=RUNNING` y devuelve. El proceso real lo cerrará el orquestador Scrapy llamando a un endpoint interno.

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| `GET` | `/api/admin/spiders` | ADMIN | Lista de spiders con `lastRunAt`, `status`, `enabled`. |
| `POST` | `/api/admin/spiders/{id}/run` | ADMIN | Crea un `SpiderRun(status=RUNNING, started_at=now)`. Marca `Spider.status=RUNNING`. Devuelve el `SpiderRun`. **TODO: integrar con orquestador Scrapy real (issue #45).** |
| `GET` | `/api/admin/spiders/{id}/runs?page=0&size=20` | ADMIN | Historial paginado de runs. |
| `POST` | `/api/internal/spiders/runs/{runId}/finish` | (interno, sin JWT — proteger por IP allowlist o shared secret header `X-Internal-Token`) | Body: `{ "questionsInserted": N, "errors": M }`. Marca run como `FINISHED`. **Marca con TODO de seguridad si no decides el mecanismo aún.** |

## Lo que tienes que implementar (orden)

1. **Migración schema** + actualizar entidades (`QuestionReport`, `Spider`, nueva `AuditLog`).
2. **AuditLogService** — método único `record(actorId, action, targetType, targetId, details)`. Lo van a usar todos los demás servicios.
3. **History endpoint:** consulta + paginación + DTO. Test con seed de partidas terminadas.
4. **Ranking endpoints + recompute:** método de recálculo, llamarlo al finalizar partida (modificar el cierre de partida que ya escribiste en Sprints 2 y 3 para invocar `RankingService.recomputePositions(mode)`). Tests.
5. **Reportes (PLAYER):** `POST /questions/{id}/report` con regla de no-duplicado.
6. **Reportes (MOD):** listar, resolver, integrar con `audit_log`. Tests con usuario MOD.
7. **Admin users:** listar, cambiar rol, soft-delete. Tests con usuario ADMIN.
8. **Admin questions:** activar/desactivar.
9. **Spiders:** listar, crear run, listar runs, endpoint interno de finish (con TODO de seguridad).
10. **Tests de autorización:** un PLAYER no puede acceder a `/mod/*` ni `/admin/*`. Un MOD no puede acceder a `/admin/users` ni `/admin/spiders`.

## Detalles importantes

- **Soft delete de usuarios:** asegúrate de que `JwtAuthFilter` rechaza tokens de usuarios con `is_active=false` (mensaje claro `USER_DISABLED`).
- **Proteger contra escalada:** un ADMIN no puede degradarse a sí mismo ni eliminarse — devolver `403 SELF_MODIFICATION_FORBIDDEN`.
- **Reportes anónimos:** no exponer `reportedBy` en el listado de reportes a moderadores que no sean ADMIN — proteger al reportante. Decidir y documentar.
- **Audit log siempre:** cualquier acción en `/admin/*` o `/mod/reports/*` debe quedar registrada.
- **Performance del ranking:** si el recálculo síncrono al final de cada partida se vuelve lento (>200ms en perfil dev con datos seed), mover a `@Async` y dejar TODO.

## Lo que NO debes hacer

- No implementar la ejecución real de Scrapy → solo el endpoint REST que la dispararía.
- No implementar notificaciones push, emails, ni avisos al usuario reportado.
- No implementar 2FA ni gestión de sesiones por dispositivo.
- No tocar el frontend.

## Entrega

1. Resumen de lo implementado y de los tests añadidos.
2. Lista de TODOs marcados (especialmente integración Scrapy y endpoint interno).
3. Instrucciones para probar manualmente:
   - Login como PLAYER, MOD, ADMIN (los seed users de Sprint 1)
   - Curl/Postman para los endpoints clave
4. `docs/bd-scheme.md` actualizado.
5. Una nota en este mismo documento sobre qué falta para considerar el backend "completo más allá del MVP" (rejoin de partidas, refresh tokens en Redis, etc.).

## Cuando dudes

Pregunta. Especialmente sobre:

- Mecanismo de auth para el endpoint interno de Scrapy
- Anonimización de reportantes
- Si conviene mover el recompute de ranking a un job programado en lugar de síncrono
