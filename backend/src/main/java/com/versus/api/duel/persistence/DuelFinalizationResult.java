package com.versus.api.duel.persistence;

import com.versus.api.achievements.dto.AchievementResponse;
import com.versus.api.stats.dto.EloChangeResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DuelFinalizationResult(
        Map<UUID, List<AchievementResponse>> achievementsByUser,
        Map<UUID, EloChangeResponse> eloChangesByUser) {
}
