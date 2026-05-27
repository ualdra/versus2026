# Plan: Unificar módulo de preguntas y rediseñar compare-card

## Contexto

Actualmente el backend tiene **dos sistemas paralelos** sirviendo preguntas:

1. **`Card`** (tabla `cards`) — datos realmente scrapeados (`scraper/output/normalized/*.json` → `CardImportService`). Tiene `categoria`, `subcategoria`, `nombre`, `valor`, `unidad`, `inverse`, `eligible_for_survival`. Lo consume `CardService` y solo lo usan **Supervivencia** y **Precisión singleplayer** (vía [GameService.java](backend/src/main/java/com/versus/api/game/GameService.java)).
2. **`Question` + `QuestionOption`** (tablas `questions`, `question_options`) — esquema alterno usado por [DuelOrchestrator.java](backend/src/main/java/com/versus/api/duel/DuelOrchestrator.java#L32) (Duelo Binario, Duelo Precisión y Sabotaje) vía [QuestionService.java](backend/src/main/java/com/versus/api/questions/QuestionService.java). Hoy esta tabla está prácticamente vacía porque el scraper no escribe ahí.

Consecuencias:
- Duelo binario, duelo de precisión y sabotaje no muestran preguntas scrapeadas reales.
- El compare-card de supervivencia no muestra bien la categoría/subcategoría y, en preguntas inverse (ranking), dice "¿Cuál tiene el valor MÁS BAJO?" cuando debería decir "¿Cuál tiene mejor posición en el ranking?".
- El frontend tiene un `QuestionService` ([question.service.ts](frontend/src/app/core/services/question.service.ts)) que solo el modo Practice usa, mientras el resto va por `GameService`/`DuelService`.

Objetivo: **`Card` es la única fuente de verdad** para todos los modos. Se extrae la lógica de construcción de DTOs binario/numérico a un servicio compartido (`QuestionService` backend revisado) que tanto `GameService` (singleplayer) como `DuelOrchestrator` (multiplayer) consumen. Se rediseña el compare-card y se corrige el texto para preguntas ranking. En frontend, se mantiene un único `QuestionService` que llama al endpoint unificado (cuando aplica) y un único modelo `Question`/`QuestionBinary`/`QuestionNumeric`.

---

## Cambios

### Backend

#### 1. Crear `CardQuestionFactory` (servicio compartido)

Nuevo archivo: `backend/src/main/java/com/versus/api/questions/CardQuestionFactory.java`

Centraliza la conversión `Card → QuestionBinaryResponse / QuestionNumericResponse`. Hoy esta lógica vive duplicada en [GameService.buildBinaryResponse / buildNumericResponse](backend/src/main/java/com/versus/api/game/GameService.java#L316-L332). Métodos:

- `QuestionBinaryResponse buildBinary(UUID roundToken, Card a, Card b)`
- `QuestionNumericResponse buildNumeric(UUID roundToken, Card card)`

Cambios sobre la lógica actual:
- **Categoría y subcategoría** se devuelven como campos separados (no concatenados con " · "). Requiere ampliar el DTO (ver punto 3).
- **Texto de pregunta binaria** según `inverse`:
  - `inverse = true` → `"¿Cuál tiene mejor posición en el ranking?"`
  - `inverse = false` → `"¿Cuál tiene mayor valor?"` (más natural que "MÁS ALTO")
- **Texto de pregunta numérica**: se mantiene `"¿Cuál es el valor de {nombre}?"`.
- Se devuelve `inverse` en el DTO binario para que el frontend pueda renderizar pistas visuales (icono ↑ vs trofeo).

Reusar:
- `CardService.getRandomPairForSurvival()` para pares binarios.
- `CardService.getRandomCard()` (o nuevo `getRandomCardNotEligibleForSurvival()` ya existente en [CardRepository.findRandomActiveNotEligibleForSurvival](backend/src/main/java/com/versus/api/cards/repo/CardRepository.java#L42)) para preguntas numéricas.

#### 2. Migrar `DuelOrchestrator` para que use `CardQuestionFactory`

[DuelOrchestrator.java](backend/src/main/java/com/versus/api/duel/DuelOrchestrator.java):
- Reemplazar la dependencia `QuestionService questions` por `CardService cards` + `CardQuestionFactory cardFactory`.
- En el método donde hoy llama `questions.findRandomActiveQuestion(type, null)` (buscar las llamadas en el orchestrator — están en el ciclo de scheduling de rounds): generar la pregunta del round desde `CardService` igual que [GameService.startSurvival](backend/src/main/java/com/versus/api/game/GameService.java#L60).
  - Modos `BINARY_DUEL` y `SABOTAGE` → `cards.getRandomPairForSurvival()` + `cardFactory.buildBinary(...)`.
  - Modo `PRECISION_DUEL` → `cards.getRandomCard()` + `cardFactory.buildNumeric(...)`.
- Mantener el `roundToken` (UUID) como `id` del `QuestionResponse` (igual que ya hace GameService para anti-replay).
- La validación de respuestas dentro de `DuelOrchestrator` (donde se compara la opción/valor enviada con la correcta) debe consultar `CardService.getById(...)` en lugar de `QuestionService.findActiveQuestion(...)`. Buscar la lógica de comparación de respuestas en el orchestrator/engine.

#### 3. Refactor `GameService` para usar el factory

[GameService.java](backend/src/main/java/com/versus/api/game/GameService.java):
- Eliminar `buildBinaryResponse` y `buildNumericResponse` privados.
- Inyectar `CardQuestionFactory` y delegar.
- No cambia comportamiento externo.

#### 4. Extender DTOs

[QuestionBinaryResponse.java](backend/src/main/java/com/versus/api/questions/dto/QuestionBinaryResponse.java):
- Añadir campos:
  - `String subcategory` (separado de `category`).
  - `boolean inverse` (para que el frontend muestre el indicador correcto).

[QuestionNumericResponse.java](backend/src/main/java/com/versus/api/questions/dto/QuestionNumericResponse.java):
- Añadir campo `String subcategory`.

[QuestionResponse.java](backend/src/main/java/com/versus/api/questions/dto/QuestionResponse.java) (sealed interface):
- Añadir `String subcategory()` al contrato.

#### 5. `QuestionService` (backend) — endpoint público

[QuestionService.java](backend/src/main/java/com/versus/api/questions/QuestionService.java) sigue existiendo para servir el endpoint público `GET /api/questions/random` (usado por Practice). Cambia su implementación para que **también consulte cards** vía `CardQuestionFactory`:
- `getRandom(QuestionType, category)` ahora devuelve una pregunta construida desde un `Card` aleatorio (filtrado por categoría si se especifica). Eliminamos la lectura desde la tabla `questions`.
- `getCategories()` → delegar a `CardRepository.findDistinctCategorias()`.
- `getById(UUID)` → tras el cambio los IDs son `roundToken` efímeros, así que este método se elimina o se reemplaza por `getCardById` interno. **Verificar si lo consume PracticeService**: si sí, hay que adaptar PracticeService para que reciba el `cardId` directamente.

> **Nota sobre Practice**: revisar [PracticeService.java](backend/src/main/java/com/versus/api/practice/PracticeService.java) en la implementación. Si valida respuestas contra `Question.options[].isCorrect`, hay que cambiarlo a comparar contra `Card.valor` (igual que survival/precision). Está fuera del scope estricto del request del usuario pero es necesario para no romper Practice.

### Frontend

#### 6. Modelo `Question` — añadir campos

[game.models.ts](frontend/src/app/core/models/game.models.ts):

```ts
export interface QuestionBinary {
  id: string;
  type: 'BINARY';
  text: string;
  category: string;
  subcategory: string;     // NUEVO
  inverse: boolean;        // NUEVO
  options: QuestionOption[];
  scrapedAt: string | null;
}

export interface QuestionNumeric {
  id: string;
  type: 'NUMERIC';
  text: string;
  category: string;
  subcategory: string;     // NUEVO
  unit: string | null;
  scrapedAt: string | null;
}
```

`QuestionOption.sub` y `QuestionOption.unit` se mantienen — el backend ya los rellena desde `Card.unidad`.

#### 7. `QuestionService` (frontend) — punto único declarado

[question.service.ts](frontend/src/app/core/services/question.service.ts) ya existe y centraliza la API `/api/questions/random`, `/{id}`, `/categories`. **No se inventa un nuevo servicio**; se documenta su rol explícitamente:

- Añadir métodos semánticos para clarificar uso desde game modes (aunque internamente el backend ya devuelve la pregunta dentro de `StartGameResponse`/`QuestionPayload`):
  - `randomBinary(category?: string): Observable<QuestionBinary>` → wrapper de `random('BINARY', category)`.
  - `randomNumeric(category?: string): Observable<QuestionNumeric>` → wrapper de `random('NUMERIC', category)`.
- Ningún modo de juego activo lo llamará directamente (las preguntas vienen embebidas en las respuestas de `GameService` y los eventos del `DuelService`), pero queda como **único punto público para "pedir una pregunta suelta"** (usado por Practice y disponible para nuevos modos).

> No se modifican `GameService` ni `DuelService` para que llamen a `QuestionService` — eso introduciría una llamada HTTP extra. El requisito del usuario "que todos los modos usen el mismo servicio de preguntas" se cumple **en el backend** (todos pasan por `CardQuestionFactory`). En el frontend, todos los modos consumen el mismo modelo `Question` que viene serializado idénticamente desde cualquier endpoint.

#### 8. Rediseñar `compare-card`

Archivos:
- [compare-card.html](frontend/src/app/features/survival/components/compare-card/compare-card.html)
- [compare-card.scss](frontend/src/app/features/survival/components/compare-card/compare-card.scss)
- [compare-card.ts](frontend/src/app/features/survival/components/compare-card/compare-card.ts) — extender `CardItem`:
  ```ts
  export interface CardItem {
    label: string;
    sub: string;
    value: number;
    unit: string;
    cat: string;
    subcat: string;   // NUEVO
    stub: string;
  }
  ```

Cambios visuales:
- **Eliminar** `vs-compare__placeholder` + `vs-img-stub` (no aporta — son rayas decorativas).
- **Mover categoría y subcategoría arriba**, con tipografía grande:
  - `vs-compare__category` → font-size 18px, color `--vs-accent-gold`, Bebas Neue.
  - Añadir `vs-compare__subcategory` → font-size 14px, letter-spacing 0.12em, color `--vs-text-secondary`.
- **Nombre del item** (label) en el centro: pasar de 32px a 36-40px, peso 700, Bebas Neue (vs-display).
- **Sub** del item: subir de 12px a 14px y mejorar contraste.
- **CTA**: eliminar el "↑ MÁS / ↓ MENOS" hardcodeado. El indicador queda solo en la pregunta principal (que sí indica claramente "¿cuál tiene mayor valor?" o "¿cuál tiene mejor posición en el ranking?"). El card pasa a mostrar solo nombre + sub + (al revelar) el valor.
- Padding/spacing más generoso entre bloques.

#### 9. Ajustar plantillas que usan compare-card y muestran categoría

[survival.html](frontend/src/app/features/survival/pages/survival/survival.html):
- Sustituir línea 42 (`<div class="vs-q-label">CATEGORÍA · {{ q.category | uppercase }}</div>`) por un bloque más grande que muestre categoría y subcategoría:
  ```html
  <div class="vs-q-meta">
    <div class="vs-q-meta__cat">{{ q.category | uppercase }}</div>
    <div class="vs-q-meta__subcat">{{ q.subcategory | uppercase }}</div>
  </div>
  <h2 class="vs-q-text vs-display">{{ q.text }}</h2>
  ```
- Pasar `subcat` en `cardItem()` ([survival.ts:60-70](frontend/src/app/features/survival/pages/survival/survival.ts#L60-L70)).

[binary-duel.html](frontend/src/app/features/binary-duel/pages/binary-duel/binary-duel.html#L62-L65) y [sabotage.html](frontend/src/app/features/sabotage/pages/sabotage/sabotage.html#L72-L76):
- Mismo cambio: categoría + subcategoría en grande encima del texto de pregunta.

[precision.html](frontend/src/app/features/precision/pages/precision/precision.html#L27-L33) y `precision-duel.html`:
- Mismo cambio para mantener consistencia (aunque allí no hay compare-card, sí se muestra la pregunta).

#### 10. CSS — añadir estilos `vs-q-meta`

Definir clases utilitarias en `styles.scss` (o en cada componente):

```scss
.vs-q-meta {
  display: flex; flex-direction: column; align-items: center; gap: 4px; margin-bottom: 12px;
  &__cat {
    font-family: 'Bebas Neue', sans-serif;
    font-size: 28px; letter-spacing: 0.18em;
    color: var(--vs-accent-gold);
  }
  &__subcat {
    font-family: 'Inter', sans-serif;
    font-size: 14px; letter-spacing: 0.14em; text-transform: uppercase;
    color: var(--vs-text-secondary);
  }
}
```

---

## Archivos críticos a modificar

**Backend** (Java):
- `backend/src/main/java/com/versus/api/questions/CardQuestionFactory.java` *(nuevo)*
- [backend/src/main/java/com/versus/api/game/GameService.java](backend/src/main/java/com/versus/api/game/GameService.java) — usar factory, eliminar duplicación
- [backend/src/main/java/com/versus/api/duel/DuelOrchestrator.java](backend/src/main/java/com/versus/api/duel/DuelOrchestrator.java) — sustituir `QuestionService` por `CardService` + factory
- [backend/src/main/java/com/versus/api/questions/QuestionService.java](backend/src/main/java/com/versus/api/questions/QuestionService.java) — leer desde cards
- [backend/src/main/java/com/versus/api/questions/dto/QuestionResponse.java](backend/src/main/java/com/versus/api/questions/dto/QuestionResponse.java) — añadir `subcategory`
- [backend/src/main/java/com/versus/api/questions/dto/QuestionBinaryResponse.java](backend/src/main/java/com/versus/api/questions/dto/QuestionBinaryResponse.java) — añadir `subcategory`, `inverse`
- [backend/src/main/java/com/versus/api/questions/dto/QuestionNumericResponse.java](backend/src/main/java/com/versus/api/questions/dto/QuestionNumericResponse.java) — añadir `subcategory`
- `backend/src/main/java/com/versus/api/practice/PracticeService.java` — adaptar si dependía de `Question`/`QuestionOption`

**Frontend** (TypeScript):
- [frontend/src/app/core/models/game.models.ts](frontend/src/app/core/models/game.models.ts) — añadir `subcategory`, `inverse`
- [frontend/src/app/core/services/question.service.ts](frontend/src/app/core/services/question.service.ts) — añadir wrappers semánticos
- [frontend/src/app/features/survival/components/compare-card/compare-card.ts](frontend/src/app/features/survival/components/compare-card/compare-card.ts) — añadir `subcat` a `CardItem`
- [frontend/src/app/features/survival/components/compare-card/compare-card.html](frontend/src/app/features/survival/components/compare-card/compare-card.html) — rediseño
- [frontend/src/app/features/survival/components/compare-card/compare-card.scss](frontend/src/app/features/survival/components/compare-card/compare-card.scss) — tipografía/spacing
- [frontend/src/app/features/survival/pages/survival/survival.ts](frontend/src/app/features/survival/pages/survival/survival.ts) — pasar `subcat` a `cardItem()`
- [frontend/src/app/features/survival/pages/survival/survival.html](frontend/src/app/features/survival/pages/survival/survival.html) — bloque `vs-q-meta`
- [frontend/src/app/features/binary-duel/pages/binary-duel/binary-duel.html](frontend/src/app/features/binary-duel/pages/binary-duel/binary-duel.html) — bloque `vs-q-meta`
- [frontend/src/app/features/sabotage/pages/sabotage/sabotage.html](frontend/src/app/features/sabotage/pages/sabotage/sabotage.html) — bloque `vs-q-meta`
- [frontend/src/app/features/precision/pages/precision/precision.html](frontend/src/app/features/precision/pages/precision/precision.html) — bloque `vs-q-meta`
- `frontend/src/app/features/precision-duel/pages/precision-duel/precision-duel.html` — bloque `vs-q-meta`
- `frontend/src/styles.scss` (o equivalente) — estilos `vs-q-meta`

**Tests a actualizar** (búsqueda inicial — confirmar en implementación):
- `backend/src/test/java/com/versus/api/it/capa4/SurvivalGameIT.java`
- `backend/src/test/java/com/versus/api/game/GameServiceTest.java`
- `backend/src/test/java/com/versus/api/it/support/Factories.java`

---

## Verificación

1. **Datos seedeados**: verificar que la tabla `cards` tiene datos (correr `CardImportService` si está vacía). Confirmar con `docker compose exec postgres psql -U versus -d versus -c "SELECT COUNT(*) FROM cards WHERE status='ACTIVE';"`.
2. **Backend tests**: `cd backend && ./mvnw test` — los tests de Survival/Precision/Duel deben seguir pasando tras actualizar mocks.
3. **Arranque del stack**: `docker compose -f docker-compose.yml -f docker-compose.dev.yml up`.
4. **Smoke manual frontend** (`http://localhost:4200`):
   - **Supervivencia**: ver categoría y subcategoría grandes arriba. En una pregunta con `inverse=true` (cartas de ranking tipo posición liga), verificar que el texto dice "¿Cuál tiene mejor posición en el ranking?". El compare-card debe verse limpio y claro.
   - **Precisión**: ver categoría + subcategoría grandes.
   - **Duelo binario**: iniciar match (puede requerir bot/segundo cliente). Verificar que las preguntas son scrapeadas reales (no placeholder/lorem), con categoría+subcategoría visibles.
   - **Sabotaje**: idem duelo binario.
   - **Duelo precisión**: idem precisión singleplayer pero multijugador.
5. **DevTools Network**: confirmar que las respuestas JSON de `/api/game/survival/start`, `/api/game/precision/start` y los eventos WebSocket `QUESTION` ahora incluyen `subcategory` e `inverse` (binario).
6. **Practice mode**: probar que sigue funcionando si se mantuvo (o documentar que se ajustó).
