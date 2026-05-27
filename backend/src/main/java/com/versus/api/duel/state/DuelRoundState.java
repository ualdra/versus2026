package com.versus.api.duel.state;

import com.versus.api.duel.engine.CardRoundContext;
import lombok.Data;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Data
public class DuelRoundState {
    private final int roundNumber;
    private final UUID questionId;
    private final Instant startedAt;
    private final Instant deadline;
    private volatile CardRoundContext cardContext;
    private final Map<UUID, RawAnswer> answers = new ConcurrentHashMap<>();
    private final Set<UUID> sabotageUsedBy = new HashSet<>();
    /**
     * Efectos aplicados por jugador en este round (snapshot tomado al inicio del round).
     */
    private final Map<UUID, SabotageType> effectsApplied = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> timeoutTask;
}
