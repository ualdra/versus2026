package com.versus.api.match.dto;

import com.versus.api.match.GameMode;

import java.util.List;
import java.util.UUID;

public record MatchFoundEvent(UUID matchId, GameMode mode, List<PlayerInLobbyDto> opponents) {
}
