package com.versus.api.match;

import com.versus.api.common.dto.ErrorResponse;
import com.versus.api.match.dto.JoinQueueRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Matchmaking", description = "Public matchmaking queue")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/matchmaking/queue")
@RequiredArgsConstructor
public class MatchmakingController {

    private final MatchmakingService matchmakingService;

    @Operation(summary = "Enter the matchmaking queue for a multiplayer mode",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Queued"),
                    @ApiResponse(responseCode = "400", description = "Mode is single-player",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping
    public ResponseEntity<Void> join(@AuthenticationPrincipal UUID userId,
                                     @Valid @RequestBody JoinQueueRequest req) {
        matchmakingService.joinQueue(userId, req.mode());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Leave the matchmaking queue",
            responses = @ApiResponse(responseCode = "204", description = "Removed"))
    @DeleteMapping
    public ResponseEntity<Void> leave(@AuthenticationPrincipal UUID userId) {
        matchmakingService.leaveQueue(userId);
        return ResponseEntity.noContent().build();
    }
}
