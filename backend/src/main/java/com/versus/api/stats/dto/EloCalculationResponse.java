package com.versus.api.stats.dto;

public record EloCalculationResponse(
        int winnerDelta,
        int loserDelta) {
}
