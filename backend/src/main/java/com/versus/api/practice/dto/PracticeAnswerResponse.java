package com.versus.api.practice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PracticeAnswerResponse(
        boolean correct,
        UUID correctOptionId,
        BigDecimal correctValue,
        Double deviationPercent,
        String unit,
        String explanation
) {
}
