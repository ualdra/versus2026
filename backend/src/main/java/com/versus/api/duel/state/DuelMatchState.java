package com.versus.api.duel.state;

import com.versus.api.match.GameMode;
import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class DuelMatchState {
    private final UUID matchId;
    private final GameMode mode;
    private final Map<UUID, DuelPlayerRuntime> players = new LinkedHashMap<>();
    private final AtomicInteger roundNumber = new AtomicInteger(0);
    private final Instant startedAt = Instant.now();

    private volatile DuelRoundState currentRound;
    private volatile DuelPhase phase = DuelPhase.BETWEEN_ROUNDS;
    private volatile boolean ended = false;
}
