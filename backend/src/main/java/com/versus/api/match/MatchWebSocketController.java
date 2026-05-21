package com.versus.api.match;

import com.versus.api.match.dto.AbandonMessage;
import com.versus.api.match.dto.ReadyMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MatchWebSocketController {

    private final MatchService matchService;

    @MessageMapping("/match/ready")
    public void ready(Principal principal, @Payload ReadyMessage msg) {
        UUID userId = UUID.fromString(principal.getName());
        matchService.markReady(msg.matchId(), userId, true);
    }

    @MessageMapping("/match/unready")
    public void unready(Principal principal, @Payload ReadyMessage msg) {
        UUID userId = UUID.fromString(principal.getName());
        matchService.markReady(msg.matchId(), userId, false);
    }

    @MessageMapping("/match/abandon")
    public void abandon(Principal principal, @Payload AbandonMessage msg) {
        UUID userId = UUID.fromString(principal.getName());
        matchService.removePlayer(msg.matchId(), userId);
    }
}
