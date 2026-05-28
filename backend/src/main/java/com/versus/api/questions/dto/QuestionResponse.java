package com.versus.api.questions.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.versus.api.questions.QuestionType;

import java.time.Instant;
import java.util.UUID;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = QuestionBinaryResponse.class, name = "BINARY"),
        @JsonSubTypes.Type(value = QuestionNumericResponse.class, name = "NUMERIC")
})
public sealed interface QuestionResponse permits QuestionBinaryResponse, QuestionNumericResponse {
    UUID id();
    QuestionType type();
    String text();
    String category();
    String subcategory();
    Instant scrapedAt();
}
