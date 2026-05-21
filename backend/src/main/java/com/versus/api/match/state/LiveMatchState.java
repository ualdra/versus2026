package com.versus.api.match.state;

import com.versus.api.match.GameMode;
import com.versus.api.match.MatchStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class LiveMatchState {
    private final UUID matchId;
    private final GameMode mode;
    private final String roomCode;
    private final Instant createdAt;
    @Builder.Default
    private MatchStatus status = MatchStatus.WAITING;
    @Builder.Default
    private final Map<UUID, LivePlayerState> players = new LinkedHashMap<>();

    public boolean allReady() {
        if (players.size() < mode.requiredPlayers()) return false;
        return players.values().stream().allMatch(LivePlayerState::isReady);
    }

    public boolean isFull() {
        return players.size() >= mode.requiredPlayers();
    }
}
