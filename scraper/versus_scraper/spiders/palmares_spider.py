# spider.py
import scrapy


class PalmaresClubesSpider(scrapy.Spider):
    """
    Spider que extrae la 'Tabla comparativa de Títulos' del anexo de Wikipedia
    'Clubes de fútbol de España por palmarés'.

    Columnas extraídas:
        - Rank             (posición en el ranking)
        - Club             (nombre del club)
        - LL               (Ligas - La Liga)
        - CdR              (Copa del Rey)
        - CdL              (Copa de la Liga)
        - SdE              (Supercopa de España)
        - Total Nacionales (suma de títulos nacionales)
    """

    name = "palmares_clubes"
    allowed_domains = ["es.wikipedia.org"]
    start_urls = [
        "https://es.wikipedia.org/wiki/"
        "Anexo:Clubes_de_f%C3%BAtbol_de_Espa%C3%B1a_por_palmar%C3%A9s"
    ]

    custom_settings = {
        "USER_AGENT": (
            "PalmaresClubesBot/1.0 "
            "(+https://example.com; contacto: ejemplo@example.com)"
        ),
        "ROBOTSTXT_OBEY": True,
        "FEEDS": {
            "palmares_clubes.csv": {
                "format": "csv",
                "encoding": "utf-8",
                "overwrite": True,
                "fields": [
                    "rank",
                    "club",
                    "LL",
                    "CdR",
                    "CdL",
                    "SdE",
                    "total_nacionales",
                ],
            }
        },
    }

    def parse(self, response):
        # La tabla comparativa es la primera tabla 'wikitable sortable' del artículo.
        table = response.css("table.wikitable.sortable").get()
        if not table:
            self.logger.error("No se encontró la tabla comparativa de títulos.")
            return

        # Iteramos sobre las filas del cuerpo (saltando las dos filas de cabecera).
        rows = response.css("table.wikitable.sortable")[0].css("tr")

        for row in rows:
            cells = row.xpath("./th | ./td")
            if len(cells) < 7:
                # Filas de cabecera o filas incompletas se descartan.
                continue

            # Extraemos texto plano y limpiamos espacios/saltos de línea.
            values = [
                "".join(cell.xpath(".//text()").getall()).strip()
                for cell in cells[:7]
            ]

            rank_raw = values[0]
            # Nos aseguramos de que la primera celda sea numérica (rank).
            if not rank_raw.isdigit():
                continue

            yield {
                "rank": int(rank_raw),
                "club": values[1],
                "LL": self._to_int(values[2]),
                "CdR": self._to_int(values[3]),
                "CdL": self._to_int(values[4]),
                "SdE": self._to_int(values[5]),
                "total_nacionales": self._to_int(values[6]),
            }

    @staticmethod
    def _to_int(value: str):
        """Convierte el texto de una celda a entero; devuelve 0 si está vacío
        o None si no es convertible."""
        value = (value or "").replace("\xa0", "").strip()
        if value in ("", "—", "-"):
            return 0
        try:
            return int(value)
        except ValueError:
            return value  # se devuelve el texto original si no es numérico