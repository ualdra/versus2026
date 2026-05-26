package com.versus.api.duel.state;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class DuelPlayerRuntime {
    private final UUID userId;
    private final String username;
    @Builder.Default
    private int livesRemaining = 3;
    @Builder.Default
    private int score = 0;
    @Builder.Default
    private int currentStreak = 0;
    @Builder.Default
    private int bestStreakInMatch = 0;
    @Builder.Default
    private int roundsPlayed = 0;
    @Builder.Default
    private int sabotageTokens = 0;
    @Builder.Default
    private int sabotagesUsed = 0;
    @Builder.Default
    private boolean disconnected = false;
    /** Acumulador de desviacion porcentual para PRECISION_DUEL (avgDeviation = sum/count). */
    @Builder.Default
    private double deviationSum = 0.0;
    @Builder.Default
    private int deviationCount = 0;
    /**
     * Efectos que el rival ha activado contra este jugador y que se aplican en su PRÓXIMO round.
     */
    @Builder.Default
    private final List<PendingEffect> incomingEffects = new ArrayList<>();
}
