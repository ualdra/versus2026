package com.versus.api.duel.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AnswerMessage(UUID matchId, UUID questionId, UUID optionId, BigDecimal value) {
}
