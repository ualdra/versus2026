package com.versus.api.match.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinMatchByCodeRequest(@NotBlank String roomCode) {
}
