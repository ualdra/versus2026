package com.versus.api.scraping.dto;

import com.versus.api.scraping.SpiderStatus;

import java.time.Instant;
import java.util.UUID;

public record SpiderResponse(
        UUID id,
        String name,
        String targetUrl,
        SpiderStatus status,
        Instant lastRunAt,
        SpiderRunResponse lastRun
) {}
