# Plan — Normalizador de JSON de los Scrapers (`scraper/normalize.py`)

## Context

Los 8 spiders del proyecto (`scraper/versus_scraper/spiders/`) producen JSON heterogéneos en `scraper/output/`. Cada uno tiene su propio esquema (`edicion/bota_de_oro/gol`, `release_group/worldwide`, `Jugador/G/P.J./Prom.`, etc.). Antes de poder cargar estas tarjetas en la tabla `questions` o consumirlas desde frontend/backend, hay que unificarlas en un esquema único de **4 campos**: `categoria`, `subcategoria`, `carta`, `valor`. Este plan diseña un **script Python standalone** (`scraper/normalize.py`, solo stdlib) que lee los JSONs crudos y emite los normalizados a `scraper/output/normalized/`. La integración con la BD queda fuera de alcance.

Decisiones ya confirmadas con el usuario:
- Script standalone (no pipeline de Scrapy).
- Salida: un archivo por spider + un `all.json` combinado.
- `valor` siempre numérico (`int`/`float`), nunca string.
- Métricas "home" de worldometers traducidas vía diccionario explícito (sin traducir → warning + texto en inglés).
- Claves JSON en ASCII minúsculas: `categoria`, `subcategoria`, `carta`, `valor`.

## Critical files

- **A crear:** `c:\Users\adrim\versus2026\scraper\normalize.py`
- **Entrada (read-only):** `c:\Users\adrim\versus2026\scraper\output\*.json` (8 ficheros)
- **Salida (a crear):** `c:\Users\adrim\versus2026\scraper\output\normalized\{spider}.json` + `all.json`
- **Referencia esquema BD:** `c:\Users\adrim\versus2026\docs\bd-scheme.md` (no se modifica)

No hay que tocar `pipelines.py`, `items.py`, `settings.py` ni los spiders.

## Estructura del fichero `scraper/normalize.py`

```
1. Module docstring + __future__ imports
2. Stdlib imports: json, re, sys, argparse, logging, pathlib, typing
3. Constantes:
   - DEFAULT_INPUT_DIR  = Path("scraper/output")
   - DEFAULT_OUTPUT_DIR = Path("scraper/output/normalized")
4. WORLDOMETERS_METRIC_ES (dict de traducción, ver §3)
5. Helpers: parse_int, parse_money, parse_suffix, parse_float, _warn, _emit
6. 8 normalizadores: normalize_<spider>(raw) -> list[dict]
7. Registro: SPIDER_NORMALIZERS = {"<spider>": normalize_<spider>, ...}
8. I/O: load_raw, write_normalized
9. main(argv) + __main__ guard
```

## 1. Helpers (firmas y comportamiento)

Todos devuelven `int`/`float`/`None`; `None` ⇒ `_emit` descarta + log.

```python
def parse_int(s) -> int | None
```
- Numérico → `int(s)`.
- String: regex `r"([0-9][0-9,]*)"` para coger la **primera corrida numérica con comas**, eliminar comas, `int(...)`.
- Cubre `"1,476,625,576"` → `1476625576` y `"20,280,000,000  Year: 2000"` → `20280000000` (el sufijo `Year: 2000` se ignora).
- Devuelve `None` con `""`, `"?"`, sin dígitos, o tras `ValueError`.

```python
def parse_money(s: str) -> int | None
```
- Limpia `$`, comas, espacios; delega en `parse_int`. `"$472,162,520"` → `472162520`.

```python
def parse_suffix(s: str) -> int | None
```
- Regex `r"^\s*([0-9]+(?:\.[0-9]+)?)\s*([KMB])?\s*$"`.
- Multiplicadores: `K=1_000`, `M=1_000_000`, `B=1_000_000_000`, ausente=1.
- `"20.22M"` → `20220000`; `"1.5K"` → `1500`; `"5B"` → `5_000_000_000`.

```python
def parse_float(s) -> float | None
```
- Numérico → cast directo.
- String: reemplaza `","` por `"."`, luego `float(...)`. `None` en `""`, `"?"`, `ValueError`.

```python
def _warn(msg: str) -> None
```
- Wrapper de `logging.warning` (stderr; configurado en `main`).

```python
def _emit(cards, categoria, subcategoria, carta, valor) -> None
```
- Si `valor is None` o `carta` es falsy → warning + descarta.
- En otro caso: `cards.append({"categoria": ..., "subcategoria": ..., "carta": ..., "valor": ...})`.

## 2. Normalizadores por spider

Todos: `def normalize_<spider>(raw: list[dict]) -> list[dict]`.

### 2.1 `bota_de_oro` (1 carta por fila)
- categoria = "Bota de oro"
- subcategoria = "Bota de oro"
- carta = `f"{row['bota_de_oro']} en {row['edicion']}"`
- valor = `parse_int(row['gol'])`

### 2.2 `boxoffice_mojo_worldwide` (2 cartas por fila)
- carta = `f"{row['release_group']} en {row['year']}"`
- Emite **dos** registros con esa misma `carta`:
  - `("Beneficios de peliculas", "ranking",    carta, parse_int(row['rank']))`
  - `("Beneficios de peliculas", "beneficio",  carta, parse_money(row['worldwide']))`

### 2.3 `goleadores` (1 carta por fila)
- categoria = subcategoria = "Goleadores de la Copa Mundial"
- carta = `row['Jugador']`
- valor = `parse_int(row['Goles'])`
- `Pos` no se usa.

### 2.4 `goleadores_historia` (hasta 4 cartas por fila, se descartan `"?"`/`""`)
- categoria = "Maximos Goleadores"; carta = `row['Jugador']`.
- Emite (los `None` de los helpers harán que `_emit` los descarte):
  - `("Maximos Goleadores", "Posicion",          carta, parse_int(row['#']))`
  - `("Maximos Goleadores", "Goles",             carta, parse_int(row['G']))`
  - `("Maximos Goleadores", "Partidos Jugados",  carta, parse_int(row['P.J.']))`
  - `("Maximos Goleadores", "Promedio de Goles", carta, parse_float(row['Prom.']))`

### 2.5 `palmares_clubes` (5 cartas por fila)
- categoria = "Títulos de Clubes Nacionales"; carta = `row['club']`.
- Cinco emisiones:
  - `("...", "La Liga",             carta, parse_int(row['LL']))`
  - `("...", "Copa del Rey",        carta, parse_int(row['CdR']))`
  - `("...", "Copa de la Liga",     carta, parse_int(row['CdL']))`
  - `("...", "Supercopa de España", carta, parse_int(row['SdE']))`
  - `("...", "Total Nacionales",    carta, parse_int(row['total_nacionales']))`

### 2.6 `rrss` (1 carta por fila)
- categoria = "Famosos en Redes"
- subcategoria = `row['platform']` (Twitch / YouTube / TikTok)
- carta = `row['name']`
- valor = `parse_suffix(row['subscribers'])` (convierte "20.22M" → 20220000, "1.5K" → 1500, etc.)

### 2.7 `trofeo_zarra` (3 cartas por fila)
- categoria = "Trofeo Zarra"; carta = `f"{row['jugador']} en {row['temporada']}"`.
- Tres emisiones:
  - `("Trofeo Zarra", "Goles",    carta, parse_int(row['goles']))`
  - `("Trofeo Zarra", "Partidos", carta, parse_int(row['partidos']))`
  - `("Trofeo Zarra", "Promedio", carta, parse_float(row['promedio']))`

### 2.8 `worldometers` (despacho por forma de fila)
```python
for row in raw:
    if "yearly_water_used" in row:
        _emit(cards, "Datos Globales", "Agua usada al año",
              row["country"], parse_int(row["yearly_water_used"]))
    elif "population_2026" in row:
        country = row["country"]
        _emit(cards, "Datos Globales", "Población en 2026",
              country, parse_int(row["population_2026"]))
        _emit(cards, "Datos Globales", "Ranking en Población en 2026",
              country, parse_int(row["rank"]))
    elif "metric" in row and "value" in row:
        metric_en = row["metric"]
        carta = WORLDOMETERS_METRIC_ES.get(metric_en)
        if carta is None:
            _warn(f"worldometers: metric sin traducir {metric_en!r}, dejo inglés")
            carta = metric_en
        _emit(cards, "Datos Globales", "Datos Globales",
              carta, parse_int(row["value"]))
    else:
        _warn(f"worldometers: fila desconocida {row!r}")
```

Notas: `parse_int("20,280,000,000  Year: 2000")` se queda con la primera corrida `20,280,000,000` y descarta el sufijo, gracias a la regex.

## 3. Diccionario de traducción Worldometers

`WORLDOMETERS_METRIC_ES: dict[str, str]` cubre el 100 % de las métricas presentes en `worldometers.json` (líneas 378–390):

| English (clave)                              | Español (valor)                              |
|---|---|
| `Coronavirus Cases`                          | `Casos de Coronavirus`                        |
| `Current World Population`                   | `Población Global en el Mundo`                |
| `Births this year`                           | `Nacimientos este año`                        |
| `Deaths this year`                           | `Muertes este año`                            |
| `Cars produced this year`                    | `Coches producidos este año`                  |
| `Bicycles produced this year`                | `Bicicletas producidas este año`              |
| `Computers produced this year`               | `Ordenadores producidos este año`             |
| `New book titles published this year`        | `Nuevos libros publicados este año`           |
| `Overweight people in the world`             | `Personas con sobrepeso en el mundo`          |
| `Obese people in the world`                  | `Personas obesas en el mundo`                 |
| `Water used this year (million L)`           | `Agua usada este año (millones L)`            |
| `Abortions this year`                        | `Abortos este año`                            |
| `Money spent on illegal drugs this year`     | `Dinero gastado en drogas ilegales este año` |

## 4. Registro y CLI

```python
SPIDER_NORMALIZERS: dict[str, Callable[[list[dict]], list[dict]]] = {
    "bota_de_oro":              normalize_bota_de_oro,
    "boxoffice_mojo_worldwide": normalize_boxoffice_mojo_worldwide,
    "goleadores":               normalize_goleadores,
    "goleadores_historia":      normalize_goleadores_historia,
    "palmares_clubes":          normalize_palmares_clubes,
    "rrss":                     normalize_rrss,
    "trofeo_zarra":             normalize_trofeo_zarra,
    "worldometers":             normalize_worldometers,
}
```

CLI:
- `python scraper/normalize.py` → normaliza los 8 + escribe `all.json`.
- `--input-dir PATH`, `--output-dir PATH` para override.
- `--spider rrss --spider worldometers` para ejecuciones puntuales.
- Logging WARNING a stderr; resumen final a stdout.
- Idempotente: re-ejecutar reescribe ficheros.
- Tolerancia: cada fichero en try/except — un JSON inválido no tira los demás.

## 5. I/O

```python
def load_raw(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, list):
        raise ValueError(f"{path}: se esperaba array JSON, se obtuvo {type(data).__name__}")
    return data

def write_normalized(path: Path, cards: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        json.dump(cards, f, ensure_ascii=False, indent=2)
        f.write("\n")
```

UTF-8 con `ensure_ascii=False` para conservar tildes y acentos (`Eusébio`, `Müller`, `España`, `año`).

## 6. Conteos esperados (sanity check)

| Spider | filas crudas | cartas |
|---|---|---|
| bota_de_oro              | 57   | 57            |
| boxoffice_mojo_worldwide | 1399 | 2798 (×2)     |
| goleadores               | 100  | 100           |
| goleadores_historia      | 107  | ~360–428 (≤×4) |
| palmares_clubes          | 18   | 90 (×5)       |
| rrss                     | 299  | 299           |
| trofeo_zarra             | 19   | 57 (×3)       |
| worldometers             | 389  | 391 (183 agua + 195×2 población + 13 métricas) |
| **`all.json`**           |      | **~4 150**    |

Desviaciones grandes ⇒ algo va mal.

## 7. Salidas de ejemplo (deben coincidir con la spec del usuario)

`normalized/bota_de_oro.json[0]`
```json
{"categoria":"Bota de oro","subcategoria":"Bota de oro","carta":"Eusébio ( S. L. Benfica ) en 1967-68","valor":42}
```

`normalized/boxoffice_mojo_worldwide.json[0..1]`
```json
[
  {"categoria":"Beneficios de peliculas","subcategoria":"ranking","carta":"Demon Slayer: Kimetsu no Yaiba - The Movie: Mugen Train en 2020","valor":1},
  {"categoria":"Beneficios de peliculas","subcategoria":"beneficio","carta":"Demon Slayer: Kimetsu no Yaiba - The Movie: Mugen Train en 2020","valor":472162520}
]
```

`normalized/goleadores.json[0]`
```json
{"categoria":"Goleadores de la Copa Mundial","subcategoria":"Goleadores de la Copa Mundial","carta":"Miroslav Klose","valor":16}
```

`normalized/goleadores_historia.json` — Cristiano Ronaldo (4 cartas):
```json
[
  {"categoria":"Maximos Goleadores","subcategoria":"Posicion","carta":"Cristiano Ronaldo","valor":1},
  {"categoria":"Maximos Goleadores","subcategoria":"Goles","carta":"Cristiano Ronaldo","valor":971},
  {"categoria":"Maximos Goleadores","subcategoria":"Partidos Jugados","carta":"Cristiano Ronaldo","valor":1322},
  {"categoria":"Maximos Goleadores","subcategoria":"Promedio de Goles","carta":"Cristiano Ronaldo","valor":0.73}
]
```

`normalized/goleadores_historia.json` — Erwin Helmchen (`P.J.="?"`, `Prom.=""`) ⇒ solo **2 cartas**, no 4. Se logean 2 warnings de skip.

`normalized/palmares_clubes.json` — Real Madrid (5 cartas):
```json
[
  {"categoria":"Títulos de Clubes Nacionales","subcategoria":"La Liga","carta":"Real Madrid","valor":36},
  {"categoria":"Títulos de Clubes Nacionales","subcategoria":"Copa del Rey","carta":"Real Madrid","valor":20},
  {"categoria":"Títulos de Clubes Nacionales","subcategoria":"Copa de la Liga","carta":"Real Madrid","valor":1},
  {"categoria":"Títulos de Clubes Nacionales","subcategoria":"Supercopa de España","carta":"Real Madrid","valor":14},
  {"categoria":"Títulos de Clubes Nacionales","subcategoria":"Total Nacionales","carta":"Real Madrid","valor":71}
]
```

`normalized/rrss.json[0]`
```json
{"categoria":"Famosos en Redes","subcategoria":"Twitch","carta":"KaiCenat","valor":20220000}
```

`normalized/trofeo_zarra.json[0..2]`
```json
[
  {"categoria":"Trofeo Zarra","subcategoria":"Goles","carta":"David Villa en 2005-06","valor":25},
  {"categoria":"Trofeo Zarra","subcategoria":"Partidos","carta":"David Villa en 2005-06","valor":37},
  {"categoria":"Trofeo Zarra","subcategoria":"Promedio","carta":"David Villa en 2005-06","valor":0.68}
]
```

`normalized/worldometers.json` — un ejemplo de cada rama:
```json
[
  {"categoria":"Datos Globales","subcategoria":"Agua usada al año","carta":"Afghanistan","valor":20280000000},
  {"categoria":"Datos Globales","subcategoria":"Población en 2026","carta":"India","valor":1476625576},
  {"categoria":"Datos Globales","subcategoria":"Ranking en Población en 2026","carta":"India","valor":1},
  {"categoria":"Datos Globales","subcategoria":"Datos Globales","carta":"Casos de Coronavirus","valor":704753890},
  {"categoria":"Datos Globales","subcategoria":"Datos Globales","carta":"Población Global en el Mundo","valor":8294270995}
]
```

## 8. Orden de implementación

1. Esqueleto: imports, constantes, dict de traducción vacío con keys, registro vacío, `main` vacío.
2. Helpers (`parse_int`, `parse_money`, `parse_suffix`, `parse_float`, `_warn`, `_emit`).
3. `load_raw` / `write_normalized` + bucle `main` dirigido por el registro.
4. Normalizadores de menor a mayor complejidad: `bota_de_oro` → `goleadores` → `rrss` → `trofeo_zarra` → `palmares_clubes` → `boxoffice_mojo_worldwide` → `goleadores_historia` → `worldometers`.
5. Rellenar `WORLDOMETERS_METRIC_ES` con las 13 entradas.
6. Ejecutar, verificar §9.

## 9. Verificación

### Ejecución
```powershell
python scraper/normalize.py
```
- Stdout: 8 líneas `Normalization summary` + total combinado.
- Stderr: solo warnings esperados de `goleadores_historia` (filas con `"?"`/`""`). Cero warnings de "metric sin traducir".

Variantes:
```powershell
python scraper/normalize.py --spider worldometers
python scraper/normalize.py --output-dir /tmp/cards-test
```

### Spot-checks manuales
1. `normalized/bota_de_oro.json[0]` coincide literalmente con §7 (incluye los paréntesis y espacios alrededor de `S. L. Benfica`).
2. `normalized/boxoffice_mojo_worldwide.json` longitud = `2 × len(raw)`; las dos primeras cartas comparten `carta`.
3. En `normalized/goleadores_historia.json`, buscar `"Erwin Helmchen"`: exactamente **2** cartas (Posicion + Goles).
4. En `normalized/palmares_clubes.json`, bloque Real Madrid idéntico a §7.
5. En `normalized/rrss.json`: `KaiCenat → 20220000`, `Jynxzi → 10110000` (entrada `"10.11M"`).
6. En `normalized/worldometers.json`: Afghanistan agua = `20280000000` sin residuo `Year:`; India tiene 2 cartas; "Casos de Coronavirus" presente y "Población Global en el Mundo" presente.
7. Recorrer `all.json` y comprobar `isinstance(c["valor"], (int, float)) and not isinstance(c["valor"], bool)` en todas las cartas.
8. Re-ejecutar el script: archivos idénticos (idempotencia).

### Casos borde (que se dan en los datos crudos)
- `goleadores_historia` filas con `"P.J.": "?"` o `"Prom.": ""` ⇒ se descartan esas cartas, no toda la fila.
- `worldometers` `"yearly_water_used": "20,280,000,000  Year: 2000"` ⇒ `parse_int` se queda con `20280000000`.
- `palmares_clubes` `"Atlético  Madrid"` (doble espacio): se preserva tal cual; no cosmetizamos upstream aquí.

## 10. Riesgos y mitigaciones

- **Encoding en Windows**: usar siempre `encoding="utf-8"` y `ensure_ascii=False`. Si la consola PowerShell renderiza mal, `chcp 65001` (no es bug del script).
- **`isinstance(True, int)` es True**: los datos crudos no contienen booleanos, pero conviene no aceptarlos en `parse_int` si llegan (caso no esperado).
- **Drift de métricas worldometers**: si aparece una métrica nueva no traducida, el warning sirve de prompt para extender el dict. No es un fallo.
- **Años como int o str en boxoffice**: f-string maneja ambos transparentemente.
