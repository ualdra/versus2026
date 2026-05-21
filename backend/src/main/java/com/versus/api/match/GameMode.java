package com.versus.api.match;

public enum GameMode {
    SURVIVAL(1),
    PRECISION(1),
    BINARY_DUEL(2),
    PRECISION_DUEL(2),
    SABOTAGE(2);

    private final int requiredPlayers;

    GameMode(int requiredPlayers) {
        this.requiredPlayers = requiredPlayers;
    }

    public int requiredPlayers() {
        return requiredPlayers;
    }

    public boolean isMultiplayer() {
        return requiredPlayers > 1;
    }
}
