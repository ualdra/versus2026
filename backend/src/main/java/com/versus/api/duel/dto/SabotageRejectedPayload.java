package com.versus.api.duel.dto;

public record SabotageRejectedPayload(String reason) {

    public static final String NO_TOKENS = "NO_TOKENS";
    public static final String ALREADY_USED = "ALREADY_USED";
    public static final String INVALID_TARGET = "INVALID_TARGET";
    public static final String WRONG_PHASE = "WRONG_PHASE";
    public static final String UNSUPPORTED_MODE = "UNSUPPORTED_MODE";
}
