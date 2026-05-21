package com.versus.api.match.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AbandonMessage(@NotNull UUID matchId) {
}
