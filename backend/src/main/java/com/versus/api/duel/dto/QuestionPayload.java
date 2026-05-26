package com.versus.api.duel.dto;

import com.versus.api.duel.state.SabotageType;
import com.versus.api.questions.dto.QuestionResponse;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record QuestionPayload(
        int roundNumber,
        QuestionResponse question,
        Instant serverNow,
        Instant deadline,
        int timerSeconds,
        Map<UUID, SabotageType> effectsApplied) {
}
