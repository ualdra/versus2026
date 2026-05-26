package com.versus.api.duel.state;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RawAnswer(UUID userId, UUID questionId, String optionId, BigDecimal value, Instant receivedAt) {
}
