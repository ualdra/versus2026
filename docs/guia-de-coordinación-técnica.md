# ⚔️ VERSUS — Guía de coordinación técnica
 
> Este documento es la referencia central del equipo. Si no sabes por dónde empezar, empieza aquí. Si tienes dudas de qué hace un endpoint, búscalo aquí. Si vas a crear algo nuevo, comprueba que no existe ya aquí.
 
---
 
## 🧠 Qué es Versus (versión rápida)
 
Un juego de preguntas multijugador con **5 modos de juego**. Las preguntas se extraen automáticamente de la web con scrapers. Hay matchmaking, ranking global, perfiles y sistema de vidas.
 
**Stack:**
- **Backend:** Spring Boot — API REST + WebSockets
- **Frontend:** Angular — SPA
- **Base de datos:** PostgreSQL
- **Scraping:** Scrapy (Python)
- **Infraestructura:** Docker
---
 
## 🗺️ Módulos del sistema
 
El proyecto se divide en **10 módulos**. Cada issue pertenece a uno.
 
```
┌─────────────────────────────────────────────────────────┐
│                        VERSUS                           │
│                                                         │
│  [AUTH]  [USERS]  [SOCIAL]  [QUESTIONS]  [GAME]        │
│  [MATCH]  [STATS]  [ACHIEVEMENTS]  [SCRAPING] [ADMIN]  │
└─────────────────────────────────────────────────────────┘
```
 
---
 
## 📦 Módulo 1 — AUTH
> Issues: #39, #40, #84, #85
 
Autenticación con JWT. Login, registro, refresco de token y roles.
 
### Endpoints
 
| Método | Ruta | Descripción | Issues |
|--------|------|-------------|--------|
| `POST` | `/api/auth/register` | Registro de nuevo usuario | #85 |
| `POST` | `/api/auth/login` | Login → devuelve `accessToken` + `refreshToken` | #84 |
| `POST` | `/api/auth/refresh` | Renueva el accessToken con el refreshToken | #84 |
| `POST` | `/api/auth/logout` | Invalida el refreshToken | #84 |
 
### Contrato de login
 
**Request:**
```json
POST /api/auth/login
{
  "email": "raul@versus.com",
  "password": "mipassword"
}
```
 
**Response:**
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci...",
  "user": {
    "id": "uuid",
    "username": "Raúl",
    "role": "PLAYER",
    "avatarUrl": "https://..."
  }
}
```
 
### Roles disponibles
 
| Rol | Valor | Acceso |
|-----|-------|--------|
| Jugador | `PLAYER` | Todo lo de juego |
| Moderador | `MODERATOR` | + gestión de reportes |
| Admin | `ADMIN` | + gestión de usuarios y spiders |
 
### Para el frontend (#40)
- Implementar `AuthInterceptor` que adjunte el `Bearer token` en cada petición
- Implementar `AuthGuard` para rutas protegidas
- Guardar tokens en `localStorage` o `sessionStorage`
---
 
## 👤 Módulo 2 — USERS
> Issues: #85
 
Gestión del perfil de usuario.
 
### Endpoints
 
| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/api/users/me` | Perfil del usuario autenticado |
| `PUT` | `/api/users/me` | Actualizar username o avatar |
| `PUT` | `/api/users/me/password` | Cambiar contrasena (requiere contrasena actual) |
| `PUT` | `/api/users/me/avatar` | Seleccionar avatar por URL o subir imagen multipart |
| `DELETE` | `/api/users/me` | Eliminar cuenta con soft delete y anonimizacion |
| `GET` | `/api/users/:id` | Perfil público de cualquier usuario |
 
### Contrato de perfil
 
```json
GET /api/users/me
→ 200
{
  "id": "uuid",
  "username": "Raúl",
  "email": "raul@versus.com",
  "avatarUrl": "https://...",
  "role": "PLAYER",
  "createdAt": "2025-01-01T00:00:00Z"
}
```

### Contratos de ajustes de cuenta

La pantalla `/settings` centraliza cuenta, avatar, notificaciones, audio y zona de peligro.

**Cambiar password:**
```json
PUT /api/users/me/password
{
  "currentPassword": "actual123",
  "newPassword": "nueva1234"
}
```
Respuesta: `204 No Content`. La nueva contrasena debe tener minimo 8 caracteres.

**Seleccionar avatar predefinido:**
```json
PUT /api/users/me/avatar
Content-Type: application/json
{
  "avatarUrl": "https://api.dicebear.com/..."
}
```

**Subir avatar propio:**
```http
PUT /api/users/me/avatar
Content-Type: multipart/form-data

file=<png|jpeg|max 2MB>
```

El backend devuelve `UserMeResponse` y el frontend actualiza topbar/perfil inmediatamente. Hasta que exista el modulo de almacenamiento, el upload se guarda como `data:image/...;base64` en `users.avatar_url`.

**Eliminar cuenta:**
```http
DELETE /api/users/me
```
Respuesta: `204 No Content`. En frontend se exige doble confirmacion escribiendo el username exacto. En backend se aplica soft delete con `status=DELETED`, `is_active=false` y anonimizacion de username/email/avatar/password.

### Pantalla `/settings` (frontend)

- Cuenta: username editable; email visible pero dependiente del modulo de email para cambio real.
- Password: requiere password actual y confirmacion visual en el formulario.
- Avatar: galeria de avatares predefinidos con confirmacion `Aceptar/Cancelar`; upload PNG/JPEG con crop basico y boton de subida.
- Notificaciones: preferencias de solicitudes de amistad, invitaciones y logros guardadas en `localStorage`.
- Centro de notificaciones: desplegable en el topbar con contador de no leidas, historial local por usuario (`vs.notifications.<userId>`) y acciones de marcar leidas/vaciar.
- Audio: controles `Efectos de sonido`, `Musica de fondo`, silenciar todo y feedback reducido guardados en `localStorage`.
- Zona de peligro: borrar cuenta exige escribir el username.
- Topbar: muestra username/avatar reales y XP calculado desde `/api/stats/me` mientras no exista campo `xp` dedicado.
 
---

## 🤝 Módulo SOCIAL — AMIGOS E INVITACIONES
> Issue: #94

Gestiona busqueda de jugadores, solicitudes de amistad e invitaciones a partidas PvP entre amigos.

### Endpoints

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/api/social/users/search?query=ra` | Busca usuarios activos por username. Devuelve máximo 10 resultados. |
| `GET` | `/api/social/friends` | Lista de amigos del usuario autenticado. |
| `POST` | `/api/social/friend-requests` | Envía solicitud de amistad a `{ toUserId }`. |
| `GET` | `/api/social/friend-requests/incoming` | Solicitudes recibidas pendientes. |
| `GET` | `/api/social/friend-requests/outgoing` | Solicitudes enviadas pendientes. |
| `POST` | `/api/social/friend-requests/{id}/accept` | Acepta una solicitud recibida. |
| `POST` | `/api/social/friend-requests/{id}/decline` | Rechaza una solicitud recibida. |
| `DELETE` | `/api/social/friend-requests/{id}` | Cancela una solicitud enviada. |
| `POST` | `/api/social/match-invites` | Crea lobby PvP o reutiliza sala privada e invita a `{ friendUserId, mode, matchId? }`. |
| `GET` | `/api/social/match-invites/incoming` | Invitaciones recibidas pendientes. |
| `GET` | `/api/social/match-invites/outgoing` | Invitaciones enviadas recientes. |
| `POST` | `/api/social/match-invites/{id}/accept` | Acepta invitación y devuelve `LobbyStateDto`. |
| `POST` | `/api/social/match-invites/{id}/decline` | Rechaza invitación recibida. |

### Contratos principales

```json
POST /api/social/friend-requests
{ "toUserId": "uuid" }
```

```json
{
  "id": "uuid",
  "requester": { "userId": "uuid", "username": "Player1", "avatarUrl": null, "relation": "REQUEST_SENT" },
  "addressee": { "userId": "uuid", "username": "Player2", "avatarUrl": null, "relation": "REQUEST_RECEIVED" },
  "status": "PENDING",
  "createdAt": "2026-05-25T18:00:00Z",
  "respondedAt": null
}
```

```json
POST /api/social/match-invites
{ "friendUserId": "uuid", "mode": "BINARY_DUEL", "matchId": "uuid-opcional" }
```

`mode` debe ser multijugador: `BINARY_DUEL`, `PRECISION_DUEL` o `SABOTAGE`.
Si `matchId` se envia, debe ser una sala privada viva en `WAITING`, con el emisor dentro, mismo modo y espacio disponible.

### Eventos WebSocket

El cliente se suscribe a `/user/queue/social`.

| Evento | Payload |
|--------|---------|
| `FRIEND_REQUEST` | `{ requestId, from: SocialUser }` |
| `MATCH_INVITE` | `{ inviteId, matchId, mode, from: SocialUser }` |

`SocialUser = { userId, username, avatarUrl, relation }`, con `relation` en `SELF | NONE | FRIEND | REQUEST_SENT | REQUEST_RECEIVED`.

### Frontend

- Ruta `/friends` bajo `authGuard`.
- `SocialService` centraliza llamadas REST.
- `NotificationCenterService` escucha `/user/queue/social`, respeta `friendRequests` y `matchInvites` en `vs.notificationPrefs`, y enlaza a `/friends`.

---

## ❓ Módulo 3 — QUESTIONS
> Issues: #41, #42, #43, #44, #52
 
Preguntas binarias y numéricas que alimentan los modos de juego.
 
### Tipos de pregunta
 
| Tipo | Descripción | Ejemplo |
|------|-------------|---------|
| `BINARY` | Dos opciones, una correcta | ¿Quién tiene más goles: Messi o Cristiano? |
| `NUMERIC` | Respuesta numérica libre | ¿Cuántos seguidores tiene Cristiano en Instagram? |
 
### Endpoints
 
| Método | Ruta | Auth | Descripción | Issues |
|--------|------|------|-------------|--------|
| `GET` | `/api/questions/random` | No (público) | Pregunta aleatoria (opcionalmente por categoría o tipo) | #42 |
| `GET` | `/api/questions/random?type=BINARY&category=football` | No (público) | Filtros opcionales | #42, #43 |
| `GET` | `/api/questions/:id` | Sí (JWT) | Pregunta por ID | #41 |
| `GET` | `/api/questions/categories` | No (público) | Lista de categorías disponibles | #43 |
 
### Contrato de pregunta BINARY
 
```json
{
  "id": "uuid",
  "type": "BINARY",
  "text": "¿Quién tiene más seguidores en Instagram?",
  "category": "football",
  "options": [
    { "id": "uuid-a", "text": "Cristiano Ronaldo" },
    { "id": "uuid-b", "text": "Lionel Messi" }
  ],
  "scrapedAt": "2025-04-01T00:00:00Z"
}
```
 
> ⚠️ La opción correcta **NO se envía al frontend** hasta que el jugador responde. El backend la guarda internamente y la valida al recibir la respuesta.
 
### Contrato de pregunta NUMERIC
 
```json
{
  "id": "uuid",
  "type": "NUMERIC",
  "text": "¿Cuántos seguidores tiene Cristiano Ronaldo en Instagram?",
  "category": "football",
  "unit": "millones",
  "scrapedAt": "2025-04-01T00:00:00Z"
}
```
 
---
 
## 🎮 Módulo 4 — GAME (modos en solitario)
> Issues: #53, #54, #55, #56, #57, #58, #59, #60, #61, #62
 
Los dos modos de un solo jugador: **Supervivencia** y **Precisión**.
 
### Flujo general de partida
 
```
Frontend                          Backend
   │                                 │
   ├─ POST /api/game/start ─────────►│ Crea sesión de partida
   │◄── { sessionId, question } ─────┤ Devuelve 1ª pregunta
   │                                 │
   ├─ POST /api/game/answer ────────►│ Valida respuesta
   │◄── { correct, lifeDelta,  ──────┤ Devuelve resultado
   │      nextQuestion | gameOver }  │
   │                                 │
   └─ (si gameOver) ─────────────────┤ Guarda historial
```
 
### Endpoints modo Supervivencia
 
| Método | Ruta | Descripción | Issues |
|--------|------|-------------|--------|
| `POST` | `/api/game/survival/start` | Inicia partida, devuelve sesión + 1ª pregunta | #53 |
| `POST` | `/api/game/survival/answer` | Envía respuesta, recibe resultado y siguiente pregunta | #53, #55 |
 
**Request answer:**
```json
{
  "sessionId": "uuid",
  "questionId": "uuid",
  "optionId": "uuid-a"
}
```
 
**Response answer:**
```json
{
  "correct": true,
  "livesRemaining": 3,
  "lifeDelta": 0,
  "streak": 4,
  "scoreDelta": 150,
  "nextQuestion": { ... },
  "gameOver": false,
  "achievementsUnlocked": []
}
```
 
### Endpoints modo Precisión
 
| Método | Ruta | Descripción | Issues |
|--------|------|-------------|--------|
| `POST` | `/api/game/precision/start` | Inicia partida numérica | #60 |
| `POST` | `/api/game/precision/answer` | Envía número, recibe desviación y daño/curación | #59, #60 |
 
**Request answer:**
```json
{
  "sessionId": "uuid",
  "questionId": "uuid",
  "value": 650000000
}
```
 
**Response answer:**
```json
{
  "correctValue": 640000000,
  "deviation": 1.56,
  "deviationPercent": 1.56,
  "lifeDelta": 5,
  "livesRemaining": 105,
  "nextQuestion": { ... },
  "gameOver": false,
  "achievementsUnlocked": []
}
```
 
> 📐 `lifeDelta` positivo = curación, negativo = daño. El algoritmo de daño/curación se define en #59.
 
---
 
## ⚡ Módulo 5 — MATCH (modos multijugador)
> Issues: #63, #64, #65, #66, #67, #68, #69, #70, #71, #72, #73, #74
 
Los tres modos PvP en tiempo real: **Duelo binario**, **Duelo de precisión** y **Sabotaje**.
 
### ⚠️ Este módulo usa WebSockets, no REST
 
La comunicación durante la partida es por WebSocket (`STOMP` sobre SockJS es el estándar en Spring Boot).
 
### Conexión WebSocket
 
```
Frontend conecta a:  ws://localhost:8080/ws  (STOMP sobre SockJS)
Auth: header CONNECT  Authorization: Bearer <jwt>
 
Suscripciones del cliente:
  /user/queue/achievements   -> logros desbloqueados (ACHIEVEMENT_UNLOCKED)
  /user/queue/match          → notificaciones privadas (MATCH_FOUND)
  /user/queue/social         → solicitudes de amistad e invitaciones (FRIEND_REQUEST, MATCH_INVITE)
  /topic/match/{matchId}     → estado compartido del lobby/partida
 
Envíos del cliente:
  /app/match/ready           → marcar listo en el lobby       (PR #90)
  /app/match/unready         → quitar listo                    (PR #90)
  /app/match/abandon         → abandonar la sala vía WS        (PR #90)
  /app/match/answer          → enviar respuesta a una ronda    (PR #91 #92 #93)
  /app/match/sabotage        → activar sabotaje                (PR #93)
```

Detalles de la capa de transport (envelope, autenticación, reconexión) en [`docs/backend/modules/websocket.md`](backend/modules/websocket.md).
 
### Flujo de sala de espera → partida (PR #90, ya implementado)
 
```
1. POST /api/matchmaking/queue {mode}   → Entrar en cola
2. Scheduler empareja N jugadores       → emite MATCH_FOUND a cada uno
3. Frontend redirige a /play/lobby/:matchId
4. SUBSCRIBE /topic/match/{id}; GET /api/matches/{id}/lobby para snapshot
5. Cada jugador envía /app/match/ready  → emite PLAYER_READY
6. Cuando todos están listos            → emite MATCH_STARTING { countdownSeconds }
7. Tras el countdown                    → emite MATCH_START { matchId, mode }
8. (PR #91+) lógica de juego: QUESTION / ROUND_RESULT / MATCH_END
```

### Flujo de partida privada con código (issue #105)

Desde el lobby privado, el host tambien puede invitar a un amigo con `POST /api/social/match-invites` enviando `{ friendUserId, mode, matchId }`.

```
1. Host: POST /api/matches {mode}              → crea sala y recibe {matchId, roomCode}
2. Host comparte roomCode                      → código de 6 chars, generado server-side
3. Invitado: POST /api/matches/join-by-code    → body {roomCode}
4. Backend normaliza abc-234 → ABC234          → valida formato y estado WAITING
5. Invitado recibe LobbyStateDto               → frontend redirige a /play/lobby/:matchId
6. Ambos usan el mismo flujo de ready/countdown que matchmaking
```
 
### Endpoints REST de sala (PR #90, #105)
 
| Método | Ruta | Descripción | Issues |
|--------|------|-------------|--------|
| `POST` | `/api/matches` | Crear sala privada (devuelve `roomCode`) | #90 |
| `POST` | `/api/matches/{id}/join` | Unirse a sala existente | #90 |
| `POST` | `/api/matches/join-by-code` | Unirse a sala privada con `{ roomCode }` | #105 |
| `DELETE` | `/api/matches/{id}/abandon` | Abandonar la sala | #90 |
| `GET` | `/api/matches/{id}/lobby` | Snapshot del estado del lobby | #90 |
| `POST` | `/api/matchmaking/queue` | Entrar en cola de matchmaking | #90 |
| `DELETE` | `/api/matchmaking/queue` | Salir de la cola | #90 |
 
### Eventos WebSocket (backend → frontend)

Todos los eventos van envueltos en `{ type, matchId, payload }`.

| Evento | PR | Canal | Payload |
|--------|----|-------|---------|
| `MATCH_FOUND` | #90 | `/user/queue/match` | `{ matchId, mode, opponents: PlayerInLobby[] }` |
| `PLAYER_JOINED` | #90 | `/topic/match/{id}` | `{ player: PlayerInLobby }` |
| `PLAYER_LEFT` | #90 | `/topic/match/{id}` | `{ userId }` |
| `PLAYER_READY` | #90 | `/topic/match/{id}` | `{ userId, ready }` |
| `MATCH_STARTING` | #90 | `/topic/match/{id}` | `{ countdownSeconds }` |
| `MATCH_START` | #90 | `/topic/match/{id}` | `{ matchId, mode }` |
| `QUESTION` | #91-#93 | `/topic/match/{id}` | `{ roundNumber, question, serverNow, deadline, timerSeconds, effectsApplied }` |
| `ANSWER_RESULT` | #91-#93 | `/user/queue/match` | `{ accepted, rejectionReason?, isCorrect?, deviation? }` |
| `ROUND_RESULT` | #91-#93 | `/topic/match/{id}` | `{ roundNumber, questionId, reveal, outcomes[], runtime }` |
| `MATCH_END` | #91-#93 | `/topic/match/{id}` | `{ winnerUserId?, reason, stats[] }` (`reason: NORMAL\|DISCONNECT\|MAX_ROUNDS_TIE`) |
| `SABOTAGE_ACTIVATED` | #93 | `/topic/match/{id}` | `{ type, by, target, appliesOnRound }` |
| `SABOTAGE_REJECTED` | #93 | `/user/queue/match` | `{ reason: NO_TOKENS\|ALREADY_USED\|INVALID_TARGET\|WRONG_PHASE\|UNSUPPORTED_MODE }` |
| `EFFECT_APPLIED` | #93 | `/topic/match/{id}` | `{ type, target, roundNumber }` |

`PlayerInLobby = { userId, username, avatarUrl, ready }`.
`PlayerRoundOutcome = { userId, answered, isCorrect, deviation, valueGiven, optionGiven, lifeDelta }`.
`PlayerRuntimeSnapshot = { userId, livesRemaining, score, currentStreak, sabotageTokens, pendingIncomingEffects: SabotageType[] }`.
`FinalStats = { userId, username, result: WIN|LOSS|DRAW|ABANDONED, livesRemaining, score, bestStreakInMatch, roundsPlayed, avgDeviation, sabotagesUsed }`.

### Mensajes que envía el cliente

| Destination | Payload | Modos |
|---|---|---|
| `/app/match/ready` · `/unready` · `/abandon` | `{ matchId }` | Todos (lobby) |
| `/app/match/answer` | `{ matchId, questionId, optionId? (UUID), value? (BigDecimal) }` | #91 #92 #93 |
| `/app/match/sabotage` | `{ matchId, type, targetUserId }` | #93 |

### Lógica de daño por modo (Sprint 4 — implementación final)

| Modo | Quién pierde vida | Detalles |
|------|-------------------|----------|
| **Binary Duel (#91)** | Quien falla | `-1` vida base. Bonus de racha: si el rival acertó con `streak >= 1` previo, **`-1` adicional**. Sin respuesta = `-1`. |
| **Precision Duel (#92)** | Quien tiene mayor desviación | `-max(1, ceil(|devLoser − devWinner| × 0.02))`. Empate de desviaciones = 0 daño + racha. Timeout = **-3** vidas. |
| **Sabotaje (#93)** | Mecánica binaria + efectos | +1 token cada 3 aciertos. Tres efectos: `TIME_BOMB` (-5s al timer del rival), `OBFUSCATION` (oculta opción), `LIFE_STEAL` (si target falla, atacante recupera +1 vida). |

Defaults: **3 vidas iniciales, 15s/pregunta (10s con TIME_BOMB), 10 rondas máximas**. Detalle completo en [`docs/backend/modules/duel.md`](backend/modules/duel.md).
 
---
 
## 📊 Módulo 6 — STATS & RANKING
> Issues: #54, #76, #77, #78, #79, #96
 
Historial de partidas, estadísticas personales y ranking global.
 
### Endpoints
 
| Método | Ruta | Descripción | Issues |
|--------|------|-------------|--------|
| `GET` | `/api/stats/me` | Resumen de stats en todos los modos (overview) | #77, #96 |
| `GET` | `/api/stats/me?mode=SURVIVAL` | Stats filtradas por modo | #77 |
| `GET` | `/api/users/me/history` | Historial paginado de partidas | #76, #96 |
| `GET` | `/api/users/me/history?mode=BINARY_DUEL` | Historial filtrado por modo | #96 |
| `GET` | `/api/matches/{id}` | Detalle completo de una partida | #96 |
| `GET` | `/api/ranking/:mode` | Top 100 de un modo | #78 |
| `GET` | `/api/ranking/:mode/me` | Posición propia en el ranking | #78 |
 
### Contrato de stats — overview (sin parámetro mode)
 
```json
GET /api/stats/me
→ 200
{
  "byMode": [
    {
      "mode": "SURVIVAL",
      "gamesPlayed": 42,
      "gamesWon": 28,
      "winRate": 66.6,
      "bestStreak": 12,
      "currentStreak": 3,
      "avgDeviation": null,
      "avgScore": 310
    }
  ],
  "favoriteMode": "SURVIVAL",
  "totalPlayTimeSeconds": 18450
}
```
 
> `byMode` contiene una entrada por cada modo (SURVIVAL, PRECISION, BINARY_DUEL, PRECISION_DUEL, SABOTAGE).  
> `favoriteMode` es el modo con más `gamesPlayed`, o `null` si el usuario nunca ha jugado.  
> `totalPlayTimeSeconds` es la suma de segundos de todas las partidas FINISHED del usuario.
 
### Contrato de stats — por modo
 
```json
GET /api/stats/me?mode=SURVIVAL
→ 200
{
  "mode": "SURVIVAL",
  "gamesPlayed": 42,
  "gamesWon": 28,
  "winRate": 66.6,
  "bestStreak": 12,
  "currentStreak": 3,
  "avgDeviation": null,
  "avgScore": 310
}
```
 
> `avgDeviation` solo aplica a modos numéricos (PRECISION, PRECISION_DUEL).  
> `avgScore` es el promedio de puntuación a lo largo de todas las partidas del modo; `null` si nunca se ha jugado.
 
### Contrato de historial de partidas
 
```json
GET /api/users/me/history?page=0&size=20&mode=BINARY_DUEL
→ 200
{
  "content": [
    {
      "id": "bbbb0000-0000-0000-0000-000000000002",
      "mode": "BINARY_DUEL",
      "result": "WIN",
      "score": 480,
      "bestStreak": 5,
      "livesRemaining": 2,
      "roundsPlayed": 10,
      "finishedAt": "2025-05-07T14:22:00Z",
      "opponent": {
        "id": "cccc0000-0000-0000-0000-000000000003",
        "username": "Rival",
        "avatarUrl": "https://..."
      }
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```
 
> `opponent` es `null` en modos solitarios (SURVIVAL, PRECISION).  
> El tamaño máximo de página es 50; valores superiores se clampean automáticamente.  
> `mode` es un query param opcional; si se omite se devuelven partidas de todos los modos.
 
### Contrato de detalle de partida
 
```json
GET /api/matches/{id}
→ 200
{
  "id": "bbbb0000-0000-0000-0000-000000000002",
  "mode": "PRECISION_DUEL",
  "createdAt": "2025-05-07T14:10:00Z",
  "finishedAt": "2025-05-07T14:22:00Z",
  "players": [
    {
      "userId": "aaaa0000-0000-0000-0000-000000000001",
      "username": "Player1",
      "score": 480,
      "livesRemaining": 2,
      "bestStreakInMatch": 5,
      "result": "WIN"
    }
  ],
  "rounds": [
    {
      "roundNumber": 1,
      "questionId": "dddd0000-0000-0000-0000-000000000004",
      "questionText": "¿Cuántos seguidores tiene...?",
      "correct": true,
      "answerGiven": "4200000",
      "deviation": 1.8
    }
  ]
}

→ 404  si la partida no existe
→ 403  si el usuario autenticado no es jugador de esa partida
```
 
> `deviation` es `null` para modos no numéricos (BINARY_DUEL, SABOTAGE).  
> `rounds` está ordenado por `roundNumber` ascendente y solo contiene las respuestas del usuario autenticado.
 
---

## Modulo 7 - ACHIEVEMENTS
> Sistema de logros y emblemas visibles en perfil/topbar.

Los logros se evaluan al terminar una partida singleplayer desde `GameService`. El catalogo inicial se siembra en arranque y cada logro solo puede desbloquearse una vez por usuario.

### Endpoints

| Metodo | Ruta | Descripcion |
|--------|------|-------------|
| `GET` | `/api/achievements` | Catalogo completo con estado para el usuario autenticado |

### Contrato de logro

```json
{
  "id": "uuid",
  "key": "first_game",
  "name": "Primeros pasos",
  "description": "Juega tu primera partida.",
  "iconKey": "first",
  "category": "Primeros pasos",
  "unlocked": true,
  "unlockedAt": "2026-05-07T15:00:00Z"
}
```

Si un logro esta bloqueado, el backend devuelve `name: "???"`, `description: "???"` e `iconKey: "lock"` para no revelar como desbloquearlo.

### Eventos WebSocket

| Evento | Canal | Payload |
|--------|-------|---------|
| `ACHIEVEMENT_UNLOCKED` | `/user/queue/achievements` | `{ "type": "ACHIEVEMENT_UNLOCKED", "achievement": { ... } }` |

### Frontend

- Toast global no bloqueante al desbloquear un logro.
- Centro de notificaciones: registra logros desbloqueados en tiempo real, respeta `vs.notificationPrefs.achievements` y enlaza a `/profile`.
- Perfil: seccion `Logros` con contador `desbloqueados/total`, grid de catalogo y fecha si esta desbloqueado.
- Topbar/avatar: muestra como emblema el logro desbloqueado mas reciente.
 
---
 
## 🕷️ Módulo 8 — SCRAPING

 
Scrapers en Scrapy que extraen datos reales y los insertan en PostgreSQL como preguntas.
 
### Spiders planificadas
 
| Spider | Fuente | Tipo pregunta | Issue |
|--------|--------|---------------|-------|
| RRSS (YouTube/TikTok/Twitch) | SocialBlade | NUMERIC | #97 ✅ |
| Estadísticas fútbol | FBref / Transfermarkt | NUMERIC + BINARY | #98 |
| Taquilla de cine | Box Office Mojo | NUMERIC | #98 |
| Capitales y geografía | Wikipedia | BINARY | #98 |
| Récords varios | Wikipedia / Guinness | NUMERIC | #98 |
 
### Pipeline de datos
 
```
Spider (Scrapy)
    │  yield QuestionItem(text, type, category, ...)
    ▼
DeerdaysScraperPipeline (#97 ✅)
    ├── Validación de calidad mínima
    ├── Deduplicación por SHA-256(text) → campo text_hash en questions
    ├── INSERT questions + question_options
    └── UPDATE spider_runs (questionsInserted, errors, finishedAt)
    │
    ▼
PostgreSQL → tabla questions (estado: PENDING_REVIEW)
    │
    ▼
Moderador revisa → estado: ACTIVE
```
 
> Ver documentación detallada del pipeline en [`docs/scraping-pipeline.md`](scraping-pipeline.md).
 
### Endpoints de gestión (solo ADMIN)

> Implementados en #97. El identificador de ruta es el **nombre** del spider, no su UUID.
 
| Método | Ruta | Descripción | Issue |
|--------|------|-------------|-------|
| `GET` | `/api/admin/spiders` | Lista de spiders con estado actual y último run | #97 ✅ |
| `POST` | `/api/admin/spiders/{name}/run` | Lanza el spider por nombre. 202 con el `SpiderRun` creado. 404 si no existe, 409 si ya está en ejecución | #97 ✅ |
| `GET` | `/api/admin/spiders/{name}/runs` | Historial de runs del spider ordenados por fecha descendente | #97 ✅ |
 
---

## 🛡️ Módulo 9 — ADMIN & MODERACIÓN
> Issues: #80, #81, #82

 
Gestión de preguntas reportadas y administración de la plataforma.
 
### Endpoints de moderación
 
| Método | Ruta | Descripción | Issues |
|--------|------|-------------|--------|
| `POST` | `/api/questions/{id}/report` | Reportar una pregunta (PLAYER autenticado) | #100 ✅ |
| `GET` | `/api/moderation/reports` | Lista de reportes, filtrable por `?status=` (MODERATOR+) | #100 ✅ |
| `PUT` | `/api/moderation/reports/{id}/resolve` | Resolver reporte: DISMISS / EDIT_QUESTION / DELETE_QUESTION (MODERATOR+) | #100 ✅ |
 
#### Contrato de reporte (POST `/api/questions/{id}/report`)
 
**Request:**
```json
{ "reason": "WRONG_ANSWER", "comment": "Texto opcional" }
```
Valores de `reason`: `WRONG_ANSWER`, `OUTDATED`, `OFFENSIVE`, `OTHER`
 
**Response 201:** `ReportResponse` — ver [`docs/moderation.md`](moderation.md)
 
#### Contrato de resolución (PUT `/api/moderation/reports/{id}/resolve`)
 
**Request:**
```json
{ "action": "DELETE_QUESTION" }
```
Valores de `action`: `DISMISS`, `EDIT_QUESTION`, `DELETE_QUESTION`
 
**Response 200:** `ReportResponse` con `status`, `resolvedBy`, `resolvedAt` y `action` rellenos.
 
#### Auto-flagging
 
Cuando una pregunta acumula **5 reportes PENDING**, su estado cambia automáticamente a `FLAGGED` y deja de servirse en partidas hasta que un moderador la revisa.
 
> Ver documentación completa en [`docs/moderation.md`](moderation.md).
 
### Endpoints de administración
 
Todos requieren rol `ADMIN`. Protegidos con `@PreAuthorize("hasRole('ADMIN')")` y matcher de ruta.
 
| Método | Ruta | Descripción | Issues |
|--------|------|-------------|--------|
| `GET` | `/api/admin/users?page=0&size=20&search=&role=&active=` | Lista paginada con filtros opcionales | #82 |
| `PUT` | `/api/admin/users/{id}/role` | Cambiar rol. No permite self-demotion. | #82 |
| `PUT` | `/api/admin/users/{id}/status` | Activar/suspender cuenta. No permite self-suspend. | #82 |
| `GET` | `/api/admin/stats` | KPIs de la plataforma | #82 |
| `GET` | `/api/admin/logs?limit=20` | Últimas N entradas de actividad del sistema (max 100) | #82 |
 
### Contrato GET /api/admin/users
 
```json
GET /api/admin/users?page=0&size=20&search=alice&role=PLAYER&active=true
Authorization: Bearer <admin-token>

→ 200
{
  "items": [
    {
      "id": "bbbb0000-0000-0000-0000-000000000002",
      "username": "alice",
      "email": "alice@versus.com",
      "role": "PLAYER",
      "isActive": true,
      "createdAt": "2025-03-15T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```
 
> Todos los query params son opcionales. `search` hace coincidencia parcial case-insensitive sobre username y email.  
> `role` debe ser uno de `PLAYER`, `MODERATOR`, `ADMIN`. Resultados ordenados por `createdAt` descendente.  
> Errores: `401` token inválido · `403` usuario no es ADMIN.
 
### Contrato PUT /api/admin/users/{id}/role
 
```json
PUT /api/admin/users/bbbb0000-0000-0000-0000-000000000002/role
Authorization: Bearer <admin-token>
Content-Type: application/json

{ "role": "MODERATOR" }

→ 200
{
  "id": "bbbb0000-0000-0000-0000-000000000002",
  "username": "alice",
  "email": "alice@versus.com",
  "role": "MODERATOR",
  "isActive": true,
  "createdAt": "2025-03-15T10:00:00Z"
}
```
 
> Errores: `400 VALIDATION_ERROR` si el target es el propio admin autenticado (no permite self-demotion) o si `role` es nulo/inválido · `404 NOT_FOUND` si el usuario no existe · `401` · `403`.
 
### Contrato PUT /api/admin/users/{id}/status
 
```json
PUT /api/admin/users/bbbb0000-0000-0000-0000-000000000002/status
Authorization: Bearer <admin-token>
Content-Type: application/json

{ "active": false }

→ 200
{
  "id": "bbbb0000-0000-0000-0000-000000000002",
  "username": "alice",
  "email": "alice@versus.com",
  "role": "PLAYER",
  "isActive": false,
  "createdAt": "2025-03-15T10:00:00Z"
}
```
 
> Enviar `active: true` reactiva una cuenta suspendida. El backend rechaza `active: null` con `400`.  
> Errores: `400 VALIDATION_ERROR` si el target es el propio admin autenticado (no permite self-suspend) · `404 NOT_FOUND` · `401` · `403`.
 
### Contrato GET /api/admin/stats
 
```json
GET /api/admin/stats
Authorization: Bearer <admin-token>

→ 200
{
  "totalUsers": 342,
  "activeUsers": 289,
  "matchesToday": 17,
  "totalQuestions": 1240,
  "activeSpiders": 1,
  "pendingReports": 5
}
```
 
> Todos los valores son `long`. `matchesToday` cuenta partidas cuyo `createdAt` es posterior al inicio del día UTC actual.  
> `activeSpiders` cuenta spiders con estado `RUNNING`. `pendingReports` cuenta reportes con estado `PENDING`.  
> Errores: `401` · `403`.
 
### Contrato GET /api/admin/logs
 
```json
GET /api/admin/logs?limit=20
Authorization: Bearer <admin-token>

→ 200
[
  {
    "ts": "2025-04-18T14:32:00Z",
    "level": "ERR",
    "message": "Spider run finished with 4 errors, 10 questions inserted"
  },
  {
    "ts": "2025-04-18T13:10:00Z",
    "level": "INFO",
    "message": "New user registered: player99"
  },
  {
    "ts": "2025-04-18T12:55:00Z",
    "level": "INFO",
    "message": "Question report submitted: texto incorrecto"
  }
]
```
 
> `limit` por defecto es 20; el máximo aceptado es 100 (valores superiores se clampean).  
> Resultados ordenados por `ts` descendente (más reciente primero).
 
| `level` | Condición |
|---------|-----------|
| `INFO`  | Spider run sin errores, registro de usuario, reporte de pregunta |
| `WARN`  | Spider run con 1 o 2 errores |
| `ERR`   | Spider run con 3 o más errores |
 
> Fuentes agregadas: ejecuciones de spiders (`startedAt`), registros de usuarios (`createdAt`), reportes de preguntas (`createdAt`).  
> Errores: `401` · `403`.
 
---
 
## 🎓 Módulo 9 — PRÁCTICA (Modo libre)
 
Modo sin presión para explorar el banco de preguntas y aprender sin consecuencias. No crea partida ni actualiza stats.
 
### Características
 
- Sin sesión de partida — sin `sessionId`
- La respuesta correcta siempre se revela tras contestar
- Muestra `explanation` si la pregunta la tiene
- Contadores de sesión en cliente (racha, precisión media), sin persistir
- Accesible sin autenticación a nivel de API (el frontend lo mantiene bajo `authGuard`)
 
### Endpoints
 
| Método | Ruta | Auth | Descripción |
|--------|------|------|-------------|
| `POST` | `/api/practice/answer` | No (público) | Evalúa respuesta y devuelve la correcta + explicación |
 
**Request BINARY:**
```json
{
  "questionId": "uuid-pregunta",
  "optionId": "uuid-opcion-elegida"
}
```
 
**Request NUMERIC:**
```json
{
  "questionId": "uuid-pregunta",
  "value": 650000000
}
```
 
**Response:**
```json
{
  "correct": true,
  "correctOptionId": "uuid-opcion-correcta",
  "correctValue": null,
  "deviationPercent": null,
  "unit": null,
  "explanation": "Texto explicativo (null si no existe)"
}
```
 
> Los campos `null` se omiten en el JSON. Para BINARY solo aparecen `correct`, `correctOptionId` y `explanation`. Para NUMERIC aparecen `correct`, `correctValue`, `deviationPercent`, `unit` y `explanation`.
 
### Ruta frontend
 
`/play/practice` — bajo `authGuard`. El selector de modos incluye una carta "PRÁCTICA" con color `--vs-accent-green`.  
Usa `QuestionService.random(type?, category?)` y `QuestionService.categories()` existentes.
 
### Fórmula de evaluación NUMERIC
 
Idéntica a Modo Precisión:
```
deviationPercent = |value - correctValue| / |correctValue| * 100
correct          = deviationPercent ≤ tolerancePercent (default 5%)
```
 
---
 
## 🔗 Orden de implementación recomendado
 
Para evitar bloqueos, seguir este orden. Cada fase desbloquea la siguiente.
 
```
FASE 1 — Base (sin esto nada funciona)
  ✦ #41 Modelo de preguntas (DB)
  ✦ #39 Middleware JWT y Roles (Backend)
  ✦ #84 Login (Backend)
  ✦ #40 Guard + AuthInterceptor (Frontend)
  ✦ #44 Seed inicial de preguntas
 
FASE 2 — Juego en solitario
  ✦ #42 Endpoint pregunta aleatoria
  ✦ #53 Lógica partida binaria individual
  ✦ #55 Lógica de vidas
  ✦ #56 Endpoints partida binaria
  ✦ #57 Vista juego Supervivencia (Frontend)
  ✦ #59 Algoritmo daño/curación por precisión
  ✦ #60 Endpoints partida numérica
  ✦ #61 Vista juego Precisión (Frontend)
 
FASE 3 — Multijugador
  ✦ #63 WebSockets Backend
  ✦ #64 WebSockets Frontend
  ✦ #65 Sistema de salas
  ✦ #66 Matchmaking
  ✦ #67 Sincronización de preguntas
  ✦ #68 Lógica duelo binario
  ✦ #71 Lógica duelo de precisión
  ✦ #73 Lógica modo Versus (Sabotaje)
 
FASE 4 — Stats, ranking y scraping
  ✦ #76 Historial de partidas
  ✦ #77 Estadísticas personales
  ✦ #78 Ranking global
  ✦ #45 Pipeline Scrapy → PostgreSQL
  ✦ #46–#50 Spiders individuales
 
FASE 5 — Moderación y admin
  ✦ #80 Reporte de preguntas
  ✦ #81 Panel de moderador
  ✦ #82 Panel de administrador
```
 
---
 
## 🤝 Normas de coordinación
 
### Contrato entre backend y frontend
 
- El backend define el contrato (URL, método, request, response) en este documento **antes** de implementarlo.
- El frontend **no espera** a que el backend esté listo: usa **datos mock** con la misma estructura del contrato.
- Si el contrato cambia, se actualiza este documento y se avisa al equipo.

### Códigos de error estándar
 
Todos los errores siguen este formato:
 
```json
{
  "error": "UNAUTHORIZED",
  "message": "Token expirado o inválido",
  "status": 401
}
```
 
| Código | Cuándo usarlo |
|--------|---------------|
| `400` | Request malformado o datos inválidos |
| `401` | No autenticado |
| `403` | Autenticado pero sin permisos |
| `404` | Recurso no encontrado |
| `409` | Conflicto (usuario ya existe, sala llena...) |
| `500` | Error interno del servidor |
 
### Variables de entorno
 
```bash
# Backend (application.properties / .env)
DB_URL=jdbc:postgresql://localhost:5432/versus
DB_USER=versus
DB_PASS=versus
JWT_SECRET=cambiame_en_produccion
JWT_EXPIRY=900          # 15 minutos
JWT_REFRESH_EXPIRY=604800  # 7 días
 
# Frontend (environment.ts)
API_URL=http://localhost:8080/api
WS_URL=ws://localhost:8080/ws
```
 
---
 
*Última actualización: 23-04-2026 — Si modificas algo del contrato de API, actualiza este documento.*
 
