package com.versus.api.social.dto;

import com.versus.api.match.GameMode;
import com.versus.api.social.SocialStatus;

import java.time.Instant;
import java.util.UUID;

public record MatchInviteResponse(
        UUID id,
        UUID matchId,
        GameMode mode,
        SocialUserResponse from,
        SocialUserResponse to,
        SocialStatus status,
        Instant createdAt,
        Instant respondedAt) {
}
