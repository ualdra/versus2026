package com.versus.api.social.dto;

import com.versus.api.match.GameMode;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateMatchInviteRequest(
        @NotNull UUID friendUserId,
        @NotNull GameMode mode,
        UUID matchId) {

    public CreateMatchInviteRequest(UUID friendUserId, GameMode mode) {
        this(friendUserId, mode, null);
    }
}
