# bota_de_oro_spider.py
# Ejecutar con:  scrapy runspider bota_de_oro_spider.py -o bota_de_oro.json

import scrapy


class BotaDeOroSpider(scrapy.Spider):
    name = "bota_de_oro"
    allowed_domains = ["es.wikipedia.org"]
    start_urls = ["https://es.wikipedia.org/wiki/Bota_de_Oro"]

    custom_settings = {
        "USER_AGENT": (
            "Mozilla/5.0 (compatible; ScrapyBot/1.0; "
            "+https://example.com/bot)"
        ),
        "ROBOTSTXT_OBEY": True,
    }

    def parse(self, response):
        # Localizamos la sección "Historial" y, a partir de ahí,
        # la primera tabla anidada (la que contiene los datos).
        # En el HTML la estructura es:
        #   <h2 id="Historial">...</h2>
        #   <table>            <-- tabla externa de maquetación
        #     <table class="sortable"> ... </table>   <-- tabla de datos
        #   </table>
        tabla = response.xpath(
            '//h2[@id="Historial"]/following::table[contains(@class,"sortable")][1]'
        )

        if not tabla:
            self.logger.warning("No se encontró la tabla de Historial.")
            return

        # Recorremos las filas; saltamos la cabecera y los separadores
        # (estos últimos suelen tener un único <td colspan>).
        filas = tabla.xpath('.//tr')

        for fila in filas:
            celdas = fila.xpath('./td')
            if len(celdas) < 3:
                # Cabecera o fila separadora; la ignoramos.
                continue

            edicion = self._texto(celdas[0])
            bota_de_oro = self._texto(celdas[1])
            gol = self._texto(celdas[2])  # Solo el primer "Gol"

            # Validamos que la edición tenga formato de año/temporada
            if not edicion:
                continue

            yield {
                "edicion": edicion,
                "bota_de_oro": bota_de_oro,
                "gol": gol,
            }

    @staticmethod
    def _texto(selector):
        """Extrae el texto limpio de una celda, uniendo nodos y
        normalizando espacios."""
        partes = selector.xpath('.//text()').getall()
        texto = " ".join(p.strip() for p in partes if p.strip())
        # Compactamos espacios múltiples
        return " ".join(texto.split())