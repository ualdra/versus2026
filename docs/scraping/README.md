# Scraping — Documentación

Esta carpeta documenta el subsistema de scraping de Versus: las spiders de Scrapy, el pipeline de preguntas, la configuración y las guías para añadir nuevas fuentes.

## Índice

| Archivo | Contenido |
|---|---|
| [spiders.md](spiders.md) | Referencia de cada spider: fuente, datos extraídos, estado de integración |
| [pipeline.md](pipeline.md) | Arquitectura del pipeline: items, validación, deduplicación, persistencia en BD |

## Estructura del módulo

```
scraper/
├── versus_scraper/
│   ├── spiders/               # Una spider por fuente de datos
│   │   ├── rrss_spider.py
│   │   ├── beneficiosPeliculas_spider.py
│   │   ├── botaOro_spider.py
│   │   ├── datosMundiales_spider.py
│   │   ├── goleadorCopaMundial_spider.py
│   │   ├── goleador_spider.py
│   │   ├── palmares_spider.py
│   │   └── trofeoZarra_spider.py
│   ├── items.py               # Definición de los item types
│   ├── pipelines.py           # Pipeline principal (dedup + inserción en PostgreSQL)
│   └── settings.py            # Configuración global de Scrapy
└── requirements.txt
```

## Ejecución rápida

```bash
# Desde scraper/
cd scraper

# Ejecutar una spider concreta
scrapy crawl rrss
scrapy crawl worldometers
scrapy crawl bota_de_oro

# Exportar a JSON sin pasar por la BD
scrapy crawl palmares_clubes -o palmares.json
```

> Las spiders que usan Playwright (`worldometers`) requieren instalar los navegadores:
> ```bash
> playwright install chromium
> ```

## Variables de entorno requeridas

La conexión a PostgreSQL se configura mediante variables de entorno (ver `.env.example`):

```
DB_HOST=localhost
DB_PORT=5432
DB_NAME=versus
DB_USER=versus
DB_PASSWORD=versus
```

## Estado de integración

Solo las spiders que emiten `QuestionItem` se persisten en la base de datos a través del pipeline. Las demás exportan a JSON/CSV directamente. Ver [spiders.md](spiders.md) para el estado de cada una.
