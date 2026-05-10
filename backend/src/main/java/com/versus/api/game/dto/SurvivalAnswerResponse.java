package com.versus.api.game.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.versus.api.achievements.dto.AchievementResponse;
import com.versus.api.questions.dto.QuestionResponse;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SurvivalAnswerResponse(
        boolean correct,
        int livesRemaining,
        int lifeDelta,
        int streak,
        int scoreDelta,
        QuestionResponse nextQuestion,
        boolean gameOver,
        List<AchievementResponse> achievementsUnlocked
) {
}
