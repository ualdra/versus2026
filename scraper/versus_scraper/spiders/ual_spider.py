import scrapy


class UalSpiderSpider(scrapy.Spider):
    name = "ual_spider"
    allowed_domains = ["ual.es"]
    start_urls = ["https://ual.es"]

    def parse(self, response):
        pass
