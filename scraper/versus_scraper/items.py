import scrapy


class SocialMediaCreatorItem(scrapy.Item):
    name = scrapy.Field()
    image = scrapy.Field()
    subscribers = scrapy.Field()
    platform = scrapy.Field()


class WorldStatsItem(scrapy.Item):
    source = scrapy.Field()
    metric = scrapy.Field()
    value = scrapy.Field()


class CoronavirusItem(scrapy.Item):
    source = scrapy.Field()
    metric = scrapy.Field()
    value = scrapy.Field()


class CountryPopulationItem(scrapy.Item):
    rank = scrapy.Field()
    country = scrapy.Field()
    population_2026 = scrapy.Field()


class CountryWaterItem(scrapy.Item):
    country = scrapy.Field()
    yearly_water_used = scrapy.Field()
