# Plan: Sistema de clasificación competitiva (ELO + Leaderboard)

## Context

Versus necesita un sistema de clasificación competitiva donde cada partida multijugador
afecte al ELO de los jugadores, con leaderboard global y por modo, visible desde el perfil
y desde una página pública.

**Restricción central:** hoy NO existe ningún modo multijugador (PvP). Los entities
`Match`/`MatchPlayer` existen, pero solo se rellenan en partidas single-player de Survival y
Precision desde `GameService` (un único jugador por `Match`). No hay código que finalice una
partida PvP con dos jugadores.

Por tanto este plan construye **toda la infraestructura ELO ahora** (algoritmo, persistencia,
endpoints, frontend completo, datos de prueba sembrados) dejando **un único punto de
integración aislado y documentado** (`EloService.applyForFinishedMatch(...)`). Cuando exista
al menos un modo multijugador, activar la feature será una modificación mínima de este plan:
añadir **una sola llamada** a ese método desde el código que finalice la partida PvP, sin
tocar nada más. La sección "Activación futura (PvP)" describe exactamente ese cambio.

El estado actual del backend está más desarrollado de lo que sugiere `CLAUDE.md`: ya existen
`Ranking`, `PlayerStats`, `StatsService`, patrón de paginación (`ModerationController`),
manejo de errores estándar y patrones de servicio/signals en el frontend. Se reutilizan.

### Decisiones tomadas (confirmadas con el usuario)
- **Rank al vuelo por consulta**: no se persiste `Ranking.position`. El rank de una fila =
  `offset + índice`. La posición propia = `count(score mayor) + 1`. Usa el índice existente
  `idx_rankings_mode_score (mode, score DESC)`. Sin jobs, siempre consistente.
- **Página `/rankings` pública + autenticada**: `GET /api/rankings` público;
  `GET /api/rankings/me` requiere token. Si hay sesión, se resalta la fila propia.
- **K = 32 fijo** como constante en `EloService` (estándar 1v1, exactamente lo pedido).
- **Rating inicial: 1000** por modo. Se crea la fila `Ranking` perezosamente la primera vez
  que un jugador termina una partida en ese modo (o se siembra en datos dev).

---

## Backend

Paquete nuevo: `backend/src/main/java/com/versus/api/ranking/`
(separado de `stats/` porque ELO/clasificación es un dominio propio; `stats` sigue siendo
agregados de rendimiento. `Ranking` entity y `RankingRepository` viven hoy en `stats/` — se
**mueven** a `ranking/` o se referencian desde el nuevo paquete; ver paso 1).

### 1. Reubicar/extender el dominio de ranking
- **Mover** `stats/domain/Ranking.java` → `ranking/domain/Ranking.java` y
  `stats/repo/RankingRepository.java` → `ranking/repo/RankingRepository.java`
  (actualizar imports; `RankingRepository` hoy no se usa en ningún sitio salvo su definición,
  así que el movimiento es seguro — verificado por grep).
- Añadir a `Ranking` los campos que pide el enunciado y aún no existen:
  `wins` (Integer), `losses` (Integer), `winStreak` (Integer). Mantener `score`
  (= rating ELO), `userId`, `mode`, `updatedAt`. **Eliminar `position` del scope** (rank al
  vuelo) — o dejarlo nullable sin uso; preferible eliminarlo para no confundir.
- Añadir constante `INITIAL_RATING = 1000`.
- Añadir constraint único `(user_id, mode)` (análogo a `uk_player_stats_user_mode`) para
  garantizar una fila por jugador y modo.

### 2. `EloService` — algoritmo ELO estándar
`ranking/EloService.java`:
- Método puro y testeable: `EloOutcome calculate(int winnerRating, int loserRating)` →
  devuelve `{ winnerDelta, loserDelta }`.
  - Fórmula estándar: `expectedWinner = 1 / (1 + 10^((loserRating - winnerRating)/400))`.
  - `winnerDelta = round(K * (1 - expectedWinner))`,
    `loserDelta  = round(K * (0 - (1 - expectedWinner)))` (negativo).
  - `K = 32` constante privada.
  - `winnerDelta` y `loserDelta` son enteros; suma cero salvo redondeo (aceptable).
- Método de aplicación (punto de integración único):
  `applyForFinishedMatch(UUID winnerUserId, UUID loserUserId, GameMode mode)`
  `@Transactional`:
  1. Carga (o crea con rating 1000) el `Ranking` de ganador y perdedor para `mode`.
  2. `outcome = calculate(winnerRating, loserRating)`.
  3. Aplica deltas a `score`; actualiza `wins`/`losses`; `winStreak` del ganador +1, del
     perdedor → 0.
  4. Persiste ambos.
  5. Devuelve un `EloApplyResult` con los deltas por usuario (para que el llamador, vía
     WebSocket o respuesta REST, pueda incluir el delta en la pantalla de resultado).
- Empate (si algún modo lo permite): método auxiliar
  `applyDraw(userA, userB, mode)` con `expected` como score 0.5 (no requerido por el
  enunciado pero barato y evita un TODO; incluir solo si trivial).

> **Este es el único punto que la activación futura tocará.** Nada más en el backend
> depende de que exista PvP.

### 3. Endpoints REST — `RankingController`
`ranking/RankingController.java`, `@RequestMapping("/api/rankings")`.
Reutiliza el patrón EXACTO de `ModerationController` (Spring Data `Page<T>` + `Pageable` +
`@PageableDefault`).

- `GET /api/rankings?mode=BINARY_DUEL&page=0&size=20` — **público** (sin
  `@SecurityRequirement`, y excluido del filtro JWT / permitido en `SecurityConfig`).
  Top jugadores paginado, ordenado por `score DESC`. Respuesta: `Page<RankingRowResponse>`.
  - `RankingRowResponse`: `{ position, userId, username, avatarUrl, rating, wins, losses,
    winStreak }`. `position = pageable.getOffset() + indiceEnPagina + 1`.
  - El `username`/`avatarUrl` se obtienen con un `JOIN` en una `@Query` JPQL contra `User`
    (proyección a DTO) para evitar N+1 y cumplir <500 ms.
- `GET /api/rankings/me?mode=BINARY_DUEL` — **requiere token**
  (`@AuthenticationPrincipal UUID userId`). Devuelve `{ mode, rating, wins, losses,
  winStreak, position }` del usuario. `position = rankingRepository.countByModeAndScoreGreaterThan(mode, miScore) + 1`.
  Si no tiene fila aún → rating 1000, posición = `count(mode) + 1`, W/L/streak 0.
- `GET /api/users/{id}/ranking` — ranking de un usuario concreto (todos los modos o
  filtrado por `?mode=`). Mismo cálculo de posición. Útil para la vista de perfil de otros.

### 4. Repositorio — consultas optimizadas
`RankingRepository` (Spring Data, sin implementación manual):
- `Page<RankingRowProjection> findRankingPage(GameMode mode, Pageable pageable)` vía
  `@Query` con `JOIN User u ON u.id = r.userId` proyectando a interfaz/record.
- `long countByModeAndScoreGreaterThan(GameMode mode, int score)` (posición propia, O(log n)
  con el índice).
- `Optional<Ranking> findByUserIdAndMode(UUID userId, GameMode mode)`.
- `List<Ranking> findByUserId(UUID userId)` (perfil multi-modo).
- El índice `idx_rankings_mode_score (mode, score DESC)` ya existe → consultas de página y
  count cumplen el objetivo <500 ms.

### 5. Datos de prueba (dev) — para verificar HOY sin PvP
Extender `config/DevSeedConfig.java` (ya existe como sembrador dev,
`versus.seed.enabled=true`):
- Sembrar ~30–50 filas `Ranking` con ratings variados repartidos por los 5 modos y por
  usuarios sembrados, incluyendo al usuario de prueba, para que el leaderboard, la
  paginación, el resaltado de fila propia y el widget del dashboard sean verificables
  end-to-end sin necesidad de partidas PvP.
- Guardarlo tras un flag (`versus.seed.rankings=true`) y solo en perfil `dev`.

### 6. Tests backend
- `EloServiceTest` (JUnit + Mockito, sigue el estilo de `StatsServiceTest`):
  - `calculate`: ratings iguales → ±16; favorito gana → delta pequeño; underdog gana →
    delta grande; simetría `winnerDelta ≈ -loserDelta`.
  - `applyForFinishedMatch`: crea filas a 1000 si no existen; aplica deltas; W/L y winStreak
    correctos (ganador +1, perdedor reset); idempotencia transaccional.
- `RankingControllerTest` (`@WebMvcTest` o test de slice como los existentes):
  paginación, orden por score DESC, cálculo de `position`, `/me` con y sin fila previa,
  `/api/rankings` accesible sin token, `/me` rechaza sin token (401).

---

## Frontend

### 1. Modelos — `core/models/ranking.models.ts` (nuevo)
- `interface RankingRow { position: number; userId: string; username: string;
  avatarUrl: string | null; rating: number; wins: number; losses: number;
  winStreak: number; }`
- `interface MyRanking { mode: GameMode; rating: number; wins: number; losses: number;
  winStreak: number; position: number; }`
- `interface Page<T> { content: T[]; totalElements: number; totalPages: number;
  number: number; size: number; }` — **primer consumo de respuesta paginada en el
  frontend** (no existe ninguno hoy); definir aquí para reutilizar.

### 2. Servicio — `core/services/ranking.service.ts` (nuevo)
Sigue el patrón EXACTO de `stats.service.ts` (`inject(HttpClient)`,
`environment.apiBaseUrl`, `HttpParams`, `Observable`):
- `leaderboard(mode: GameMode, page: number, size: number): Observable<Page<RankingRow>>`
  → `GET /rankings?mode=&page=&size=`.
- `mine(mode: GameMode): Observable<MyRanking>` → `GET /rankings/me?mode=`.
- `forUser(userId: string, mode?: GameMode): Observable<...>` →
  `GET /users/{id}/ranking`.

### 3. Página Leaderboard — `features/rankings/` (nuevo)
- Componente standalone `RankingsPage` (`features/rankings/pages/rankings/rankings.ts`),
  patrón signals como `Dashboard`.
- Ruta nueva en `app.routes.ts`: `{ path: 'rankings', loadComponent: ... }`
  **SIN `authGuard`** (pública). Verificar que `auth.interceptor.ts` ya excluye correctamente
  o no rompe la llamada pública (la llamada a `/rankings` no debe forzar login en 401; como
  el endpoint es público no devolverá 401).
- UI: reutiliza la tabla de `features/admin/pages/users/admin-users.html` (`.vs-card`
  con `padding:0` + `.vs-table`), `.vs-avatar`, `.vs-pill`, `.vs-mono`, tokens `--vs-`.
  - **Tabs por modo** (5 modos): signal `selectedMode`, al cambiar recarga la página 0.
  - Columnas: Posición, Avatar+Nombre (`.vs-user-cell`), ELO (`.vs-mono`), W/L,
    Racha (`.vs-pill`).
  - **Resaltar fila del jugador autenticado**: si `auth.isAuthenticated()`, comparar
    `row.userId === auth.user()?.id`; aplicar clase vía binding `[class.is-me]="..."`
    (nunca lógica en plantilla, según convención del proyecto). Fondo destacado con
    `--vs-accent-blue`.
  - **Paginación**: controles Anterior/Siguiente + indicador `page+1 / totalPages`
    (reutilizable como base para que admin pagine luego). Tamaño 20.
  - Estados de carga/vacío con signals (`loading`, `rows`).
- Enlace en `topbar` (ya referenciado en nav según exploración) → ruta `/rankings`.

### 4. Widget de ELO en el Dashboard
- En `features/player/pages/dashboard/dashboard.ts`:
  - Inyectar `RankingService`. En `ngOnInit`, además de `statsApi.mine()`, pedir el ranking
    propio del modo principal (o iterar modos para mostrar rating por modo).
  - Hoy el 4º stat block tiene `num: '—', label: 'Ranking global'` (línea 36) →
    sustituir por el ELO real y la **posición global**.
  - Añadir un bloque/sección "Tu ELO por modo": rating actual por modo + **flecha de
    tendencia (+/-)**. La tendencia requiere el delta de la última partida:
    - Fuente de la tendencia: el último delta se entrega en la pantalla de resultado
      (sección 5). Persistir el último delta por modo en `localStorage`
      (`vs.elo.lastDelta.<MODE>`) al mostrar el resultado, y leerlo en el dashboard para
      pintar la flecha ▲ verde (`--vs-accent-green`) / ▼ roja (`--vs-accent-red`) /
      `—` si no hay dato. (Evita un endpoint extra de "histórico"; consistente con el
      requisito sin sobreingeniería.)

### 5. Notificación post-partida (pantalla de resultado)
- `features/player/pages/result/result.ts` lee el estado de navegación
  (`ResultState`, líneas 5–11, 50–57). Hoy `ResultState` solo cubre single-player
  (`mode: 'SURVIVAL' | 'PRECISION'`).
- Ampliar `ResultState` con un campo **opcional** `eloDelta?: number` y `eloMode?: GameMode`.
  - Mientras no haya PvP, las pantallas single-player (`survival.ts:163`,
    `precision.ts:118`) **no** lo envían → el bloque ELO no se muestra (sin romper nada).
  - Cuando exista PvP, el flujo de fin de partida PvP navegará a `/play/result` incluyendo
    `eloDelta` (obtenido del `EloApplyResult` del backend vía WebSocket `MATCH_END` o REST).
- UI: bloque destacado encima de "VOLVER AL INICIO" mostrando `+24 ELO` (verde,
  `--vs-accent-green`) o `-18 ELO` (rojo, `--vs-accent-red`), `.vs-mono`. Computed
  `eloLabel`/`eloColor` (sin lógica en plantilla).
- Al renderizar, si hay `eloDelta`, escribir `vs.elo.lastDelta.<eloMode>` en `localStorage`
  (alimenta la flecha de tendencia del dashboard, sección 4).

### 6. Tests frontend (vitest)
- `RankingService`: URLs y params correctos (`HttpTestingController`).
- `RankingsPage`: render de filas, cambio de tab recarga, resaltado de fila propia cuando
  `userId` coincide, paginación llama al servicio con la página correcta.
- `Result`: muestra `+N ELO`/`-N ELO` con color correcto cuando `eloDelta` está presente;
  no muestra nada cuando es `undefined`.

---

## Activación futura (PvP) — cambio mínimo cuando exista un modo multijugador

Cuando se implemente al menos un modo multijugador, **activar este sistema requiere solo
esto** (actualizar este plan con estos pasos y ejecutarlos):

1. **Backend (1 llamada):** en el código que finalice una partida PvP (el servicio análogo
   a `GameService.finishMatch(...)` pero con 2 `MatchPlayer`), tras determinar ganador y
   perdedor, llamar:
   ```java
   EloApplyResult elo = eloService.applyForFinishedMatch(winnerId, loserId, match.getMode());
   ```
   Inyectar `EloService` (igual que `GameService` ya inyecta `StatsService`).
2. **Backend (propagar delta):** incluir `elo.deltaFor(userId)` en el payload del evento
   `MATCH_END` (WebSocket) o en la respuesta REST de fin de partida, para cada jugador.
3. **Frontend (propagar a resultado):** en el flujo que navega a `/play/result` al acabar
   un PvP, incluir `eloDelta` y `eloMode` en el `state` de navegación (el componente
   `Result` ya estará preparado para mostrarlo).
4. Quitar/condicionar el sembrado dev de rankings si ya hay datos reales.

Ningún otro archivo necesita cambios: endpoints, leaderboard, paginación, widget de
dashboard, tests y modelos ya están completos y verificados con datos sembrados.

---

## Criterios de aceptación → cómo se cumplen

| Criterio | Cómo |
|---|---|
| ELO se actualiza correctamente tras cada partida PvP | `EloService.applyForFinishedMatch` (fórmula estándar K=32, tests unitarios). Se cablea con 1 llamada cuando exista PvP. |
| Leaderboard carga en <500ms con paginación | `Page<T>` + `Pageable` (patrón `ModerationController`) + índice existente `(mode, score DESC)` + `@Query` con JOIN a `User` (sin N+1). |
| La posición propia se resalta en el ranking | `GET /api/rankings/me` + binding `[class.is-me]` comparando `userId` con `auth.user()?.id`. |
| Los cambios de ELO se muestran en la pantalla de resultado | `ResultState.eloDelta` → bloque `+N/-N ELO` en `result`. Preparado hoy; se rellena al activar PvP. |

---

## Archivos críticos

**Backend (nuevos salvo indicación):**
- `ranking/domain/Ranking.java` (movido desde `stats/domain/`, +wins/losses/winStreak)
- `ranking/repo/RankingRepository.java` (movido desde `stats/repo/`, +consultas)
- `ranking/EloService.java`, `ranking/RankingController.java`
- DTOs: `ranking/dto/RankingRowResponse.java`, `MyRankingResponse.java`,
  `EloApplyResult.java`
- `config/DevSeedConfig.java` (modificar: sembrar rankings)
- `config/SecurityConfig.java` (modificar: permitir `GET /api/rankings` sin auth)
- Tests: `ranking/EloServiceTest.java`, `ranking/RankingControllerTest.java`

**Frontend (nuevos salvo indicación):**
- `core/models/ranking.models.ts`, `core/services/ranking.service.ts`
- `features/rankings/pages/rankings/rankings.ts|html|scss`
- `app.routes.ts` (modificar: ruta pública `/rankings`)
- `features/player/pages/dashboard/dashboard.ts|html` (modificar: widget ELO + tendencia)
- `features/player/pages/result/result.ts|html|scss` (modificar: bloque delta ELO)
- `shared/components/layout/topbar/topbar.ts` (modificar: enlace a `/rankings` si falta)
- Tests: `ranking.service.spec.ts`, `rankings.spec.ts`, ampliar `result` spec

---

## Verificación end-to-end (sin PvP, hoy)

Backend:
- `cd backend && ./mvnw test` — pasa `EloServiceTest` y `RankingControllerTest`.
- `./mvnw spring-boot:run` con perfil `dev` (rankings sembrados). Probar:
  - `GET http://localhost:8080/api/rankings?mode=BINARY_DUEL&page=0&size=20` (sin token) →
    200, lista ordenada por rating desc, `position` correcta, < 500 ms.
  - `GET /api/rankings/me?mode=BINARY_DUEL` con token del usuario sembrado →
    su rating y posición.
  - `GET /api/users/{id}/ranking` → ranking de ese usuario.
  - Swagger UI (`springdoc` ya configurado) para inspeccionar contratos.

Frontend (`cd frontend && npm start`, http://localhost:4200):
- `/rankings` **sin estar logueado** → leaderboard visible, tabs por modo funcionan,
  paginación avanza/retrocede.
- Login con usuario sembrado → su fila aparece resaltada; dashboard muestra ELO real y
  posición; flecha de tendencia `—` (sin partidas aún).
- Simular pantalla de resultado con `eloDelta` (navegar a `/play/result` con
  `state: { mode:'SURVIVAL', score:0, bestStreak:0, rounds:0, won:true, eloDelta:24,
  eloMode:'BINARY_DUEL' }` desde la consola/un botón de prueba) → se ve `+24 ELO` en
  verde; volver al dashboard → flecha ▲ verde para ese modo.
- `ng test` — specs nuevos en verde.

Verificación de activación PvP (cuando exista): tras el cambio de 4 pasos, jugar una
partida PvP completa y comprobar que ambos ratings cambian, el leaderboard se reordena y la
pantalla de resultado muestra el delta real.
