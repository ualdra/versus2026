package com.versus.api.match.dto;

import com.versus.api.match.GameMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MatchDetailResponse(
        UUID id,
        GameMode mode,
        Instant createdAt,
        Instant finishedAt,
        List<MatchPlayerSummary> players,
        List<RoundDetailResponse> rounds
) {
}
