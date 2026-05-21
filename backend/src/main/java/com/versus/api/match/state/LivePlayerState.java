package com.versus.api.match.state;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class LivePlayerState {
    private final UUID userId;
    private final String username;
    private final String avatarUrl;
    private boolean ready;
    private int livesRemaining;
    private int score;
    private int currentStreak;
    private int bestStreak;
    private int sabotageTokens;
}
