# Pipeline de Scraping — Scrapy → PostgreSQL

> Documentación de la integración entre el scraper Scrapy y la base de datos PostgreSQL implementada en la issue #97.
> Cubre: arquitectura del pipeline, contrato `QuestionItem`, deduplicación, endpoints de gestión y cómo añadir un spider nuevo.

---

## Visión general

```
Spider (Scrapy)
    │  yield QuestionItem(...)
    ▼
DeerdaysScraperPipeline
    ├── Valida calidad mínima
    ├── Deduplicación por SHA-256 del texto
    ├── INSERT en questions + question_options
    └── UPDATE spider_runs (questionsInserted, errors, finishedAt)

Backend (Spring Boot)
    ├── POST /api/admin/spiders/{name}/run  →  ProcessBuilder("scrapy crawl {name}")
    └── GET  /api/admin/spiders/{name}/runs →  historial de ejecuciones
```

Las preguntas insertadas quedan en `status = PENDING_REVIEW`. Un moderador las activa desde el panel de administración (issue #81).

---

## Contrato QuestionItem

`scraper/versus_scraper/items.py`

| Campo | Tipo Python | Obligatorio | Descripción |
|---|---|---|---|
| `text` | `str` | ✅ | Texto completo de la pregunta. Base del hash de deduplicación. |
| `type` | `str` | ✅ | `"BINARY"` o `"NUMERIC"` |
| `category` | `str` | ✅ | Categoría libre: `"RRSS"`, `"FOOTBALL"`, `"GEO"`, etc. |
| `source_url` | `str` | — | URL de origen |
| `correct_value` | `float` | Si `NUMERIC` | Valor numérico correcto |
| `unit` | `str` | Si `NUMERIC` | Unidad legible: `"suscriptores"`, `"goles"`, `"km"`, etc. |
| `tolerance_percent` | `float` | — | Margen de error (%). Default `5`. |
| `options` | `list[dict]` | Si `BINARY` | Lista de `{"text": str, "is_correct": bool}`. Al menos una opción debe ser correcta. |

### Ejemplo NUMERIC

```python
yield QuestionItem(
    text="¿Cuántos suscriptores tiene Ibai en YouTube?",
    type="NUMERIC",
    category="RRSS",
    source_url=response.url,
    correct_value=12_500_000,
    unit="suscriptores",
    tolerance_percent=10,
)
```

### Ejemplo BINARY

```python
yield QuestionItem(
    text="¿El Real Madrid ganó la Champions de 2022?",
    type="BINARY",
    category="FOOTBALL",
    source_url=response.url,
    options=[
        {"text": "Sí", "is_correct": True},
        {"text": "No", "is_correct": False},
    ],
)
```

---

## Pipeline — `DeerdaysScraperPipeline`

`scraper/versus_scraper/pipelines.py`

### Ciclo de vida

| Método | Cuándo se llama | Qué hace |
|---|---|---|
| `open_spider` | Al arrancar el spider | Abre conexión psycopg2, busca el `spider_id` por nombre, crea entrada en `spider_runs`, marca `spiders.status = RUNNING` |
| `process_item` | Por cada item yielded | Valida, deduplica, inserta en `questions` + `question_options` |
| `close_spider` | Al terminar el spider | Actualiza `spider_runs` con `finishedAt`, `questionsInserted`, `errors`; marca `spiders.status = IDLE` |

### Deduplicación

Antes de insertar, se calcula `SHA-256(text)` y se comprueba en `questions.text_hash`. Si ya existe, el item se descarta silenciosamente (no cuenta como error). Esto garantiza idempotencia: ejecutar el mismo spider dos veces no duplica preguntas.

### Validación de calidad mínima

| Tipo | Condición de rechazo |
|---|---|
| `NUMERIC` | `correct_value` es `None` o `≤ 0` |
| `BINARY` | Ninguna opción tiene `is_correct=True` |

Los items rechazados incrementan el contador `errors` del run.

### Items que no son QuestionItem

Los items de otro tipo (p. ej. `SocialMediaCreatorItem`) se devuelven sin tocar. El pipeline no los procesa.

---

## Variables de entorno

Configurables en `scraper/versus_scraper/settings.py` y sobreescribibles desde el entorno Docker:

| Variable | Default | Descripción |
|---|---|---|
| `DB_HOST` | `localhost` | Host de PostgreSQL |
| `DB_PORT` | `5432` | Puerto |
| `DB_NAME` | `versus` | Nombre de la base de datos |
| `DB_USER` | `versus` | Usuario |
| `DB_PASSWORD` | `versus` | Contraseña |

---

## Endpoints de gestión (solo ADMIN)

> Contrato actualizado respecto al borrador inicial: se usa `{name}` (nombre del spider) en lugar de `{id}`.

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/admin/spiders` | Lista todos los spiders con estado actual y datos del último run |
| `POST` | `/api/admin/spiders/{name}/run` | Lanza el spider por nombre. Devuelve 202 con el `SpiderRun` creado. 404 si no existe, 409 si ya está en ejecución |
| `GET` | `/api/admin/spiders/{name}/runs` | Historial de runs del spider ordenados por fecha descendente |

### Respuesta `SpiderResponse`

```json
{
  "id": "uuid",
  "name": "rrss",
  "targetUrl": "https://socialblade.com/...",
  "status": "IDLE",
  "lastRunAt": "2026-05-07T10:00:00Z",
  "lastRun": {
    "id": "uuid",
    "startedAt": "2026-05-07T10:00:00Z",
    "finishedAt": "2026-05-07T10:05:00Z",
    "questionsInserted": 42,
    "errors": 3
  }
}
```

`lastRun` es `null` si el spider nunca ha sido ejecutado.

### Respuesta `SpiderRunResponse`

```json
{
  "id": "uuid",
  "startedAt": "2026-05-07T10:00:00Z",
  "finishedAt": "2026-05-07T10:05:00Z",
  "questionsInserted": 42,
  "errors": 3
}
```

`finishedAt` es `null` mientras el proceso sigue activo.

---

## Cómo lanza el backend el proceso Scrapy

`SpiderService.triggerRun(name)` actualiza el estado a `RUNNING`, crea el `SpiderRun` y llama a `launchProcess` en un hilo separado (`@Async`). Así el endpoint devuelve 202 inmediatamente.

`launchProcess` usa `ProcessBuilder`:

```
scrapy crawl {name}
```

ejecutado en el directorio configurado por la propiedad `scraper.working-dir` (por defecto `../scraper`, sobreescribible con la variable de entorno `SCRAPER_DIR`).

Al terminar el proceso:
- Exit code `0` → `Spider.status = IDLE`
- Cualquier otro exit code o `IOException` → `Spider.status = FAILED`

En ambos casos `SpiderRun.finishedAt` se establece en el bloque `finally`.

> **Importante:** `@Async` requiere que `launchProcess` sea invocado desde fuera del bean (a través del proxy de Spring). Si en el futuro se refactoriza el servicio, asegurarse de que esta llamada no se produce por auto-invocación.

---

## Cómo añadir un spider nuevo (checklist para issue #98)

1. Crear el spider en `scraper/versus_scraper/spiders/<nombre>_spider.py` siguiendo el patrón de `rrss_spider.py`.
2. Hacer que cada fila yielde un `QuestionItem` con todos los campos obligatorios según el tipo (`NUMERIC` o `BINARY`).
3. Insertar una fila en la tabla `spiders` con `name = '<nombre>'` y `status = 'IDLE'` (puede hacerse en el seed de dev).
4. Verificar que el pipeline inserta correctamente: `scrapy crawl <nombre>` desde el directorio `scraper/`.
5. Disparar desde el panel de admin: `POST /api/admin/spiders/<nombre>/run`.

El pipeline ya maneja la conexión a BD, la deduplicación y el registro del run — el spider solo tiene que centrarse en extraer datos y mapearlos a `QuestionItem`.

---

## Cambios al esquema DB introducidos por esta issue

| Tabla | Cambio | Motivo |
|---|---|---|
| `questions` | Nuevo campo `text_hash VARCHAR(64) UNIQUE` | Deduplicación idempotente por hash SHA-256 del texto |

Ver esquema actualizado en [`docs/bd-scheme.md`](bd-scheme.md).
