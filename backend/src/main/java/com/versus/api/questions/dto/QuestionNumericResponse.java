package com.versus.api.questions.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.versus.api.questions.QuestionType;

import java.time.Instant;
import java.util.UUID;

@JsonTypeName("NUMERIC")
public record QuestionNumericResponse(
        UUID id,
        QuestionType type,
        String text,
        String category,
        String subcategory,
        String unit,
        Instant scrapedAt
) implements QuestionResponse {
}
