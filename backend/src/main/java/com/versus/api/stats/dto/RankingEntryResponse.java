package com.versus.api.stats.dto;

import com.versus.api.match.GameMode;

import java.time.Instant;
import java.util.UUID;

public record RankingEntryResponse(
        UUID userId,
        String username,
        String avatarUrl,
        GameMode mode,
        long rank,
        int rating,
        int wins,
        int losses,
        int winStreak,
        int lastDelta,
        Instant updatedAt,
        boolean currentUser) {
}
