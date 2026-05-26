package com.versus.api.social.dto;

import com.versus.api.social.SocialStatus;

import java.time.Instant;
import java.util.UUID;

public record FriendRequestResponse(
        UUID id,
        SocialUserResponse requester,
        SocialUserResponse addressee,
        SocialStatus status,
        Instant createdAt,
        Instant respondedAt) {
}
