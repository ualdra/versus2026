# Estrategia de QA

## Pirámide de testing

```
         ┌───────────────┐
         │     E2E        │  pocos, flujos críticos
         ├───────────────┤
         │  Integración   │  API contracts + DB
         ├───────────────┤
         │   Unitarios    │  lógica de negocio, guards, validaciones
         └───────────────┘
```

La mayor parte de la cobertura viene de tests unitarios (rápidos, sin infraestructura). Los tests de integración validan los contratos de API. Los E2E se limitan a los flujos de usuario más críticos.

## Niveles de testing

### 1. Tests unitarios

| Servicio | Herramienta | Qué cubre |
|---|---|---|
| Frontend (`core/`) | Vitest + Angular TestBed | Guards, interceptors, servicios, validaciones |
| Backend (`*Service`) | JUnit 5 + Mockito | Lógica de negocio, cálculos de scoring, reglas de negocio |

El backend no debe hacer llamadas reales a la BD en tests unitarios — los repositorios se mockean con Mockito.

### 2. Tests de integración

| Capa | Herramienta | Qué cubre |
|---|---|---|
| Backend API | Spring Boot Test + `@SpringBootTest` + Testcontainers | Endpoints REST completos contra una BD real (contenedor temporal) |
| WebSocket | `StompClient` en test | Conexión STOMP, auth JWT, flujo básico de eventos |

Los tests de integración validan que los contratos de `guia-de-coordinación-técnica.md` se cumplen exactamente (códigos HTTP, forma del JSON, errores esperados).

### 3. Tests E2E (futuro)

Los E2E se implementarán con **Playwright** cuando el Sprint 3 esté completo. Flujos prioritarios:

1. Registro → login → jugar una partida Survival → ver resultado
2. Login como admin → resolver un reporte de pregunta
3. Login → unirse a una partida PvP → completarla

## Qué se valida en cada PR

Antes de hacer merge de cualquier PR:

- [ ] `ng test --watch=false` sin fallos (frontend)
- [ ] `./mvnw test` sin fallos (backend)
- [ ] Ningún endpoint devuelve una forma de respuesta diferente a la documentada en `guia-de-coordinación-técnica.md`
- [ ] Los errores usan el envelope estándar `{ error, message, status }`
- [ ] No se expone `correctValue` ni `isCorrect` en respuestas de `/api/questions/**`

## Datos de prueba

El perfil `dev` de Spring Boot activa `DevSeedConfig`, que inserta datos reproducibles en cada arranque con `create-drop`. Esto garantiza que los tests manuales parten siempre del mismo estado.

Para tests de integración automatizados, se usa **Testcontainers** — un contenedor PostgreSQL efímero por suite de tests. No depende del estado de ninguna BD compartida.

## Criterios de aceptación por área

### Auth

| Caso | Resultado esperado |
|---|---|
| Login con credenciales válidas | 200 con `accessToken` + `refreshToken` |
| Login con contraseña incorrecta | 401 `{ error: "UNAUTHORIZED" }` |
| Registro con email duplicado | 409 `{ error: "CONFLICT" }` |
| Petición sin token a ruta protegida | 401 |
| Petición con token expirado tras refresh exitoso | 200 (reintento automático) |

### Juego Survival

| Caso | Resultado esperado |
|---|---|
| Respuesta correcta | `correct: true`, `lifeDelta: 0`, `streak++` |
| Respuesta incorrecta | `correct: false`, `livesRemaining--` |
| 3 vidas perdidas | `gameOver: true`, sin `nextQuestion` |
| Respuesta con `sessionId` inválido | 404 `NOT_FOUND` |

### Juego Precision

| Caso | Resultado esperado |
|---|---|
| Respuesta exacta (deviation = 0%) | `lifeDelta: 0` |
| Respuesta con 10% de desviación | `lifeDelta` según fórmula sprint-2 |
| Vida llega a 0 | `gameOver: true` |

### Moderación

| Caso | Resultado esperado |
|---|---|
| Reporte de pregunta | 201, `status: PENDING` |
| 5 reportes en la misma pregunta | Pregunta pasa a `FLAGGED` automáticamente |
| Resolve como DISMISS | `status: DISMISSED`, pregunta sigue `ACTIVE` |
| Resolve como DELETE_QUESTION | Pregunta pasa a `INACTIVE` |

## Gestión de bugs

1. Abre un issue en GitHub con etiqueta `bug`.
2. Describe los pasos para reproducirlo, el comportamiento esperado y el observado.
3. Adjunta logs o capturas si aplica.
4. Si el bug es crítico (pérdida de datos, seguridad), añade también la etiqueta `priority:critical`.
5. El fix se hace en rama `fix/#<issue>-<descripcion>` y pasa por PR normal.
