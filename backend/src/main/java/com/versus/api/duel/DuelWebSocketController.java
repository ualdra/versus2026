package com.versus.api.duel;

import com.versus.api.duel.dto.AnswerMessage;
import com.versus.api.duel.dto.AnswerResultPayload;
import com.versus.api.duel.dto.SabotageMessage;
import com.versus.api.duel.dto.SabotageRejectedPayload;
import com.versus.api.websocket.MatchEventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class DuelWebSocketController {

    private final DuelOrchestrator orchestrator;
    private final SimpMessagingTemplate broker;

    @MessageMapping("/match/answer")
    public void answer(Principal principal, @Payload AnswerMessage msg) {
        UUID userId = UUID.fromString(principal.getName());
        AnswerResultPayload result = orchestrator.submitAnswer(userId, msg);
        broker.convertAndSendToUser(userId.toString(), "/queue/match",
                MatchEventEnvelope.of("ANSWER_RESULT", msg.matchId(), result));
    }

    @MessageMapping("/match/sabotage")
    public void sabotage(Principal principal, @Payload SabotageMessage msg) {
        UUID userId = UUID.fromString(principal.getName());
        SabotageRejectedPayload rejection = orchestrator.activateSabotage(userId, msg);
        if (rejection != null) {
            broker.convertAndSendToUser(userId.toString(), "/queue/match",
                    MatchEventEnvelope.of("SABOTAGE_REJECTED", msg.matchId(), rejection));
        }
    }
}
