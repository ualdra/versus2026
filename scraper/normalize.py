"""Normaliza los JSON crudos de los 8 spiders al esquema {categoria, subcategoria, carta, valor}."""
from __future__ import annotations

import argparse
import json
import logging
import re
import sys
from pathlib import Path
from typing import Callable

DEFAULT_INPUT_DIR = Path("scraper/output")
DEFAULT_OUTPUT_DIR = Path("scraper/output/normalized")

WORLDOMETERS_METRIC_ES: dict[str, str] = {
    "Coronavirus Cases":                      "Casos de Coronavirus",
    "Current World Population":               "Población Global en el Mundo",
    "Births this year":                       "Nacimientos este año",
    "Deaths this year":                       "Muertes este año",
    "Cars produced this year":                "Coches producidos este año",
    "Bicycles produced this year":            "Bicicletas producidas este año",
    "Computers produced this year":           "Ordenadores producidos este año",
    "New book titles published this year":    "Nuevos libros publicados este año",
    "Overweight people in the world":         "Personas con sobrepeso en el mundo",
    "Obese people in the world":              "Personas obesas en el mundo",
    "Water used this year (million L)":       "Agua usada este año (millones L)",
    "Abortions this year":                    "Abortos este año",
    "Money spent on illegal drugs this year": "Dinero gastado en drogas ilegales este año",
}

_INT_RE = re.compile(r"([0-9][0-9,]*)")
_SUFFIX_RE = re.compile(r"^\s*([0-9]+(?:\.[0-9]+)?)\s*([KMB])?\s*$")
_SUFFIX_MULT = {"K": 1_000, "M": 1_000_000, "B": 1_000_000_000}


def parse_int(s) -> int | None:
    if isinstance(s, int) and not isinstance(s, bool):
        return s
    if isinstance(s, float):
        return int(s)
    if not isinstance(s, str) or not s.strip():
        return None
    m = _INT_RE.search(s)
    if not m:
        return None
    try:
        return int(m.group(1).replace(",", ""))
    except ValueError:
        return None


def parse_money(s: str) -> int | None:
    if not isinstance(s, str):
        return None
    cleaned = s.replace("$", "").replace(",", "").replace(" ", "")
    return parse_int(cleaned)


def parse_suffix(s: str) -> int | None:
    if not isinstance(s, str) or not s.strip():
        return None
    m = _SUFFIX_RE.match(s)
    if not m:
        return None
    try:
        num = float(m.group(1))
        mult = _SUFFIX_MULT.get(m.group(2) or "", 1)
        return int(num * mult)
    except ValueError:
        return None


def parse_float(s) -> float | None:
    if isinstance(s, float):
        return s
    if isinstance(s, int) and not isinstance(s, bool):
        return float(s)
    if not isinstance(s, str) or not s.strip() or s.strip() == "?":
        return None
    try:
        return float(s.replace(",", "."))
    except ValueError:
        return None


def _warn(msg: str) -> None:
    logging.warning(msg)


def _emit(cards: list[dict], categoria: str, subcategoria: str, carta: str, valor) -> None:
    if not carta or valor is None:
        _warn(f"descartando: categoria={categoria!r} subcategoria={subcategoria!r} carta={carta!r} valor={valor!r}")
        return
    cards.append({"categoria": categoria, "subcategoria": subcategoria, "carta": carta, "valor": valor})


def normalize_bota_de_oro(raw: list[dict]) -> list[dict]:
    cards: list[dict] = []
    for row in raw:
        carta = f"{row['bota_de_oro']} en {row['edicion']}"
        _emit(cards, "Bota de oro", "Bota de oro", carta, parse_int(row["gol"]))
    return cards


def normalize_boxoffice_mojo_worldwide(raw: list[dict]) -> list[dict]:
    cards: list[dict] = []
    for row in raw:
        carta = f"{row['release_group']} en {row['year']}"
        _emit(cards, "Beneficios de peliculas", "ranking",   carta, parse_int(row["rank"]))
        _emit(cards, "Beneficios de peliculas", "beneficio", carta, parse_money(row["worldwide"]))
    return cards


def normalize_goleadores(raw: list[dict]) -> list[dict]:
    cards: list[dict] = []
    for row in raw:
        _emit(cards, "Goleadores de la Copa Mundial", "Goleadores de la Copa Mundial",
              row["Jugador"], parse_int(row["Goles"]))
    return cards


def normalize_goleadores_historia(raw: list[dict]) -> list[dict]:
    cards: list[dict] = []
    for row in raw:
        carta = row["Jugador"]
        _emit(cards, "Maximos Goleadores", "Posicion",          carta, parse_int(row["#"]))
        _emit(cards, "Maximos Goleadores", "Goles",             carta, parse_int(row["G"]))
        _emit(cards, "Maximos Goleadores", "Partidos Jugados",  carta, parse_int(row["P.J."]))
        _emit(cards, "Maximos Goleadores", "Promedio de Goles", carta, parse_float(row["Prom."]))
    return cards


def normalize_palmares_clubes(raw: list[dict]) -> list[dict]:
    cards: list[dict] = []
    cat = "Títulos de Clubes Nacionales"
    for row in raw:
        carta = row["club"]
        _emit(cards, cat, "La Liga",             carta, parse_int(row["LL"]))
        _emit(cards, cat, "Copa del Rey",        carta, parse_int(row["CdR"]))
        _emit(cards, cat, "Copa de la Liga",     carta, parse_int(row["CdL"]))
        _emit(cards, cat, "Supercopa de España", carta, parse_int(row["SdE"]))
        _emit(cards, cat, "Total Nacionales",    carta, parse_int(row["total_nacionales"]))
    return cards


def normalize_rrss(raw: list[dict]) -> list[dict]:
    cards: list[dict] = []
    for row in raw:
        _emit(cards, "Famosos en Redes", row["platform"], row["name"], parse_suffix(row["subscribers"]))
    return cards


def normalize_trofeo_zarra(raw: list[dict]) -> list[dict]:
    cards: list[dict] = []
    for row in raw:
        carta = f"{row['jugador']} en {row['temporada']}"
        _emit(cards, "Trofeo Zarra", "Goles",    carta, parse_int(row["goles"]))
        _emit(cards, "Trofeo Zarra", "Partidos", carta, parse_int(row["partidos"]))
        _emit(cards, "Trofeo Zarra", "Promedio", carta, parse_float(row["promedio"]))
    return cards


def normalize_worldometers(raw: list[dict]) -> list[dict]:
    cards: list[dict] = []
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
            _emit(cards, "Datos Globales", "Datos Globales", carta, parse_int(row["value"]))
        else:
            _warn(f"worldometers: fila desconocida {row!r}")
    return cards


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


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description="Normaliza los JSON de los spiders de Versus.")
    parser.add_argument("--input-dir",  type=Path, default=DEFAULT_INPUT_DIR)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--spider", dest="spiders", action="append",
                        choices=list(SPIDER_NORMALIZERS), metavar="SPIDER",
                        help="Normalizar solo este spider (repetible). Sin flag → todos.")
    args = parser.parse_args(argv)

    logging.basicConfig(level=logging.WARNING, format="%(levelname)s: %(message)s", stream=sys.stderr)

    spiders = args.spiders or list(SPIDER_NORMALIZERS)
    all_cards: list[dict] = []
    summary: list[str] = []

    for spider in spiders:
        src = args.input_dir / f"{spider}.json"
        dst = args.output_dir / f"{spider}.json"
        try:
            raw = load_raw(src)
            cards = SPIDER_NORMALIZERS[spider](raw)
            write_normalized(dst, cards)
            msg = f"[{spider}] {len(raw)} filas -> {len(cards)} cartas -> {dst}"
            summary.append(msg)
            all_cards.extend(cards)
        except Exception as exc:
            logging.error(f"{spider}: {exc}")
            summary.append(f"[{spider}] ERROR: {exc}")

    if not args.spiders:
        all_path = args.output_dir / "all.json"
        write_normalized(all_path, all_cards)
        summary.append(f"[all] {len(all_cards)} cartas totales -> {all_path}")

    print("\n".join(summary))


if __name__ == "__main__":
    main()
