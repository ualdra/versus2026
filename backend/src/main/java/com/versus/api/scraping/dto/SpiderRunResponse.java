package com.versus.api.scraping.dto;

import java.time.Instant;
import java.util.UUID;

public record SpiderRunResponse(
        UUID id,
        Instant startedAt,
        Instant finishedAt,
        Integer questionsInserted,
        Integer errors
) {}
