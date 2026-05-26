package com.versus.api.match.dto;

import com.versus.api.match.GameMode;
import com.versus.api.match.MatchResult;

import java.time.Instant;
import java.util.UUID;

public record MatchHistoryItemResponse(
        UUID id,
        GameMode mode,
        MatchResult result,
        int score,
        int bestStreak,
        int livesRemaining,
        int roundsPlayed,
        Instant finishedAt,
        OpponentSummary opponent
) {
}
