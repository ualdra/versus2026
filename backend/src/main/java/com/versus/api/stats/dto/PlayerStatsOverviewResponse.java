package com.versus.api.stats.dto;

import java.util.List;

public record PlayerStatsOverviewResponse(
        List<PlayerStatsResponse> byMode,
        String favoriteMode,
        long totalPlayTimeSeconds
) {
}
