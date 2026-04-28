import scrapy
from versus_scraper.items import SocialMediaCreatorItem


class RrssPider(scrapy.Spider):
    name = "rrss"
    allowed_domains = ["socialblade.com"]

    def __init__(self, *args, **kwargs):
        super(RrssPider, self).__init__(*args, **kwargs)
        self.start_urls = [
            "https://socialblade.com/youtube/lists/top/100/subscribers/all/ES",
            "https://socialblade.com/tiktok/lists/top/100/followers",
            "https://socialblade.com/twitch/lists/top/100/followers",
        ]

    def start_requests(self):
        for url in self.start_urls:
            yield scrapy.Request(
                url,
                callback=self.parse,
                meta={"dont_obey_robotstxt": True},
                headers={
                    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                },
            )

    def parse(self, response):
        # Determine platform from URL
        if "youtube" in response.url:
            platform = "YouTube"
        elif "tiktok" in response.url:
            platform = "TikTok"
        elif "twitch" in response.url:
            platform = "Twitch"
        else:
            platform = "Unknown"

        # Extract rows from the table
        rows = response.css("table tbody tr")

        for idx, row in enumerate(rows, 1):
            # Extract rank from first column (inside <a> tag)
            rank = row.css("td:nth-child(1) a::text").get()
            if rank:
                rank = rank.strip()

            # Extract name from second column (inside <span> within <a> tag)
            name = row.css("td:nth-child(2) a span.px-4::text").get()
            if name:
                name = name.strip()

            # Extract image from second column
            image = row.css("td:nth-child(2) img::attr(src)").get()

            # Extract subscribers from third column (inside <a> tag)
            subscribers = row.css("td:nth-child(3) a::text").get()
            if subscribers:
                subscribers = subscribers.strip()

            if name and subscribers:
                item = SocialMediaCreatorItem()
                item["name"] = name
                item["image"] = image
                item["subscribers"] = subscribers
                item["platform"] = platform

                yield item
