package com.versus.api.questions.proposals.dto;

import com.versus.api.questions.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProposeQuestionRequest(
        @NotNull QuestionType type,
        @NotBlank @Size(max = 2000) String text,
        @NotBlank @Size(max = 512) String proposedAnswer,
        @Size(max = 512) String alternativeAnswer,
        @NotBlank @Size(max = 64) String category,
        @Size(max = 1024) String sourceUrl
) {}
