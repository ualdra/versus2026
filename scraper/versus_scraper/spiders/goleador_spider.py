# goleadores_spider.py
# Ejecutar con:  scrapy runspider goleadores_spider.py -o goleadores.json

import scrapy


class GoleadoresSpider(scrapy.Spider):
    name = "goleadores_historia"
    allowed_domains = ["es.wikipedia.org"]
    start_urls = [
        "https://es.wikipedia.org/wiki/Goleador_(f%C3%BAtbol)"
    ]

    custom_settings = {
        "USER_AGENT": (
            "Mozilla/5.0 (compatible; GoleadoresBot/1.0; "
            "+https://example.com/bot)"
        ),
        "ROBOTSTXT_OBEY": True,
        "FEED_EXPORT_ENCODING": "utf-8",
    }

    def parse(self, response):
        # Localizamos el encabezado de la sección
        # "Máximos goleadores de la historia del fútbol"
        # y a partir de él, la primera tabla wikitable sortable.
        # El título aparece como texto directo del h2/h3 (sin wrapper de span).
        seccion_xpath = (
            "//h2[contains(normalize-space(.), "
            "'Máximos goleadores de la historia del fútbol')] "
            "| //h3[contains(normalize-space(.), "
            "'Máximos goleadores de la historia del fútbol')]"
        )
        seccion = response.xpath(seccion_xpath)

        # Primera tabla wikitable sortable que aparece tras el encabezado.
        tabla = seccion.xpath(
            "following::table[contains(@class, 'wikitable') "
            "and contains(@class, 'sortable')][1]"
        )

        if not tabla:
            self.logger.warning("No se encontró la tabla esperada.")
            return

        # Saltamos la fila de encabezado (la primera <tr>).
        filas = tabla.xpath(".//tr[position() > 1]")

        for fila in filas:
            celdas = fila.xpath("./th | ./td")
            if len(celdas) < 6:
                continue  # fila incompleta o de notas

            posicion = self._texto(celdas[0])
            jugador = self._texto(celdas[1])
            goles = self._texto(celdas[3])
            partidos = self._texto(celdas[4])
            promedio = self._texto(celdas[5])

            # Filtra filas vacías o de subtítulos
            if not posicion or not jugador:
                continue

            yield {
                "#": posicion,
                "Jugador": jugador,
                "G": goles,
                "P.J.": partidos,
                "Prom.": promedio,
            }

    @staticmethod
    def _texto(selector):
        """Extrae el texto plano de una celda, limpiando espacios y notas."""
        partes = selector.xpath(".//text()").getall()
        texto = " ".join(t.strip() for t in partes if t.strip())
        # Elimina marcas tipo [n 1], [2], etc.
        import re
        texto = re.sub(r"\[[^\]]*\]", "", texto).strip()
        return texto