# beneficiosPeliculas_spider.py
# BoxOfficeMojo renderiza la tabla con JavaScript, por lo que se necesita Playwright.
# Ejecutar con:
#   scrapy crawl boxoffice_mojo_worldwide -o output/boxoffice.json

import scrapy
from scrapy_playwright.page import PageMethod


class BoxOfficeMojoSpider(scrapy.Spider):
    name = "boxoffice_mojo_worldwide"
    allowed_domains = ["boxofficemojo.com"]

    years = [2020, 2021, 2022, 2023, 2024, 2025, 2026]

    custom_settings = {
        "DOWNLOAD_HANDLERS": {
            "http": "scrapy_playwright.handler.ScrapyPlaywrightDownloadHandler",
            "https": "scrapy_playwright.handler.ScrapyPlaywrightDownloadHandler",
        },
        "TWISTED_REACTOR": "twisted.internet.asyncioreactor.AsyncioSelectorReactor",
        "PLAYWRIGHT_BROWSER_TYPE": "chromium",
        "PLAYWRIGHT_LAUNCH_OPTIONS": {"headless": True},
        "DOWNLOAD_DELAY": 2.0,
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
                meta={
                    "playwright": True,
                    "playwright_page_methods": [
                        # Espera a que aparezca al menos una fila de datos
                        PageMethod("wait_for_selector", "table tr td", timeout=15000),
                    ],
                },
            )

    def parse(self, response, year):
        rows = response.xpath('//table//tr[td]')

        for row in rows:
            cells = row.xpath('./td')
            if len(cells) < 3:
                continue

            rank = cells[0].xpath('normalize-space(.)').get()
            release_group = cells[1].xpath('normalize-space(.)').get()
            worldwide = cells[2].xpath('normalize-space(.)').get()

            if not rank or not rank.isdigit():
                continue

            yield {
                "year": year,
                "rank": int(rank),
                "release_group": release_group,
                "worldwide": worldwide,
            }