# Spiders — Referencia

Cada spider extrae datos de una fuente específica. Las columnas **Item type** e **Integrada con BD** indican si los datos fluyen por el pipeline de PostgreSQL o se exportan directamente a archivo.

| Spider | Nombre Scrapy | Fuente | Item type | Integrada con BD |
|---|---|---|---|---|
| `rrss_spider.py` | `rrss` | socialblade.com | `SocialMediaCreatorItem` | No |
| `beneficiosPeliculas_spider.py` | `boxoffice_mojo_worldwide` | boxofficemojo.com | dict raw | No |
| `botaOro_spider.py` | `bota_de_oro` | es.wikipedia.org | dict raw | No |
| `datosMundiales_spider.py` | `worldometers` | worldometers.info | `WorldStatsItem` / `CountryPopulationItem` / … | No |
| `goleadorCopaMundial_spider.py` | `goleadores` | es.wikipedia.org | dict raw | No |
| `goleador_spider.py` | `goleadores_historia` | es.wikipedia.org | dict raw | No |
| `palmares_spider.py` | `palmares_clubes` | es.wikipedia.org | dict raw | No |
| `trofeoZarra_spider.py` | `trofeo_zarra` | es.wikipedia.org | dict raw | No |

> **Nota:** Ninguna spider genera todavía `QuestionItem` con formato listo para la BD. El paso de convertir datos crudos en preguntas de juego está pendiente de implementación (Sprint 4).

---

## Detalle por spider

### `rrss_spider.py` — Redes Sociales

**Nombre Scrapy:** `rrss`  
**Fuente:** [socialblade.com](https://socialblade.com)  
**Propósito:** Obtener el top 100 de creadores por seguidores en YouTube, TikTok y Twitch.

**Campos extraídos:**

| Campo | Descripción |
|---|---|
| `name` | Nombre del canal/creador |
| `platform` | `youtube` / `tiktok` / `twitch` |
| `subscribers` | Número de seguidores/suscriptores |
| `image` | URL de la imagen de perfil |

**Observaciones:**
- Omite `robots.txt` explícitamente (`custom_settings`).
- Los datos son útiles para preguntas tipo _"¿Quién tiene más seguidores, A o B?"_ (modo Binary).

---

### `beneficiosPeliculas_spider.py` — Taquilla Mundial

**Nombre Scrapy:** `boxoffice_mojo_worldwide`  
**Fuente:** [boxofficemojo.com](https://www.boxofficemojo.com)  
**Propósito:** Recopilar las películas con mayor recaudación mundial por año (2020–2026).

**Campos extraídos:**

| Campo | Descripción |
|---|---|
| `rank` | Posición en el ranking del año |
| `release_group` | Título de la película |
| `worldwide` | Recaudación mundial en USD |
| `year` | Año del ranking |

**Observaciones:**
- Delay de descarga: 1 segundo (respeta la carga del servidor).
- Útil para preguntas de tipo _"¿Cuánto recaudó X?"_ o comparaciones de taquilla.

---

### `botaOro_spider.py` — Bota de Oro (La Liga)

**Nombre Scrapy:** `bota_de_oro`  
**Fuente:** [Wikipedia — Bota de Oro](https://es.wikipedia.org/wiki/Bota_de_Oro)  
**Propósito:** Historial completo de ganadores del trofeo Bota de Oro de La Liga española.

**Campos extraídos:**

| Campo | Descripción |
|---|---|
| `season` | Temporada (ej. `2022-23`) |
| `player` | Nombre del jugador premiado |
| `goals` | Goles marcados |

**Observaciones:**
- Usa XPath para localizar la tabla de historial en la página de Wikipedia.
- Incluye normalización de texto para eliminar espacios extra y caracteres de referencia de Wikipedia.

---

### `datosMundiales_spider.py` — Estadísticas Mundiales

**Nombre Scrapy:** `worldometers`  
**Fuente:** [worldometers.info](https://www.worldometers.info)  
**Propósito:** Obtener estadísticas globales en tiempo real (población, COVID, agua) y datos por país.

**Item types emitidos:**

| Item | Campos | Página origen |
|---|---|---|
| `WorldStatsItem` | `metric`, `value`, `source` | Portada (contadores JS) |
| `CoronavirusItem` | `metric`, `value`, `source` | `/coronavirus/` |
| `CountryPopulationItem` | `rank`, `country`, `population_2026` | `/world-population/population-by-country/` |
| `CountryWaterItem` | `country`, `yearly_water_used` | `/water/` |

**Observaciones:**
- **Requiere Playwright**: la portada renderiza contadores con JavaScript. La spider usa `scrapy-playwright` con `meta={"playwright": True}` y espera el selector del contador de población antes de parsear.
- Las subpáginas (coronavirus, países) son HTML estático y no necesitan JS.
- Esta es la spider más compleja del módulo; se debe probar con `playwright install chromium` antes de ejecutar.

---

### `goleadorCopaMundial_spider.py` — Goleadores Copa del Mundo

**Nombre Scrapy:** `goleadores`  
**Fuente:** [Wikipedia — Goleadores Copa Mundial](https://es.wikipedia.org/wiki/Anexo:Goleadores_de_la_Copa_Mundial_de_F%C3%BAtbol)  
**Propósito:** Ranking histórico de máximos goleadores en Copas del Mundo FIFA.

**Campos extraídos:**

| Campo | Descripción |
|---|---|
| `position` | Posición en el ranking |
| `player` | Nombre del jugador |
| `country` | País |
| `goals` | Total de goles en Copa del Mundo |
| `matches` | Partidos jugados |
| `average` | Promedio goles/partido |
| `world_cups` | Copas del Mundo disputadas |

**Observaciones:**
- Localiza la tabla mediante XPath buscando el encabezado de sección específico.

---

### `goleador_spider.py` — Goleadores Históricos del Fútbol

**Nombre Scrapy:** `goleadores_historia`  
**Fuente:** [Wikipedia — Goleador (fútbol)](https://es.wikipedia.org/wiki/Goleador_(f%C3%BAtbol))  
**Propósito:** Lista de los máximos goleadores de toda la historia del fútbol profesional.

**Campos extraídos:**

| Campo | Descripción |
|---|---|
| `position` | Posición en el ranking |
| `player` | Nombre del jugador |
| `goals` | Total de goles en carrera |
| `matches` | Partidos jugados |
| `average` | Promedio goles/partido |

**Observaciones:**
- Limpia notas de referencia de Wikipedia (ej. `[n. 1]`) mediante regex antes de almacenar.

---

### `palmares_spider.py` — Palmarés Clubes Españoles

**Nombre Scrapy:** `palmares_clubes`  
**Fuente:** [Wikipedia — Palmarés clubes España](https://es.wikipedia.org/wiki/Anexo:Clubes_de_f%C3%BAtbol_de_Espa%C3%B1a_por_palmar%C3%A9s)  
**Propósito:** Número de títulos domésticos por club de fútbol español.

**Campos extraídos:**

| Campo | Descripción |
|---|---|
| `rank` | Posición en el ranking |
| `club` | Nombre del club |
| `LL` | Títulos de Liga (La Liga) |
| `CdR` | Copas del Rey |
| `CdL` | Copas de la Liga |
| `SdE` | Supercopas de España |
| `total` | Total de títulos domésticos |

**Observaciones:**
- Incluye helper `_to_int` para conversión segura de celdas vacías o guiones (`—`) a `0`.
- Exporta directamente a `palmares_clubes.csv` mediante `custom_settings["FEEDS"]`.

---

### `trofeoZarra_spider.py` — Trofeo Zarra

**Nombre Scrapy:** `trofeo_zarra`  
**Fuente:** [Wikipedia — Trofeo Zarra](https://es.wikipedia.org/wiki/Trofeo_Zarra)  
**Propósito:** Historial de ganadores del Trofeo Zarra (máximo goleador español en La Liga).

**Campos extraídos:**

| Campo | Descripción |
|---|---|
| `season` | Temporada |
| `player` | Jugador premiado |
| `goals` | Goles marcados |
| `matches` | Partidos jugados |
| `average` | Promedio goles/partido |

**Observaciones:**
- Maneja formato numérico español: usa coma como separador decimal (`_to_float`).
- Delay de descarga: 1 segundo.

---

## Guía para añadir una nueva spider

1. Crear `scraper/versus_scraper/spiders/mi_spider.py` con clase que extiende `scrapy.Spider`.
2. Definir `name`, `allowed_domains` y `start_urls`.
3. Decidir el **item type**:
   - Si los datos serán preguntas de juego → usar `QuestionItem` (fluye por el pipeline a PostgreSQL).
   - Si son datos de apoyo → emitir dict o un item propio y configurar `FEEDS` en `custom_settings`.
4. Registrar la spider en la tabla de la BD (`spiders`) con su nombre Scrapy, para que el panel de admin pueda activarla.
5. Documentar la spider en este archivo.
