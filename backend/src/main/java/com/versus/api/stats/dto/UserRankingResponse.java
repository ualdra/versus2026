package com.versus.api.stats.dto;

import com.versus.api.match.dto.MatchHistoryItemResponse;
import com.versus.api.users.dto.UserPublicResponse;

import java.util.List;

public record UserRankingResponse(
        UserPublicResponse user,
        List<RankingSummaryResponse> rankings,
        List<MatchHistoryItemResponse> history) {
}
