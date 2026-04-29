# Backend MVP — Sprint 3

> Briefing de implementación del backend de Versus para el Sprint 3.
> Cubre: **multijugador en tiempo real con WebSockets** — sala privada, matchmaking, y los tres modos PvP (Duelo Binario, Duelo de Precisión, Sabotaje).

---

## Tarea

Eres el implementador del backend de **Versus**. Los Sprints 1 y 2 ya están entregados: Auth, Users, Questions, Game singleplayer (Survival, Precision) y Stats básicos funcionan. Ahora toca el **multijugador en tiempo real**: WebSockets sobre STOMP, salas privadas con código, matchmaking automático, y la lógica de los tres modos PvP.

## Contexto que DEBES leer antes de empezar

Lee estos archivos del repo en este orden:

1. `CLAUDE.md` (raíz) — visión general
2. `docs/guia-de-coordinación-técnica.md` — **contrato de API canónico**, sección "Módulo 5 — MATCH"
3. `docs/backend-mvp-sprint-1-2.md` — briefing previo, te da el modelo de paquetes y patrones que ya están en uso
4. `docs/bd-scheme.md` — schema final tras los Sprints 1-2
5. Código existente: `backend/src/main/java/com/versus/api/match/` (entidades ya creadas en Sprint 1) y `backend/src/main/java/com/versus/api/game/` (lógica singleplayer como referencia)

No leas el frontend salvo si necesitas verificar un evento WebSocket o un payload.

## Stack y restricciones

- Java 25, Spring Boot 4.0.5, PostgreSQL 18 (`spring-boot-starter-websocket` ya añadido en Sprint 1)
- STOMP sobre SockJS (estándar Spring)
- Errores REST siguen el formato `{ "error": "CODE", "message": "...", "status": N }`
- Errores WebSocket: enviar evento `ERROR` por el canal del usuario con el mismo shape
- DTOs separados de entidades (regla del proyecto)
- Validación con `jakarta.validation` en request DTOs y en payloads STOMP (`@Valid` en handlers)

## Cambios al esquema DB

Pequeños:

1. **`matches`**: añadir UNIQUE parcial en `room_code WHERE room_code IS NOT NULL`. Las salas privadas usan códigos de 6 caracteres alfanuméricos en mayúsculas.
2. **`match_players`**: añadir `is_ready BOOLEAN DEFAULT FALSE` (para fase de "pulsa Listo" antes de empezar).
3. **`matchmaking_queue`**: añadir `last_seen TIMESTAMP` para limpiar entradas zombie tras desconexión.
4. Cuando termines, **actualiza `docs/bd-scheme.md`**.

## Estructura de paquetes (extender la existente)

```
com.versus.api/
├── config/
│   ├── WebSocketConfig.java        (NUEVO — registra /ws, configura STOMP, broker, prefijos)
│   └── WebSocketSecurityConfig.java (NUEVO — autenticación STOMP por JWT en CONNECT)
├── match/
│   ├── controller/
│   │   ├── MatchRestController.java       (REST: queue, room, state)
│   │   └── MatchWebSocketController.java  (STOMP @MessageMapping handlers)
│   ├── service/
│   │   ├── MatchmakingService.java        (cola FIFO por modo)
│   │   ├── RoomService.java               (sala privada, código, join)
│   │   ├── MatchOrchestrator.java         (estado de partida, rondas, timers)
│   │   ├── modes/
│   │   │   ├── BinaryDuelHandler.java
│   │   │   ├── PrecisionDuelHandler.java
│   │   │   └── SabotageHandler.java
│   │   └── DamageCalculator.java          (centraliza fórmulas de los 3 modos)
│   ├── dto/
│   │   ├── ws/                            (eventos WS: MatchFoundEvent, MatchStartEvent, QuestionEvent, RoundResultEvent, MatchEndEvent, ErrorEvent)
│   │   └── rest/                          (CreateRoomResponse, JoinRoomRequest, MatchStateResponse, QueueRequest)
│   └── domain/                            (entidades ya creadas en Sprint 1)
└── common/
    └── ws/
        ├── StompPrincipal.java            (Principal con userId)
        └── StompAuthChannelInterceptor.java (extrae JWT, valida, establece Principal)
```

## Configuración WebSocket

`WebSocketConfig`:

- Endpoint: `/ws` con SockJS habilitado
- Broker simple (en memoria, suficiente para MVP)
- Prefijo aplicación: `/app`
- Prefijo broker: `/topic`, `/queue`
- Prefijo usuario: `/user`

`WebSocketSecurityConfig` (vía `ChannelInterceptor`):

- En `CONNECT`: leer header `Authorization: Bearer <jwt>`, validar con `JwtService` (ya existe en Sprint 1), establecer `StompPrincipal` con el `userId`. Si falla → cerrar conexión.
- En el resto de comandos (`SUBSCRIBE`, `SEND`): verificar que hay Principal.

## Lógica común de partida multijugador

**Modelo de Match:** ya está en BD. Una partida PvP = `Match` con `status` evolucionando `WAITING → IN_PROGRESS → FINISHED`, dos `MatchPlayer` (no más en MVP), N `MatchRound` con FK a `Question`, y `MatchAnswer` por jugador y ronda.

**Orquestación de ronda (servidor-autoritativo):**

1. `MatchOrchestrator` mantiene en memoria el estado activo de la partida (`Map<UUID matchId, MatchState>`). Persiste cambios relevantes en BD (round creado, answer guardada, match finalizado).
2. Al empezar partida: emite `MATCH_START` con la 1ª pregunta a `/topic/match/{id}`.
3. Lanza un timer de 15 segundos por ronda. Si un jugador no responde, se asume respuesta vacía (peor desviación / fallo).
4. Cuando ambos jugadores han respondido (o expira el timer), llama al `*ModeHandler` correspondiente para calcular `lifeDelta` por jugador.
5. Emite `ROUND_RESULT` a `/topic/match/{id}`.
6. Si alguien llega a `livesRemaining ≤ 0` o se han jugado N rondas (límite por modo), finaliza:
   - Persiste `Match.status=FINISHED`, `finished_at`, `MatchPlayer.result`
   - Emite `MATCH_END` con ganador y stats
   - Actualiza `player_stats` y `rankings` de ambos jugadores
7. Si no, emite siguiente `QUESTION`.

**Vidas iniciales por modo:**

| Modo | Vidas | Tipo de pregunta | Rondas máx |
|---|---|---|---|
| `BINARY_DUEL` | 3 | BINARY | 15 |
| `PRECISION_DUEL` | 100 | NUMERIC | 10 |
| `SABOTAGE` | 100 | NUMERIC | 10 |

**Selección de pregunta:** al iniciar cada ronda, pedir una pregunta aleatoria del tipo correspondiente que no haya aparecido aún en la partida. Si se acaban, terminar partida.

## Lógica de daño por modo

Centralizar en `DamageCalculator`. Marcar cada fórmula con `// TODO(#XX): confirmar con el equipo`.

### Duelo Binario (issue #68)

- Ambos responden, el que acierta no pierde vida.
- El que falla: `lifeDelta = -1`.
- Si ambos fallan: ambos `-1`.
- Si ambos aciertan: nadie pierde, sigue.

### Duelo de Precisión (issue #71)

- Ambos responden NUMERIC, se calcula `dev_i = |answer_i - correct| / correct * 100`.
- El de menor `dev`: `lifeDelta = 0`.
- El de mayor `dev`: `lifeDelta = -min(50, round(|dev_loser - dev_winner|))`.
- Empate técnico (`|dev_a - dev_b| < 0.01`): ambos `0`.

### Sabotaje (issue #74)

- Ambos responden NUMERIC. El que acierta mejor **daña al rival** en proporción a su acierto.
- `accuracy_winner = max(0, 100 - dev_winner)` (cuanto más cerca, más daño hace).
- `lifeDelta_loser = -min(50, round(accuracy_winner / 2))`.
- `lifeDelta_winner = 0`.
- Empate técnico: ambos `0`.

## Endpoints REST

Todos bajo `/api/match/*`, autenticados (PLAYER+).

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/match/queue` | Body: `{ "mode": "BINARY_DUEL" }`. Inserta en `matchmaking_queue` (UNIQUE user+mode). Si ya hay otro esperando con el mismo modo, los empareja inmediatamente y emite `MATCH_FOUND` a ambos. Devuelve `{ "status": "WAITING" \| "MATCHED", "matchId": "..." }`. |
| `DELETE` | `/api/match/queue` | Quita al usuario de la cola. 204. |
| `POST` | `/api/match/room` | Body: `{ "mode": "..." }`. Crea `Match` con `room_code` único de 6 chars, `status=WAITING`, añade al creador como `MatchPlayer`. Devuelve `{ "matchId", "roomCode" }`. |
| `POST` | `/api/match/room/join` | Body: `{ "roomCode": "ABC123" }`. Busca match, valida `status=WAITING` y solo 1 player, añade al usuario, emite `MATCH_FOUND` a ambos. 409 si llena, 404 si no existe. |
| `GET` | `/api/match/{matchId}` | Estado actual de la sala. Solo si el usuario es uno de los `MatchPlayer`. |

## Endpoints WebSocket

### Cliente → Servidor

| Destino | Payload | Acción |
|---|---|---|
| `/app/match/ready` | `{ "matchId": "..." }` | Marca `MatchPlayer.is_ready=true`. Cuando ambos están ready, dispara `MATCH_START`. |
| `/app/match/answer` | `{ "matchId", "questionId", "optionId" \| "value" }` | Registra respuesta del jugador. Si ambos han respondido o expiró timer, dispara `ROUND_RESULT`. Validar tipo según modo. |
| `/app/match/leave` | `{ "matchId": "..." }` | Abandono voluntario. El otro gana automáticamente. |

### Servidor → Cliente

Eventos publicados por el `MatchOrchestrator`:

| Evento | Canal | Payload |
|---|---|---|
| `MATCH_FOUND` | `/user/queue/match` | `{ "type": "MATCH_FOUND", "matchId", "mode", "opponent": { "id", "username", "avatarUrl" } }` |
| `MATCH_START` | `/topic/match/{id}` | `{ "type": "MATCH_START", "mode", "question", "timeLimit": 15, "initialLives": N }` |
| `QUESTION` | `/topic/match/{id}` | `{ "type": "QUESTION", "roundNumber", "question", "timeLimit": 15 }` |
| `ROUND_RESULT` | `/topic/match/{id}` | `{ "type": "ROUND_RESULT", "roundNumber", "answers": [{ "userId", "answerGiven", "isCorrect", "deviation" }], "lifeDeltas": { "<userId>": N }, "livesRemaining": { "<userId>": N }, "correctAnswer": "..." }` |
| `MATCH_END` | `/topic/match/{id}` | `{ "type": "MATCH_END", "winnerId" \| null, "result": "WIN" \| "DRAW" \| "ABANDONED", "stats": { "<userId>": { "correctAnswers": N, "avgDeviation": N \| null } } }` |
| `OPPONENT_LEFT` | `/topic/match/{id}` | `{ "type": "OPPONENT_LEFT", "userId" }` |
| `ERROR` | `/user/queue/match` | `{ "type": "ERROR", "error": "CODE", "message": "..." }` |

> Todos los eventos deben llevar el campo `type` (discriminador para el frontend).

## Lo que tienes que implementar (orden)

1. **`WebSocketConfig` + `StompAuthChannelInterceptor` + `StompPrincipal`.** Test: conectar con JWT válido funciona, sin token rechaza el CONNECT.
2. **REST de salas:** `POST /match/room`, `POST /match/room/join`, `GET /match/{id}`. Test integración con dos usuarios mock.
3. **REST de matchmaking:** `POST /match/queue`, `DELETE /match/queue`, con FIFO simple por modo.
4. **`MatchOrchestrator` esqueleto:** estado en memoria, persistencia de rondas, timer de ronda. Sin lógica de modos aún.
5. **STOMP handlers:** `/app/match/ready`, `/app/match/answer`, `/app/match/leave`.
6. **`BinaryDuelHandler`** + integración end-to-end (dos clientes conectan, juegan, alguien gana, `MATCH_END` se emite).
7. **`PrecisionDuelHandler`**.
8. **`SabotageHandler`**.
9. **Actualización de `player_stats` y `rankings`** al finalizar partida (usa servicios de Stats del Sprint 1-2; añade método `recordPvpMatch`).
10. **Limpieza:** scheduler `@Scheduled` cada 30s que purga `matchmaking_queue` con `last_seen` antiguo (>60s).
11. **Tests:**
    - Unit: cada `*Handler` con casos de daño borde.
    - Integración: flujo completo BINARY_DUEL con dos clientes STOMP (`@SpringBootTest` + `WebSocketStompClient`).

## Manejo de desconexiones (mínimo viable)

- `SessionDisconnectEvent` → si el usuario estaba en una partida `IN_PROGRESS`, marcar como `ABANDONED`, dar la victoria al otro, emitir `MATCH_END`.
- Si estaba en cola de matchmaking, eliminar de la cola.

## Lo que NO debes hacer

- No implementar reconexión / "rejoin in progress match" → futuro.
- No implementar más de 2 jugadores por partida.
- No implementar chat / emotes.
- No tocar moderación, admin, scraping, ranking endpoints (aunque sí debes actualizar la tabla `rankings` al finalizar partida) → Sprint 4.
- No tocar el frontend.
- No usar Redis / RabbitMQ → broker simple en memoria es suficiente para MVP.

## Entrega

1. Resumen de lo implementado y de los tests añadidos (especial atención a los tests STOMP end-to-end).
2. Lista de TODOs marcados (especialmente fórmulas de daño).
3. Instrucciones para probar manualmente con dos clientes (curl + un cliente STOMP, p.ej. `wscat` o un script Java).
4. `docs/bd-scheme.md` actualizado.

## Cuando dudes

Pregunta. Especialmente sobre:

- Fórmulas de daño (los TODOs)
- Estrategia de timeout y desconexión
- Si los tests STOMP requieren librerías nuevas
