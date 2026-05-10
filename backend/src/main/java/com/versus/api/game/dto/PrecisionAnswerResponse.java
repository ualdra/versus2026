package com.versus.api.game.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.versus.api.achievements.dto.AchievementResponse;
import com.versus.api.questions.dto.QuestionResponse;

import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PrecisionAnswerResponse(
        BigDecimal correctValue,
        double deviation,
        double deviationPercent,
        int lifeDelta,
        int livesRemaining,
        QuestionResponse nextQuestion,
        boolean gameOver,
        List<AchievementResponse> achievementsUnlocked
) {
}
