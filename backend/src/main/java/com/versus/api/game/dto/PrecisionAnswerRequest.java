package com.versus.api.game.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PrecisionAnswerRequest(
        @NotNull UUID sessionId,
        @NotNull UUID questionId,
        @NotNull BigDecimal value
) {
}
