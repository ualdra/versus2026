package com.versus.api.game.dto;

import com.versus.api.questions.dto.QuestionResponse;

import java.util.UUID;

public record StartGameResponse(
        UUID sessionId,
        QuestionResponse question
) {
}
