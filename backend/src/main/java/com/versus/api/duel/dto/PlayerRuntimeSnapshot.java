package com.versus.api.duel.dto;

import com.versus.api.duel.state.SabotageType;

import java.util.List;
import java.util.UUID;

public record PlayerRuntimeSnapshot(
        UUID userId,
        int livesRemaining,
        int score,
        int currentStreak,
        int sabotageTokens,
        List<SabotageType> pendingIncomingEffects) {
}
