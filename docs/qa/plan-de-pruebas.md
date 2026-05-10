# Plan de pruebas por funcionalidad

Referencia de pruebas manuales y automatizadas por módulo. Marca cada caso con ✅ al verificarlo.

## Convención de estado

| Estado | Significado |
|---|---|
| ✅ | Verificado (test automatizado o manual documentado) |
| 🔲 | Pendiente de verificar |
| 🚧 | Funcionalidad no implementada aún |
| ❌ | Fallo conocido (con issue abierto) |

---

## Módulo: Autenticación

### Registro

| # | Caso de prueba | Tipo | Estado |
|---|---|---|---|
| A1 | Registro exitoso con datos válidos devuelve 201 + tokens | Integración | 🔲 |
| A2 | Email ya registrado devuelve 409 CONFLICT | Integración | 🔲 |
| A3 | Contraseñas no coinciden devuelve 400 VALIDATION_ERROR | Integración | 🔲 |
| A4 | Email con formato inválido devuelve 400 | Integración | 🔲 |
| A5 | Formulario de registro muestra error cuando las contraseñas no coinciden | Unitario (frontend) | 🔲 |

### Login

| # | Caso de prueba | Tipo | Estado |
|---|---|---|---|
| A6 | Login con credenciales válidas devuelve 200 + tokens + user | Integración | 🔲 |
| A7 | Login con contraseña incorrecta devuelve 401 | Integración | 🔲 |
| A8 | Login con email no registrado devuelve 401 | Integración | 🔲 |
| A9 | El `accessToken` recibido es un JWT válido con `sub=userId` | Unitario (backend) | 🔲 |

### Refresh y sesión

| # | Caso de prueba | Tipo | Estado |
|---|---|---|---|
| A10 | Refresh con token válido devuelve nuevos tokens | Integración | 🔲 |
| A11 | Refresh con token expirado devuelve 401 | Integración | 🔲 |
| A12 | Petición a ruta protegida con access token expirado → interceptor refresca y reintenta | Unitario (frontend) | 🔲 |
| A13 | Logout invalida el refresh token (uso posterior devuelve 401) | Integración | 🔲 |

---

## Módulo: Usuarios

| # | Caso de prueba | Tipo | Estado |
|---|---|---|---|
| U1 | GET /api/users/me devuelve perfil del usuario autenticado | Integración | 🔲 |
| U2 | PUT /api/users/me actualiza username | Integración | 🔲 |
| U3 | PUT /api/users/me con username ya en uso devuelve 409 | Integración | 🔲 |
| U4 | GET /api/users/{id} devuelve perfil público (sin email) | Integración | 🔲 |
| U5 | GET /api/users/{id} con id inexistente devuelve 404 | Integración | 🔲 |

---

## Módulo: Preguntas

| # | Caso de prueba | Tipo | Estado |
|---|---|---|---|
| Q1 | GET /api/questions/random devuelve una pregunta BINARY o NUMERIC | Integración | 🔲 |
| Q2 | La respuesta de pregunta BINARY incluye `options` pero NO `isCorrect` | Integración | 🔲 |
| Q3 | La respuesta de pregunta NUMERIC incluye `unit` pero NO `correctValue` | Integración | 🔲 |
| Q4 | GET /api/questions/random?type=BINARY devuelve solo preguntas BINARY | Integración | 🔲 |
| Q5 | Solo se sirven preguntas en estado ACTIVE (no INACTIVE, PENDING_REVIEW, FLAGGED) | Unitario (backend) | 🔲 |
| Q6 | GET /api/questions/categories devuelve lista no vacía | Integración | 🔲 |

---

## Módulo: Juego Survival

| # | Caso de prueba | Tipo | Estado |
|---|---|---|---|
| G1 | POST /api/game/survival/start devuelve sessionId, question y livesRemaining=3 | Integración | 🔲 |
| G2 | Respuesta correcta devuelve correct=true y no resta vidas | Integración | 🔲 |
| G3 | Respuesta incorrecta resta una vida | Integración | 🔲 |
| G4 | Racha de 3 correctas aplica bonus de puntuación | Unitario (backend) | 🔲 |
| G5 | Tercera respuesta incorrecta devuelve gameOver=true y sin nextQuestion | Integración | 🔲 |
| G6 | Answer con sessionId inválido devuelve 404 | Integración | 🔲 |
| G7 | El componente Survival muestra la animación `animate-wrong` en respuesta incorrecta | Unitario (frontend) | 🔲 |

---

## Módulo: Juego Precision

| # | Caso de prueba | Tipo | Estado |
|---|---|---|---|
| P1 | POST /api/game/precision/start devuelve livesRemaining=100 | Integración | 🔲 |
| P2 | Respuesta exacta (deviation=0%) devuelve lifeDelta=0 | Unitario (backend) | 🔲 |
| P3 | Respuesta con 50% de desviación aplica fórmula correctamente | Unitario (backend) | 🔲 |
| P4 | Vida llega a 0 → gameOver=true | Integración | 🔲 |
| P5 | La barra de vida del frontend cambia de color según el nivel (verde→amarillo→rojo) | Unitario (frontend) | 🔲 |

---

## Módulo: Estadísticas

| # | Caso de prueba | Tipo | Estado |
|---|---|---|---|
| S1 | GET /api/stats/me devuelve estadísticas del usuario autenticado | Integración | 🔲 |
| S2 | GET /api/stats/me?mode=SURVIVAL devuelve solo stats de ese modo | Integración | 🔲 |
| S3 | Win rate se calcula correctamente (gamesWon/gamesPlayed * 100) | Unitario (backend) | 🔲 |
| S4 | Las stats se actualizan tras terminar una partida | Integración | 🔲 |

---

## Módulo: Moderación

| # | Caso de prueba | Tipo | Estado |
|---|---|---|---|
| M1 | POST /api/questions/{id}/report crea reporte con status=PENDING | Integración | 🔲 |
| M2 | Una pregunta con 5 reportes pasa automáticamente a FLAGGED | Unitario (backend) | 🔲 |
| M3 | GET /api/moderation/reports requiere rol MODERATOR o ADMIN | Integración | 🔲 |
| M4 | PUT /api/moderation/reports/{id}/resolve con DISMISS cambia status a DISMISSED | Integración | 🔲 |
| M5 | PUT con DELETE_QUESTION pasa la pregunta a INACTIVE | Integración | 🔲 |
| M6 | PLAYER intentando resolver reporte recibe 403 FORBIDDEN | Integración | 🔲 |

---

## Módulo: WebSocket / Multiplayer (Sprint 3)

| # | Caso de prueba | Tipo | Estado |
|---|---|---|---|
| W1 | Conexión STOMP con JWT válido se establece correctamente | Integración | 🚧 |
| W2 | Conexión sin JWT o con JWT inválido es rechazada | Integración | 🚧 |
| W3 | Ambos jugadores reciben ROUND_START al mismo tiempo | E2E | 🚧 |
| W4 | GAME_OVER se envía a ambos jugadores al terminar la partida | E2E | 🚧 |

---

## Módulo: Media / Avatares

| # | Caso de prueba | Tipo | Estado |
|---|---|---|---|
| AV1 | POST /api/media/upload acepta imagen ≤10MB | Integración | 🔲 |
| AV2 | POST /api/media/upload rechaza fichero >10MB con 400 | Integración | 🔲 |
| AV3 | GET /api/media/{id} devuelve 404 para asset que no existe | Integración | 🔲 |
| AV4 | DELETE /api/media/{id} de un asset ajeno devuelve 403 | Integración | 🔲 |

---

## Flujos E2E críticos (futuro — Playwright)

| Flujo | Pasos | Estado |
|---|---|---|
| Registro completo | Registro → login automático → dashboard con stats a 0 | 🚧 |
| Partida Survival completa | Login → Survival → 3 preguntas → resultado | 🚧 |
| Partida PvP | 2 usuarios → lobby → partida → resultado | 🚧 |
| Reporte y resolución | Login player → reportar pregunta → login admin → resolver | 🚧 |
