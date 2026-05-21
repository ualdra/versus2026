package com.versus.api.match.dto;

import com.versus.api.match.GameMode;

import java.util.UUID;

public record MatchCreatedResponse(UUID matchId, GameMode mode, String roomCode) {
}
