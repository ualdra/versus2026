package com.versus.api.stats.dto;

import com.versus.api.match.GameMode;

public record PlayerStatsResponse(
        GameMode mode,
        int gamesPlayed,
        int gamesWon,
        double winRate,
        int bestStreak,
        int currentStreak,
        Double avgDeviation
) {
}
