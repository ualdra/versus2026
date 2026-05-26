package com.versus.api.practice.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PracticeAnswerRequest(
        @NotNull UUID questionId,
        UUID optionId,
        BigDecimal value
) {
}
