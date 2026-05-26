package com.versus.api.match.dto;

import java.util.UUID;

public record RoundDetailResponse(
        int roundNumber,
        UUID questionId,
        String questionText,
        boolean correct,
        String answerGiven,
        Double deviation
) {
}
