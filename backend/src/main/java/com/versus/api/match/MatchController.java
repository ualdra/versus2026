package com.versus.api.match;

import com.versus.api.common.dto.ErrorResponse;
import com.versus.api.match.dto.CreateMatchRequest;
import com.versus.api.match.dto.LobbyStateDto;
import com.versus.api.match.dto.MatchCreatedResponse;
import com.versus.api.match.state.LiveMatchState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Match", description = "Multiplayer match lifecycle (lobby, join, abandon)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @Operation(summary = "Create a new multiplayer match (room) and join as owner",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Match created"),
                    @ApiResponse(responseCode = "400", description = "Mode is single-player",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MatchCreatedResponse create(@AuthenticationPrincipal UUID userId,
                                       @Valid @RequestBody CreateMatchRequest req) {
        LiveMatchState state = matchService.createMatch(req.mode(), userId);
        matchService.addPlayer(state.getMatchId(), userId);
        return new MatchCreatedResponse(state.getMatchId(), state.getMode(), state.getRoomCode());
    }

    @Operation(summary = "Join an existing match by id",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Joined"),
                    @ApiResponse(responseCode = "404", description = "Match not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "Match full or already started",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping("/{id}/join")
    public LobbyStateDto join(@AuthenticationPrincipal UUID userId, @PathVariable("id") UUID matchId) {
        LiveMatchState state = matchService.addPlayer(matchId, userId);
        return matchService.toLobbyDto(state);
    }

    @Operation(summary = "Abandon the lobby/match",
            responses = @ApiResponse(responseCode = "204", description = "Abandoned"))
    @DeleteMapping("/{id}/abandon")
    public ResponseEntity<Void> abandon(@AuthenticationPrincipal UUID userId, @PathVariable("id") UUID matchId) {
        matchService.removePlayer(matchId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get the current lobby state of a match",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Current state"),
                    @ApiResponse(responseCode = "404", description = "Match not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @GetMapping("/{id}/lobby")
    public LobbyStateDto lobby(@PathVariable("id") UUID matchId) {
        return matchService.toLobbyDto(matchService.requireLive(matchId));
    }
}
