package com.versus.api.questions.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.versus.api.questions.QuestionType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonTypeName("BINARY")
public record QuestionBinaryResponse(
        UUID id,
        QuestionType type,
        String text,
        String category,
        String subcategory,
        boolean inverse,
        List<QuestionOptionResponse> options,
        Instant scrapedAt
) implements QuestionResponse {
}
