package com.versus.api.duel.dto;

import com.versus.api.match.MatchResult;

import java.util.UUID;

public record FinalStatsPayload(
        UUID userId,
        String username,
        MatchResult result,
        int livesRemaining,
        int score,
        int bestStreakInMatch,
        int roundsPlayed,
        Double avgDeviation,
        int sabotagesUsed) {
}
