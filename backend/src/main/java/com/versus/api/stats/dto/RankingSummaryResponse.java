package com.versus.api.stats.dto;

import com.versus.api.match.GameMode;

public record RankingSummaryResponse(
        GameMode mode,
        long rank,
        int rating,
        int wins,
        int losses,
        int winStreak,
        int lastDelta) {
}
