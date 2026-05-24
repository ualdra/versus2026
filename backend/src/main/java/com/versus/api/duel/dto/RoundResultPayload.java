package com.versus.api.duel.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RoundResultPayload(
        int roundNumber,
        UUID questionId,
        Reveal reveal,
        List<PlayerRoundOutcome> outcomes,
        Map<UUID, PlayerRuntimeSnapshot> runtime) {

    public record Reveal(UUID correctOptionId, BigDecimal correctValue) {
    }
}
