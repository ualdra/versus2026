package com.versus.api.match.dto;

import com.versus.api.match.GameMode;
import jakarta.validation.constraints.NotNull;

public record CreateMatchRequest(@NotNull GameMode mode) {
}
