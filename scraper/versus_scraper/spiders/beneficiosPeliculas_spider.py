# boxoffice_spider.py
# Ejecutar con:
#   scrapy runspider boxoffice_spider.py -o boxoffice.csv

import scrapy


class BoxOfficeMojoSpider(scrapy.Spider):
    name = "boxoffice_mojo_worldwide"
    allowed_domains = ["boxofficemojo.com"]

    # Años a recoger
    years = [2020, 2021, 2022, 2023, 2024, 2025, 2026]

    # Cabeceras para evitar bloqueos básicos
    custom_settings = {
        "USER_AGENT": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/124.0.0.0 Safari/537.36"
        ),
        "DOWNLOAD_DELAY": 1.0,
        "ROBOTSTXT_OBEY": False,
        "FEED_EXPORT_ENCODING": "utf-8",
    }

    def start_requests(self):
        for year in self.years:
            url = f"https://www.boxofficemojo.com/year/world/{year}/"
            yield scrapy.Request(
                url=url,
                callback=self.parse,
                cb_kwargs={"year": year},
            )

    def parse(self, response, year):
        # La tabla principal tiene la clase "imdb-scroll-table-inner"
        # (Box Office Mojo). Seleccionamos las filas <tr> que contengan datos.
        rows = response.xpath('//table//tr[td]')

        for row in rows:
            cells = row.xpath('./td')
            if len(cells) < 3:
                continue

            rank = cells[0].xpath('normalize-space(.)').get()
            # La columna "Release Group" es un enlace
            release_group = cells[1].xpath('normalize-space(.)').get()
            worldwide = cells[2].xpath('normalize-space(.)').get()

            # Filtramos filas vacías o cabeceras residuales
            if not rank or not rank.isdigit():
                continue

            yield {
                "year": year,
                "rank": int(rank),
                "release_group": release_group,
                "worldwide": worldwide,
            }