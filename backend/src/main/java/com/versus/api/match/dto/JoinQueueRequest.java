package com.versus.api.match.dto;

import com.versus.api.match.GameMode;
import jakarta.validation.constraints.NotNull;

public record JoinQueueRequest(@NotNull GameMode mode) {
}
