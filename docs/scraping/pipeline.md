# Pipeline de Scraping — Arquitectura

## Visión general

```
Spider
  │  yield QuestionItem / dict / ItemSubclass
  ▼
DeerdaysScraperPipeline          ← solo procesa QuestionItem
  ├── Deduplicación (SHA-256)
  ├── Validación por tipo (NUMERIC / BINARY)
  └── Inserción en PostgreSQL
        ├── questions
        └── question_options
```

Los items que **no** son `QuestionItem` salen directamente del pipeline sin procesarse; Scrapy los exporta según el `FEEDS` configurado en cada spider (JSON, CSV…).

---

## Items definidos (`items.py`)

### `QuestionItem` — preguntas para el juego

El único item que persiste en la base de datos. Todos los campos que la BD necesita deben venir rellenos desde la spider.

| Campo | Tipo | Descripción |
|---|---|---|
| `question_text` | str | Enunciado de la pregunta |
| `question_type` | str | `NUMERIC` o `BINARY` |
| `correct_value` | float \| None | Respuesta numérica correcta (solo NUMERIC) |
| `unit` | str \| None | Unidad del valor (ej. `"km"`, `"goles"`) |
| `tolerance` | float \| None | Margen de error aceptable (solo NUMERIC) |
| `options` | list[dict] \| None | Lista de opciones para BINARY |
| `source_url` | str | URL de origen del dato |
| `difficulty` | str | `EASY`, `MEDIUM` o `HARD` |
| `category` | str | Categoría temática (ej. `"futbol"`, `"cine"`) |

**Estructura de una opción BINARY:**
```python
{"text": "Lionel Messi", "is_correct": True}
{"text": "Cristiano Ronaldo", "is_correct": False}
```

### Otros item types (sin persistencia en BD)

| Item | Campos principales | Spider que lo usa |
|---|---|---|
| `SocialMediaCreatorItem` | `name`, `platform`, `subscribers`, `image` | `rrss` |
| `WorldStatsItem` | `metric`, `value`, `source` | `worldometers` |
| `CoronavirusItem` | `metric`, `value`, `source` | `worldometers` |
| `CountryPopulationItem` | `rank`, `country`, `population_2026` | `worldometers` |
| `CountryWaterItem` | `country`, `yearly_water_used` | `worldometers` |

---

## `DeerdaysScraperPipeline` (`pipelines.py`)

### Ciclo de vida

```
open_spider()
  ├── Conecta a PostgreSQL (psycopg2)
  ├── Resuelve spider_id por nombre en tabla `spiders`
  └── Crea registro en `spider_runs` con status RUNNING

process_item(item)           ← llamado por cada item yieldeado
  ├── Si no es QuestionItem → return item (pasa de largo)
  ├── Deduplicación: hash SHA-256 del question_text
  │     └── Si ya existe en BD → DropItem
  ├── Validación:
  │     ├── NUMERIC: correct_value no puede ser None
  │     └── BINARY: al menos una opción con is_correct=True
  ├── INSERT en `questions` con status = 'PENDING_REVIEW'
  └── INSERT en `question_options` (solo BINARY)

close_spider()
  └── Actualiza `spider_runs`: finish_time, questions_scraped, errors_count
      y resetea status de spider a IDLE
```

### Deduplicación

Se calcula `SHA-256(question_text.strip().lower())` y se guarda en un set en memoria (`seen_hashes`). Antes de insertar se comprueba también en BD para resistir reinicios. Esto hace que los reruns de una spider sean **idempotentes**: no generan duplicados.

### Estados de una pregunta tras el scraping

```
PENDING_REVIEW  ← estado inicial; visible en el panel de moderación
APPROVED        ← moderador aprueba; entra en rotación de juego
REJECTED        ← moderador rechaza; no se usa
```

---

## Configuración (`settings.py`)

| Parámetro | Valor | Descripción |
|---|---|---|
| `BOT_NAME` | `versus_scraper` | Identificador del bot |
| `USER_AGENT` | Chrome 91 / Win10 | Cabecera User-Agent enviada |
| `ROBOTSTXT_OBEY` | `False` (global) | Cada spider puede sobreescribir con `custom_settings` |
| `ITEM_PIPELINES` | `DeerdaysScraperPipeline: 300` | Prioridad 300 (menor número = mayor prioridad) |
| `ASYNCIO_EVENT_LOOP` | `AsyncioSelectorReactor` | Necesario para scrapy-playwright |
| `FEED_EXPORT_ENCODING` | `utf-8` | Codificación de archivos exportados |

**Variables de entorno para la BD:**

```
DB_HOST     (default: localhost)
DB_PORT     (default: 5432)
DB_NAME     (default: versus)
DB_USER     (default: versus)
DB_PASSWORD (default: versus)
```

---

## Tablas de BD involucradas

Ver esquema completo en [`docs/bd-scheme.md`](../bd-scheme.md).

### `spiders`
Registro de cada spider conocida. La columna `name` debe coincidir exactamente con el atributo `name` de la clase Scrapy.

| Columna | Tipo | Descripción |
|---|---|---|
| `id` | UUID | PK |
| `name` | varchar | Nombre Scrapy de la spider |
| `status` | enum | `IDLE` / `RUNNING` / `FAILED` |
| `last_run` | timestamp | Última ejecución |

### `spider_runs`
Historial de ejecuciones con métricas.

| Columna | Tipo | Descripción |
|---|---|---|
| `id` | UUID | PK |
| `spider_id` | UUID | FK → spiders |
| `start_time` | timestamp | Inicio de la ejecución |
| `finish_time` | timestamp | Fin (null mientras RUNNING) |
| `questions_scraped` | int | Preguntas insertadas en este run |
| `errors_count` | int | Errores de validación/pipeline |

---

## Integración con el panel de administración (Sprint 4)

El panel admin lanzará spiders a través de la API REST:

```
POST /api/admin/spiders/{name}/run   → dispara la spider remotamente
GET  /api/admin/spiders              → lista spiders y su estado
GET  /api/admin/spiders/{id}/runs    → historial de ejecuciones
```

Estos endpoints están documentados en [`docs/guia-de-coordinación-técnica.md`](../guia-de-coordinación-técnica.md) pero aún no están implementados.

---

## Cómo convertir datos crudos en `QuestionItem`

La mayoría de spiders actuales extraen datos crudos (nombres, cifras). Para que esos datos generen preguntas de juego hay que añadir una capa de transformación dentro de la spider o en un middleware.

**Ejemplo — convertir un goleador en pregunta NUMERIC:**

```python
from versus_scraper.items import QuestionItem

def parse_row(self, row):
    yield QuestionItem(
        question_text=f"¿Cuántos goles marcó {row['player']} en su carrera?",
        question_type="NUMERIC",
        correct_value=float(row["goals"]),
        unit="goles",
        tolerance=0,
        options=None,
        source_url=self.start_urls[0],
        difficulty="MEDIUM",
        category="futbol",
    )
```

**Ejemplo — comparación BINARY con dos opciones:**

```python
yield QuestionItem(
    question_text="¿Quién tiene más seguidores en YouTube?",
    question_type="BINARY",
    correct_value=None,
    unit=None,
    tolerance=None,
    options=[
        {"text": creator_a["name"], "is_correct": creator_a["subscribers"] > creator_b["subscribers"]},
        {"text": creator_b["name"], "is_correct": creator_b["subscribers"] > creator_a["subscribers"]},
    ],
    source_url="https://socialblade.com",
    difficulty="EASY",
    category="rrss",
)
```
