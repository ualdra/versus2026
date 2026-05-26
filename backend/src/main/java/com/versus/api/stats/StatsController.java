package com.versus.api.stats;

import com.versus.api.match.GameMode;
import com.versus.api.stats.dto.PlayerStatsOverviewResponse;
import com.versus.api.stats.dto.PlayerStatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Stats", description = "Player statistics and rankings")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @Operation(summary = "Get the authenticated player's stats across all modes",
            responses = @ApiResponse(responseCode = "200", description = "Overview returned"))
    @GetMapping(value = "/me", params = "!mode")
    public PlayerStatsOverviewResponse mine(@AuthenticationPrincipal UUID userId) {
        return statsService.getMine(userId);
    }

    @Operation(summary = "Get the authenticated player's stats for a specific mode",
            parameters = @Parameter(name = "mode", description = "Game mode (SURVIVAL, PRECISION, BINARY_DUEL, PRECISION_DUEL, SABOTAGE)"),
            responses = @ApiResponse(responseCode = "200", description = "Stats returned"))
    @GetMapping(value = "/me", params = "mode")
    public PlayerStatsResponse mineByMode(@AuthenticationPrincipal UUID userId,
                                          @RequestParam GameMode mode) {
        return statsService.getMine(userId, mode);
    }
}
