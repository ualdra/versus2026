package com.versus.api.match.dto;

import com.versus.api.match.MatchResult;

import java.util.UUID;

public record MatchPlayerSummary(
        UUID userId,
        String username,
        int score,
        int livesRemaining,
        int bestStreakInMatch,
        MatchResult result
) {
}
