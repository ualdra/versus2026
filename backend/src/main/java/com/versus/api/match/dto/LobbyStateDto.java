package com.versus.api.match.dto;

import com.versus.api.match.GameMode;
import com.versus.api.match.MatchStatus;

import java.util.List;
import java.util.UUID;

public record LobbyStateDto(
        UUID matchId,
        GameMode mode,
        MatchStatus status,
        List<PlayerInLobbyDto> players,
        int requiredPlayers,
        String roomCode) {
}
