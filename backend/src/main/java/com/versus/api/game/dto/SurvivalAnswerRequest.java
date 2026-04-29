package com.versus.api.game.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SurvivalAnswerRequest(
        @NotNull UUID sessionId,
        @NotNull UUID questionId,
        @NotNull UUID optionId
) {
}
