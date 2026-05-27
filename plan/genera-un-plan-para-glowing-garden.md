# Plan: Sustituir preguntas de ejemplo por cartas scrapeadas

## Contexto

Los modos Supervivencia y Precisión usan actualmente preguntas de ejemplo persistidas en la tabla `questions`. El scraper ya ha producido un dataset normalizado en [scraper/output/normalized/all.json](scraper/output/normalized/all.json) con **4 405 ítems** distribuidos en **23 subcategorías** (categorías como "Bota de oro", "Goleadores de la Copa Mundial", "Famosos en Redes", "Datos Globales", etc.).

Cada ítem tiene la forma:
```json
{ "categoria": "Bota de oro", "subcategoria": "Bota de oro",
  "carta": "Eusébio ( S. L. Benfica ) en 1967-68", "valor": 42 }
```

**Objetivo:** persistir esos ítems como "cartas" en BD, y generar las preguntas dinámicamente al iniciar cada ronda:
- **Supervivencia (BINARY):** dos cartas distintas de la misma `categoria`+`subcategoria`, el jugador elige cuál tiene el valor más alto.
- **Precisión (NUMERIC):** una sola carta, el jugador estima su valor.

Para subcategorías de tipo *ranking* (donde 1 es mejor que 200), la pregunta se invierte ("¿cuál tiene MENOR valor?").

---

## Enfoque

1. **Nueva tabla `cards`** como fuente canónica (independiente de `questions`).
2. **Endpoint admin** `POST /api/admin/cards/import` que lee el JSON, deduplica por hash y persiste.
3. **Refactor de `GameService`**: deja de consultar `questions`, genera DTOs `QuestionResponse` al vuelo a partir de cartas. Estado de la ronda actual se guarda en `MatchPlayer`.
4. **UI Supervivencia**: usar el componente [compare-card](frontend/src/app/features/survival/components/compare-card/compare-card.ts) (ya existe pero no se usa) para mostrar las dos cartas lado a lado.
5. **Tabla `questions` actual** queda obsoleta para los modos single-player (no se borra; quedará disponible si más adelante se quiere mezclar contenido editorial).

---

## Cambios en BD

### Nueva tabla `cards`
Persistida vía JPA en una nueva entidad `com.versus.api.cards.domain.Card`:

| Columna | Tipo | Notas |
|---|---|---|
| `id` | UUID PK | `@UuidGenerator` |
| `categoria` | VARCHAR(64) NOT NULL | |
| `subcategoria` | VARCHAR(128) NOT NULL | |
| `nombre` | TEXT NOT NULL | El campo `carta` del JSON |
| `valor` | NUMERIC(20,4) NOT NULL | |
| `unidad` | VARCHAR(32) | Derivada de `subcategoria` |
| `is_inverse` | BOOLEAN NOT NULL DEFAULT false | true para subcats tipo ranking |
| `eligible_for_survival` | BOOLEAN NOT NULL DEFAULT true | false para subcats heterogéneas |
| `source_url` | VARCHAR(1024) NULL | |
| `scraped_at` | TIMESTAMP NULL | |
| `status` | VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' | ACTIVE / ARCHIVED |
| `text_hash` | VARCHAR(64) UNIQUE NOT NULL | SHA-256 de `categoria|subcategoria|nombre` |

Índices: `(categoria, subcategoria, status)`, `(status, eligible_for_survival)`.

### Modificaciones a `MatchPlayer` ([backend/src/main/java/com/versus/api/match/domain/MatchPlayer.java])
Tres columnas nuevas para el estado de la ronda en curso (se rellenan al servir una pregunta, se validan en `answerXxx`):

```java
@Column(name = "current_card_a_id")   private UUID currentCardAId;
@Column(name = "current_card_b_id")   private UUID currentCardBId;   // null en Precisión
@Column(name = "current_round_token") private UUID currentRoundToken; // se envía como QuestionResponse.id
```

### Modificaciones a `MatchRound` ([backend/src/main/java/com/versus/api/match/domain/MatchRound.java])
- `question_id` pasa a `nullable = true` (las rondas nuevas ya no apuntan a `questions`).
- Añadir `card_a_id UUID` y `card_b_id UUID` (B nullable; null en Precisión).

---

## Backend — archivos a crear

### Módulo `com.versus.api.cards`
- **`domain/Card.java`** — entidad JPA descrita arriba.
- **`CardStatus.java`** — enum `ACTIVE`, `ARCHIVED`.
- **`repo/CardRepository.java`** con consultas nativas (Postgres `random()` ya usado en `QuestionRepository`):
  - `findRandomActiveCard()` — para Precisión.
  - `findRandomActivePairForSurvival()` — devuelve 2 cartas distintas de la misma `(categoria, subcategoria)` con `valor` distinto y `eligible_for_survival = true`. Implementación: 1ª consulta elige carta aleatoria elegible; 2ª consulta elige otra del mismo par `(categoria, subcategoria)` con valor distinto.
  - `findById(UUID)` — para validar el optionId enviado en `answer`.
- **`CardService.java`**:
  - `getRandomCard(): Card`
  - `getRandomPairForSurvival(): CardPair` (record `{Card a, Card b}` — se garantiza que `a.valor != b.valor`).
- **`CardController.java`** con `POST /api/admin/cards/import` (rol `ADMIN`, ver patrón en `SpiderController`).
- **`CardImportService.java`** — lee el JSON, deduplica, deriva unidades y `is_inverse`, persiste. Idempotente.
- **`dto/CardImportResponse.java`** — record `{ totalRead, inserted, skippedDuplicates, skippedFiltered, errors[] }`.

### Configuración
- `application.properties`: nueva propiedad `versus.cards.json-path` (default `scraper/output/normalized/all.json`, relativa al working dir del backend).

### Refactor de `GameService` ([backend/src/main/java/com/versus/api/game/GameService.java])

Sustituir las dependencias `QuestionService` + `QuestionRepository` por `CardService` + `CardRepository` para los flujos single-player.

**`startSurvival(userId)`**:
1. Crea `Match` + `MatchPlayer` (como ahora, lives = 3).
2. Llama `cards.getRandomPairForSurvival()` → `(cardA, cardB)`.
3. Genera `roundToken = UUID.randomUUID()`.
4. Guarda en MatchPlayer: `currentCardAId = cardA.id`, `currentCardBId = cardB.id`, `currentRoundToken = roundToken`.
5. Devuelve `StartGameResponse(matchId, buildBinaryResponse(roundToken, cardA, cardB))`.

**`answerSurvival(userId, request)`**:
1. Carga sesión + `MatchPlayer`.
2. Valida `request.questionId == player.currentRoundToken` (anti-replay).
3. Carga `cardA`, `cardB` por los ids guardados.
4. Valida `request.optionId ∈ {cardA.id, cardB.id}`.
5. Determina ganadora: `is_inverse ? min(valor) : max(valor)` (`is_inverse` es coherente dentro de un par porque ambas cartas vienen de la misma subcat).
6. `correct = request.optionId == ganadora.id`.
7. Actualiza streak/score/lives (lógica actual sin cambios).
8. Persiste `MatchRound { matchId, roundNumber, cardAId, cardBId, questionId = null }` y `MatchAnswer { roundId, userId, answerGiven = optionId.toString(), lifeDelta, isCorrect = correct }`.
9. Si no es game over: genera nuevo par, actualiza `currentCardAId/B/Token`, incluye `nextQuestion` en la respuesta.
10. Devuelve `SurvivalAnswerResponse` (estructura igual).

**`startPrecision(userId)` y `answerPrecision`**: paralelo, pero con una sola carta:
- `currentCardBId` queda null.
- `text = "¿Cuál es el valor de " + card.nombre + "?"`, `unit = card.unidad`.
- En `answer`, se carga la carta, se calcula desviación contra `card.valor` con la **fórmula y tolerancia actuales** ([GameService.java:128-150](backend/src/main/java/com/versus/api/game/GameService.java#L128-L150)) — se reutiliza tal cual.

### Construcción de DTOs (helpers privados en `GameService` o en `CardService`)
```java
QuestionBinaryResponse buildBinaryResponse(UUID roundToken, Card a, Card b) {
    String text = (a.isInverse())
        ? "¿Cuál tiene el valor MÁS BAJO?"
        : "¿Cuál tiene el valor MÁS ALTO?";
    String category = a.getCategoria() + " · " + a.getSubcategoria();
    List<QuestionOptionResponse> options = List.of(
        new QuestionOptionResponse(a.getId(), a.getNombre()),
        new QuestionOptionResponse(b.getId(), b.getNombre())
    );
    return new QuestionBinaryResponse(roundToken, QuestionType.BINARY, text,
                                      category, options, a.getScrapedAt());
}
```

Para soportar el `compare-card` del front, ampliar `QuestionOptionResponse` con metadatos opcionales (o crear un DTO específico `CompareOptionResponse`):
```java
record QuestionOptionResponse(UUID id, String text, String sub, String unit) { }
```
- `sub` = posible contexto extra extraído de `nombre` (no necesario en MVP — dejar `null`).
- `unit` = `card.unidad`.
- El **valor numérico NO se incluye** en el response (se revela en frontend con animación tras la respuesta — pero como el backend devuelve `correct: boolean` y nada más, el front anima desde el lado server-driven; alternativa: añadir `revealedValueA/B` en `SurvivalAnswerResponse` para que el front lo pinte). Recomendado: añadir `revealedValues: { cardId → numeric }` a `SurvivalAnswerResponse`.

---

## Reglas de derivación

### Mapeo `subcategoria → unidad`
Implementado como `Map<String,String>` constante en `CardImportService`:

| subcategoria | unidad |
|---|---|
| `Bota de oro`, `Goleadores de la Copa Mundial`, `Goles` | `goles` |
| `Partidos`, `Partidos Jugados` | `partidos` |
| `Promedio`, `Promedio de Goles` | `goles/partido` |
| `beneficio` | `USD` |
| `TikTok`, `YouTube`, `Twitch` | `seguidores` |
| `Población en 2026` | `habitantes` |
| `Agua usada al año` | `m³/año` |
| `Copa de la Liga`, `Copa del Rey`, `La Liga`, `Supercopa de España`, `Total Nacionales` | `títulos` |
| `ranking`, `Posicion`, `Ranking en Población en 2026` | `puesto` |
| (resto / `Datos Globales`) | `null` |

### Subcategorías con `is_inverse = true`
- `ranking` (Beneficios de peliculas)
- `Posicion` (Maximos Goleadores)
- `Ranking en Población en 2026` (Datos Globales)

### Subcategorías con `eligible_for_survival = false`
- `Datos Globales / Datos Globales` (mezcla heterogénea: casos COVID + población global etc.; no son comparables entre sí). Sí elegibles para Precisión.

Las cartas se cargan **todas** como `ACTIVE`; los flags determinan en qué modo aparecen y cómo se interpretan.

### Hash de deduplicación
`text_hash = SHA-256(categoria + "|" + subcategoria + "|" + nombre)` — re-importar el mismo JSON no duplica.

---

## Frontend — cambios

### Modelos ([frontend/src/app/core/models/game.models.ts])
Extender `QuestionOption`:
```ts
export interface QuestionOption {
  id: string;
  text: string;
  sub?: string | null;
  unit?: string | null;
}
```
Extender `SurvivalAnswerResponse` con `revealedValues?: Record<string, number>` (cardId → valor) para animar la revelación tras responder.

### Página Supervivencia
- [features/survival/pages/survival/survival.html](frontend/src/app/features/survival/pages/survival/survival.html#L46-L57): reemplazar el `for` de `<button>` por dos `<app-compare-card>`:
  ```html
  <div class="vs-survival__arena vs-compare-arena">
    <app-compare-card
      [item]="cardItem(q.options[0])"
      [state]="cardState(q.options[0].id)"
      [revealed]="phase() !== 'idle'"
      position="left"
      (picked)="pick(q.options[0].id)" />
    <app-compare-card
      [item]="cardItem(q.options[1])"
      [state]="cardState(q.options[1].id)"
      [revealed]="phase() !== 'idle'"
      position="right"
      (picked)="pick(q.options[1].id)" />
  </div>
  ```
- [features/survival/pages/survival/survival.ts](frontend/src/app/features/survival/pages/survival/survival.ts):
  - Importar `CompareCardComponent`.
  - Añadir `cardItem(opt: QuestionOption): CardItem` que mapea opción → `{label: opt.text, sub: opt.sub ?? '', value: this.revealedValues()[opt.id] ?? 0, unit: opt.unit ?? '', cat: q.category, stub: ''}`.
  - Añadir `cardState(optId)` que devuelve `'correct' | 'wrong' | 'idle'` según el resultado de la última respuesta.
  - Guardar `revealedValues` desde `SurvivalAnswerResponse` en una signal local.

### Página Precisión
Sin cambios estructurales: el `text` que devuelve el backend ya incluye el nombre de la carta y `unit` se sigue mostrando como ahora.

---

## Endpoint `POST /api/admin/cards/import`

- **Auth:** rol `ADMIN` (consistente con [SpiderController](backend/src/main/java/com/versus/api/scraping/SpiderController.java)).
- **Request body** (todos opcionales):
  ```json
  { "path": "scraper/output/normalized/all.json", "purgeFirst": false }
  ```
- **Response 200:**
  ```json
  { "totalRead": 4405, "inserted": 4392, "skippedDuplicates": 13,
    "skippedFiltered": 0, "errors": [] }
  ```
- **Lógica de `CardImportService.importFrom(path, purgeFirst)`:**
  1. Si `purgeFirst`: `cardRepo.deleteAll()`.
  2. Lee el JSON con `ObjectMapper` (Jackson ya está en classpath).
  3. Por cada ítem: valida no-null en categoria/subcategoria/carta/valor; deriva `unidad`, `isInverse`, `eligibleForSurvival`; calcula `textHash`.
  4. Si `cardRepo.existsByTextHash(hash)` → contar como duplicado.
  5. Si no, `cardRepo.save(card)` con `status = ACTIVE`, `scrapedAt = Instant.now()`.
  6. Devuelve el resumen.

---

## Archivos críticos

### A crear (backend)
- `backend/src/main/java/com/versus/api/cards/CardController.java`
- `backend/src/main/java/com/versus/api/cards/CardImportService.java`
- `backend/src/main/java/com/versus/api/cards/CardService.java`
- `backend/src/main/java/com/versus/api/cards/CardStatus.java`
- `backend/src/main/java/com/versus/api/cards/domain/Card.java`
- `backend/src/main/java/com/versus/api/cards/repo/CardRepository.java`
- `backend/src/main/java/com/versus/api/cards/dto/CardImportRequest.java`
- `backend/src/main/java/com/versus/api/cards/dto/CardImportResponse.java`

### A modificar
- [backend/src/main/java/com/versus/api/game/GameService.java](backend/src/main/java/com/versus/api/game/GameService.java) — sustituir lógica de `start*/answer*` por flujo basado en cartas.
- [backend/src/main/java/com/versus/api/game/dto/SurvivalAnswerResponse.java](backend/src/main/java/com/versus/api/game/dto/SurvivalAnswerResponse.java) — añadir `revealedValues`.
- [backend/src/main/java/com/versus/api/match/domain/MatchPlayer.java](backend/src/main/java/com/versus/api/match/domain/MatchPlayer.java) — añadir `currentCardAId`, `currentCardBId`, `currentRoundToken`.
- [backend/src/main/java/com/versus/api/match/domain/MatchRound.java](backend/src/main/java/com/versus/api/match/domain/MatchRound.java) — `questionId` nullable, añadir `cardAId`, `cardBId`.
- [backend/src/main/java/com/versus/api/questions/dto/QuestionOptionResponse.java](backend/src/main/java/com/versus/api/questions/dto/QuestionOptionResponse.java) — añadir `sub`, `unit` opcionales.
- [backend/src/main/resources/application.properties](backend/src/main/resources/application.properties) — añadir `versus.cards.json-path`.
- [frontend/src/app/core/models/game.models.ts](frontend/src/app/core/models/game.models.ts) — extender `QuestionOption` y `SurvivalAnswerResponse`.
- [frontend/src/app/features/survival/pages/survival/survival.html](frontend/src/app/features/survival/pages/survival/survival.html) — usar `<app-compare-card>`.
- [frontend/src/app/features/survival/pages/survival/survival.ts](frontend/src/app/features/survival/pages/survival/survival.ts) — importar componente, helpers `cardItem`, `cardState`, signal `revealedValues`.

### Sin cambios (verificados existentes y reutilizables)
- [SurvivalController](backend/src/main/java/com/versus/api/game/SurvivalController.java), [PrecisionController](backend/src/main/java/com/versus/api/game/PrecisionController.java) — solo delegan a `GameService`.
- [QuestionType](backend/src/main/java/com/versus/api/questions/QuestionType.java) — se reutilizan `BINARY` y `NUMERIC`.
- `QuestionBinaryResponse` / `QuestionNumericResponse` DTOs — se reutilizan tal cual.
- Frontend [GameService](frontend/src/app/core/services/game.service.ts) — los endpoints no cambian de URL ni de forma del request principal.

---

## Orden de implementación

1. **Backend datos**: `Card` entity + repo + status enum + `CardImportService` + endpoint. **Verificación**: hacer un curl al endpoint y comprobar conteos.
2. **Backend modelos de ronda**: ampliar `MatchPlayer` y `MatchRound`.
3. **Backend lógica**: refactorizar `GameService` (Supervivencia primero, luego Precisión). **Verificación**: tests unitarios del flujo `start → answer → next`.
4. **DTOs ampliados**: `SurvivalAnswerResponse.revealedValues`, `QuestionOptionResponse.sub/unit`.
5. **Frontend modelos** + **Survival UI** con `compare-card`.
6. **Smoke test e2e**.

---

## Verificación

### Backend (sin frontend)
1. `cd backend && ./mvnw spring-boot:run`
2. Login con un usuario admin: `POST /api/auth/login` → token.
3. `curl -X POST http://localhost:8080/api/admin/cards/import -H "Authorization: Bearer $TOKEN"` → comprobar `inserted ≈ 4400, skippedDuplicates = 0` la primera vez.
4. Re-ejecutar el mismo curl → `inserted = 0, skippedDuplicates ≈ 4400` (idempotencia).
5. `POST /api/game/survival/start` → respuesta debe traer 2 opciones con texto distinto y `category` con formato `"<cat> · <subcat>"`.
6. `POST /api/game/survival/answer` con el `optionId` y `questionId` del paso anterior → debe devolver `correct: bool` coherente; el `nextQuestion.options` deben venir de la misma o distinta subcat pero internamente consistentes.
7. Repetir con `/api/game/precision/start` y `/api/game/precision/answer` enviando un `value` cercano al real (verificar que se conoce el valor mediante la BD: `SELECT valor FROM cards WHERE id = ?`).
8. Verificar que para una subcat de ranking (p.ej. `ranking` de películas) el `text` devuelto contiene "MÁS BAJO".

### Frontend
1. `cd frontend && npm start`.
2. Login → `/play/survival` → ver dos tarjetas lado a lado renderizadas con compare-card.
3. Hacer click en una → ver animación correct/wrong; el valor se revela en ambas tarjetas.
4. Pulsar "SIGUIENTE" → nuevo par.
5. Tras 3 fallos → game over y vuelta a mode-select.
6. `/play/precision` → ver pregunta con nombre de carta y unidad; introducir valor; ver desviación coherente.

### Consultas SQL de smoke
```sql
SELECT subcategoria, COUNT(*) FROM cards WHERE status='ACTIVE' GROUP BY subcategoria ORDER BY 2 DESC;
SELECT subcategoria, COUNT(*) FROM cards WHERE is_inverse=true GROUP BY subcategoria;
SELECT subcategoria, COUNT(*) FROM cards WHERE eligible_for_survival=false GROUP BY subcategoria;
```

---

## Notas y trade-offs

- **Tolerancia en Precisión**: se mantiene el 5% por defecto. Para subcats con valores enormes (población, beneficios) o muy pequeños (promedios decimales) el 5% puede ser demasiado estricto o demasiado laxo. Fuera del scope; ajustar en tarea posterior por subcategoría.
- **Estado de ronda en `MatchPlayer`**: alternativa más sucia sería pasar los `cardAId/cardBId` por la API; preferimos guardarlo en el servidor para no exponer datos que el cliente no necesita y blindar contra manipulación.
- **Cartas con valor duplicado** dentro de una subcat: `findRandomActivePairForSurvival` reintenta hasta encontrar valores distintos (límite de reintentos = 5, si falla devuelve 409 y el frontend reintenta `start`). Casos detectados en la muestra (e.g., dos años con 40 goles en Bota de oro): manejable.
- **`Datos Globales / Datos Globales`** (13 ítems heterogéneos): se cargan con `eligible_for_survival = false`, solo aparecen en Precisión.
- **Tabla `questions` actual**: queda en la BD sin uso para los modos refactorizados. No se borra para no perder datos de ejemplo que puedan haber servido para tests. Se podría archivar (`status = ARCHIVED`) en una tarea de housekeeping posterior.
