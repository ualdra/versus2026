package com.versus.api.match.dto;

import com.versus.api.match.GameMode;

import java.util.UUID;

public record MatchStartEvent(UUID matchId, GameMode mode) {
}
