package com.versus.api.achievements.dto;

import java.time.Instant;

public record AchievementResponse(
        String id,
        String key,
        String name,
        String description,
        String iconKey,
        String category,
        boolean unlocked,
        Instant unlockedAt
) {
}
