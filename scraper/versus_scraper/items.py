import scrapy


class SocialMediaCreatorItem(scrapy.Item):
    name = scrapy.Field()
    image = scrapy.Field()
    subscribers = scrapy.Field()
    platform = scrapy.Field()


class QuestionItem(scrapy.Item):
    """Normalized question ready to be inserted into the `questions` table."""
    text = scrapy.Field()           # question text (used for dedup hash)
    type = scrapy.Field()           # 'BINARY' or 'NUMERIC'
    category = scrapy.Field()       # e.g. 'RRSS', 'FOOTBALL'
    source_url = scrapy.Field()
    # NUMERIC-specific
    correct_value = scrapy.Field()  # numeric answer
    unit = scrapy.Field()           # e.g. 'subscribers', 'goals'
    tolerance_percent = scrapy.Field()
    # BINARY-specific
    options = scrapy.Field()        # list of {'text': str, 'is_correct': bool}
