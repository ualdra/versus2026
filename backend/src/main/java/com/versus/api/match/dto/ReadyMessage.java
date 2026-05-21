package com.versus.api.match.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReadyMessage(@NotNull UUID matchId) {
}
