# trofeo_zarra_spider.py
# Ejecutar con:  scrapy runspider trofeo_zarra_spider.py -O trofeo_zarra.json

import scrapy


class TrofeoZarraSpider(scrapy.Spider):
    name = "trofeo_zarra"
    allowed_domains = ["es.wikipedia.org"]
    start_urls = ["https://es.wikipedia.org/wiki/Trofeo_Zarra"]

    custom_settings = {
        "USER_AGENT": (
            "Mozilla/5.0 (compatible; TrofeoZarraBot/1.0; "
            "+https://example.com/bot) Scrapy"
        ),
        "ROBOTSTXT_OBEY": True,
        "DOWNLOAD_DELAY": 1.0,
    }

    def parse(self, response):
        # En la página hay varias tablas 'sortable'.
        # La primera de ellas corresponde al Trofeo Zarra de Primera División.
        tablas = response.css("table.sortable")
        if not tablas:
            self.logger.warning("No se encontró ninguna tabla sortable.")
            return
        tabla = tablas[0]

        # Saltamos la fila de cabecera (índice 0)
        filas = tabla.css("tr")[1:]

        for fila in filas:
            # Tomamos todas las celdas (td o th) de la fila
            celdas = fila.xpath("./th | ./td")
            if len(celdas) < 6:
                continue

            temporada = self._texto(celdas[0])
            jugador   = self._texto(celdas[1])
            # equipo   = self._texto(celdas[2])  # Disponible si lo necesitas
            goles     = self._texto(celdas[3])
            partidos  = self._texto(celdas[4])
            promedio  = self._texto(celdas[5])

            yield {
                "temporada": temporada,
                "jugador": jugador,
                "goles": self._a_int(goles),
                "partidos": self._a_int(partidos),
                "promedio": self._a_float(promedio),
            }

    # ---------- utilidades ----------

    @staticmethod
    def _texto(celda):
        """Extrae el texto plano de una celda eliminando notas tipo [n. 1]."""
        textos = celda.xpath(".//text()").getall()
        unido = " ".join(t.strip() for t in textos if t.strip())
        # Quitar referencias de notas como "[n. 1]" o "[1]"
        import re
        unido = re.sub(r"\[[^\]]*\]", "", unido).strip()
        return unido

    @staticmethod
    def _a_int(valor):
        try:
            return int(valor.replace(".", "").replace(",", ""))
        except (ValueError, AttributeError):
            return valor

    @staticmethod
    def _a_float(valor):
        try:
            return float(valor.replace(",", "."))
        except (ValueError, AttributeError):
            return valor