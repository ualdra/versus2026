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
 
El proyecto se divide en **8 módulos**. Cada issue pertenece a uno.
 
```
┌─────────────────────────────────────────────────────────┐
│                        VERSUS                           │
│                                                         │
│  [AUTH]  [USERS]  [QUESTIONS]  [GAME]  [MATCH]         │
│  [STATS]  [SCRAPING]  [ADMIN]                          │
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
 
| Método | Ruta | Descripción | Issues |
|--------|------|-------------|--------|
| `GET` | `/api/questions/random` | Pregunta aleatoria (opcionalmente por categoría o tipo) | #42 |
| `GET` | `/api/questions/random?type=BINARY&category=football` | Filtros opcionales | #42, #43 |
| `GET` | `/api/questions/:id` | Pregunta por ID | #41 |
| `GET` | `/api/questions/categories` | Lista de categorías disponibles | #43 |
 
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
  "gameOver": false
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
  "gameOver": false
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
Frontend conecta a:  ws://localhost:8080/ws
 
Suscripciones del cliente:
  /user/queue/match          → eventos de la partida (respuestas, vidas, resultado)
  /topic/match/{matchId}     → estado compartido de la sala
 
Envíos del cliente:
  /app/match/answer          → enviar respuesta
  /app/match/ready           → confirmar que está listo para empezar
```
 
### Flujo de sala de espera → partida
 
```
1. POST /api/match/queue        → Entrar en cola de matchmaking
2. Backend empareja dos jugadores → emite evento "MATCH_FOUND"
3. Frontend redirige a /sala/:matchId
4. Cada jugador envía /app/match/ready
5. Cuando ambos están listos → emite "MATCH_START" con 1ª pregunta
6. Cada pregunta → jugadores responden → backend procesa → emite resultado
7. Al terminar → emite "MATCH_END" con ganador y stats
```
 
### Endpoints REST de sala (previos a la partida)
 
| Método | Ruta | Descripción | Issues |
|--------|------|-------------|--------|
| `POST` | `/api/match/queue` | Entrar en cola de matchmaking | #66 |
| `DELETE` | `/api/match/queue` | Salir de la cola | #66 |
| `POST` | `/api/match/room` | Crear sala privada con código | #65 |
| `POST` | `/api/match/room/join` | Unirse a sala privada por código | #65 |
| `GET` | `/api/match/:matchId` | Estado actual de una sala | #65 |
 
### Eventos WebSocket (backend → frontend)
 
| Evento | Canal | Payload |
|--------|-------|---------|
| `MATCH_FOUND` | `/user/queue/match` | `{ matchId, opponent }` |
| `MATCH_START` | `/topic/match/{id}` | `{ question, mode }` |
| `QUESTION` | `/topic/match/{id}` | `{ question, timeLimit }` |
| `ROUND_RESULT` | `/topic/match/{id}` | `{ player1Lives, player2Lives, correct }` |
| `MATCH_END` | `/topic/match/{id}` | `{ winner, stats }` |
 
### Lógica de daño por modo
 
| Modo | Quién pierde vida | Cuándo |
|------|-------------------|--------|
| Duelo binario | El que falla | Al responder |
| Duelo de precisión | El que se desvía más | Al responder ambos |
| Sabotaje | El rival | Cuando el otro acierta mejor |
 
> El algoritmo de daño al rival (Sabotaje) se implementa en #74.
 
---
 
## 📊 Módulo 6 — STATS & RANKING
> Issues: #54, #76, #77, #78, #79
 
Historial de partidas, estadísticas personales y ranking global.
 
### Endpoints
 
| Método | Ruta | Descripción | Issues |
|--------|------|-------------|--------|
| `GET` | `/api/stats/me` | Estadísticas del usuario autenticado | #77 |
| `GET` | `/api/stats/me?mode=SURVIVAL` | Filtradas por modo | #77 |
| `GET` | `/api/stats/me/history` | Historial de partidas | #76 |
| `GET` | `/api/ranking/:mode` | Top 100 de un modo | #78 |
| `GET` | `/api/ranking/:mode/me` | Posición propia en el ranking | #78 |
 
### Contrato de stats
 
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
  "avgDeviation": null
}
```
 
> `avgDeviation` solo aplica a modos numéricos (PRECISION, PRECISION_DUEL).
 
---
 
## 🕷️ Módulo 7 — SCRAPING
> Issues: #45, #46, #47, #48, #49, #50, #51, #52, #97, #98
 
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
 
## 🛡️ Módulo 8 — ADMIN & MODERACIÓN
> Issues: #80, #81, #82
 
Gestión de preguntas reportadas y administración de la plataforma.
 
### Endpoints de moderación
 
| Método | Ruta | Descripción | Issues |
|--------|------|-------------|--------|
| `POST` | `/api/questions/:id/report` | Reportar una pregunta | #80 |
| `GET` | `/api/mod/reports` | Lista de reportes pendientes (MODERATOR+) | #81 |
| `PUT` | `/api/mod/reports/:id` | Resolver reporte (DISMISS / DEACTIVATE) | #81 |
 
### Endpoints de administración
 
| Método | Ruta | Descripción | Issues |
|--------|------|-------------|--------|
| `GET` | `/api/admin/users` | Lista de usuarios | #82 |
| `PUT` | `/api/admin/users/:id/role` | Cambiar rol de usuario | #82 |
| `DELETE` | `/api/admin/users/:id` | Eliminar usuario | #82 |
| `PUT` | `/api/admin/questions/:id/status` | Activar/desactivar pregunta | #82 |
 
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
 