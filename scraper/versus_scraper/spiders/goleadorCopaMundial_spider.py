import scrapy


class GoleadoresSpider(scrapy.Spider):
    name = "goleadores"
    allowed_domains = ["es.wikipedia.org"]
    start_urls = [
        "https://es.wikipedia.org/wiki/Anexo:Goleadores_de_la_Copa_Mundial_de_F%C3%BAtbol"
    ]

    def parse(self, response):
        # Localizamos el encabezado "Máximos goleadores" y, a partir de él,
        # la primera tabla wikitable que aparece después.
        tabla = response.xpath(
            '//*[@id="Máximos_goleadores"]'
            '/ancestor-or-self::*[self::h2 or self::div][1]'
            '/following::table[contains(@class, "wikitable")][1]'
        )

        # Estructura de fila: <th>pos</th><td>jugador</td><td>selección</td>
        # <td>goles</td><td>partidos</td><td>promedio</td><td>mundiales</td>...
        for fila in tabla.xpath('.//tr[td]'):
            celdas = fila.xpath('./td')
            if len(celdas) < 3:
                continue

            pos = fila.xpath('string(./th[1])').get(default='').strip()
            jugador = celdas[0].xpath('string(.)').get(default='').strip()
            goles = celdas[2].xpath('string(.)').get(default='').strip()

            if not jugador:
                continue

            yield {
                "Pos": pos,
                "Jugador": jugador,
                "Goles": goles,
            }