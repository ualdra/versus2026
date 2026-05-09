import scrapy
from scrapy_playwright.page import PageMethod

from versus_scraper.items import (
    WorldStatsItem,
    CoronavirusItem,
    CountryPopulationItem,
    CountryWaterItem,
)


class WorldometersSpider(scrapy.Spider):
    name = "worldometers"
    allowed_domains = ["worldometers.info"]

    # Habilita Playwright para renderizar la home (los contadores se rellenan
    # con JS y no son accesibles desde el HTML estático). Las otras páginas
    # se siguen scrapeando con el descargador normal de Scrapy.
    custom_settings = {
        "DOWNLOAD_HANDLERS": {
            "http": "scrapy_playwright.handler.ScrapyPlaywrightDownloadHandler",
            "https": "scrapy_playwright.handler.ScrapyPlaywrightDownloadHandler",
        },
        "PLAYWRIGHT_BROWSER_TYPE": "chromium",
        "PLAYWRIGHT_LAUNCH_OPTIONS": {"headless": True},
        "PLAYWRIGHT_DEFAULT_NAVIGATION_TIMEOUT": 30000,
    }

    def start_requests(self):
        # La home necesita Playwright + esperar a que un contador deje de
        # mostrar el placeholder "retrieving data...".
        yield scrapy.Request(
            "https://www.worldometers.info/",
            meta={
                "playwright": True,
                "playwright_page_methods": [
                    PageMethod(
                        "wait_for_function",
                        "() => { const el = document.querySelector("
                        "'span.rts-counter[rel=\"current_population\"]'); "
                        "return el && !el.innerText.toLowerCase().includes('retrieving'); }",
                        timeout=30000,
                    ),
                ],
            },
        )
        # Resto de URLs sin Playwright.
        for url in (
            "https://www.worldometers.info/coronavirus/",
            "https://www.worldometers.info/geography/countries-of-the-world/",
            "https://www.worldometers.info/water/",
        ):
            yield scrapy.Request(url)

    # Mapeo: etiqueta legible -> valor del atributo rel del span.rts-counter
    HOME_COUNTERS = {
        "Current World Population": "current_population",
        "Births this year": "births_this_year",
        "Deaths this year": "dth1s_this_year",
        "Cars produced this year": "automobile_produced/this_year",
        "Bicycles produced this year": "bicycle_produced/this_year",
        "Computers produced this year": "computers_sold/this_year",
        "New book titles published this year": "books_published/this_year",
        "Overweight people in the world": "overweight",
        "Obese people in the world": "obese",
        "Water used this year (million L)": "water_consumed/this_year",
        "Abortions this year": "ab/this_year",
        "Money spent on illegal drugs this year": "drug_spending/this_year",
    }

    def parse(self, response):
        url = response.url

        if url.rstrip("/").endswith("worldometers.info"):
            yield from self.parse_home(response)
        elif "coronavirus" in url:
            yield from self.parse_coronavirus(response)
        elif "countries-of-the-world" in url:
            yield from self.parse_countries(response)
        elif "/water" in url:
            yield from self.parse_water(response)

    # ------------------------------------------------------------------
    # Página principal
    # ------------------------------------------------------------------
    def parse_home(self, response):
        for label, rel in self.HOME_COUNTERS.items():
            # Cada contador es un <span class="rts-counter" rel="...">
            # con dígitos en sub-spans <span class="rts-nr-int ...">.
            counter = response.xpath(
                f'//span[@class="rts-counter" and @rel="{rel}"]'
            )
            if not counter:
                self.logger.warning("No se encontró el contador: %s", label)
                continue

            digits = counter.xpath('.//span[contains(@class,"rts-nr-int")]/text()').getall()
            value = ",".join(d.strip() for d in digits if d.strip())

            # Los contadores se rellenan vía JavaScript en el cliente; el HTML
            # estático solo contiene el placeholder "retrieving data...".
            # Sin un renderer JS (Splash/Playwright) no se pueden obtener.
            if not value:
                self.logger.warning(
                    "Contador '%s' vacío (requiere renderer JS).", label
                )
                continue

            item = WorldStatsItem()
            item["source"] = "worldometers.info"
            item["metric"] = label
            item["value"] = value
            yield item

    # ------------------------------------------------------------------
    # Página de coronavirus
    # ------------------------------------------------------------------
    def parse_coronavirus(self, response):
        # Bloque "Coronavirus Cases:" -> primer div.maincounter-number
        cases = response.xpath(
            '//div[contains(@class,"maincounter-number")][1]//span/text()'
        ).get()

        item = CoronavirusItem()
        item["source"] = "worldometers.info/coronavirus"
        item["metric"] = "Coronavirus Cases"
        item["value"] = cases.strip() if cases else None
        yield item

    # ------------------------------------------------------------------
    # Países del mundo (primera tabla)
    # ------------------------------------------------------------------
    def parse_countries(self, response):
        rows = response.xpath('(//table[contains(@class,"datatable")])[1]//tbody/tr')
        for row in rows:
            cells = row.xpath('./td')
            if len(cells) < 3:
                continue
            item = CountryPopulationItem()
            item["rank"] = cells[0].xpath('string(.)').get(default="").strip()
            item["country"] = cells[1].xpath('string(.)').get(default="").strip()
            item["population_2026"] = cells[2].xpath('string(.)').get(default="").strip()
            yield item

    # ------------------------------------------------------------------
    # Estadísticas de agua por país
    # ------------------------------------------------------------------
    def parse_water(self, response):
        rows = response.xpath('(//table[contains(@class,"datatable")])[1]//tbody/tr')
        for row in rows:
            cells = row.xpath('./td')
            if len(cells) < 2:
                continue
            item = CountryWaterItem()
            item["country"] = cells[0].xpath('string(.)').get(default="").strip()
            item["yearly_water_used"] = cells[1].xpath('string(.)').get(default="").strip()
            yield item