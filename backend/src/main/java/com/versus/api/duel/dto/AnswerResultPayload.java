package com.versus.api.duel.dto;

public record AnswerResultPayload(
        boolean accepted,
        String rejectionReason,
        Boolean isCorrect,
        Double deviation) {

    public static AnswerResultPayload accepted(Boolean isCorrect, Double deviation) {
        return new AnswerResultPayload(true, null, isCorrect, deviation);
    }

    public static AnswerResultPayload rejected(String reason) {
        return new AnswerResultPayload(false, reason, null, null);
    }
}
