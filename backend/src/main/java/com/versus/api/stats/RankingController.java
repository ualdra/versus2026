package com.versus.api.stats;

import com.versus.api.match.GameMode;
import com.versus.api.stats.dto.RankingEntryResponse;
import com.versus.api.stats.dto.RankingSummaryResponse;
import com.versus.api.stats.dto.UserRankingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Rankings", description = "Competitive leaderboards and ELO")
@RestController
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    @Operation(summary = "Get a paginated leaderboard for one game mode",
            parameters = @Parameter(name = "mode", description = "Game mode"))
    @GetMapping("/api/rankings")
    public Page<RankingEntryResponse> leaderboard(
            @RequestParam(defaultValue = "BINARY_DUEL") GameMode mode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UUID currentUserId) {
        return rankingService.getLeaderboard(mode, page, size, currentUserId);
    }

    @Operation(summary = "Get the authenticated player's rank and ELO by mode",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/api/rankings/me")
    public List<RankingSummaryResponse> mine(@AuthenticationPrincipal UUID userId) {
        return rankingService.getMine(userId);
    }

    @Operation(summary = "Get a public user's ranking summary and match history")
    @GetMapping("/api/users/{id}/ranking")
    public UserRankingResponse userRanking(@PathVariable UUID id) {
        return rankingService.getUserRanking(id);
    }
}
