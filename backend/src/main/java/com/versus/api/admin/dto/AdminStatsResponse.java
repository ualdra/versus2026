package com.versus.api.admin.dto;

public record AdminStatsResponse(
        long totalUsers,
        long activeUsers,
        long matchesToday,
        long totalQuestions,
        long activeSpiders,
        long pendingReports
) { }
