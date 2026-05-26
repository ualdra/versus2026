package com.versus.api.questions.dto;

import java.util.UUID;

public record QuestionOptionResponse(
        UUID id,
        String text,
        String sub,
        String unit
) {
    public QuestionOptionResponse(UUID id, String text) {
        this(id, text, null, null);
    }
}
