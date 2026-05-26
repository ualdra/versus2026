package com.versus.api.duel.dto;

import java.util.List;
import java.util.UUID;

public record MatchEndPayload(
        UUID winnerUserId,
        String reason,
        List<FinalStatsPayload> stats) {

    public static final String REASON_NORMAL = "NORMAL";
    public static final String REASON_DISCONNECT = "DISCONNECT";
    public static final String REASON_MAX_ROUNDS_TIE = "MAX_ROUNDS_TIE";
    public static final String REASON_NO_QUESTION = "NO_QUESTION";
}
