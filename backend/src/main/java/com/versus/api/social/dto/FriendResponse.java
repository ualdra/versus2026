package com.versus.api.social.dto;

import java.time.Instant;
import java.util.UUID;

public record FriendResponse(
        UUID userId,
        String username,
        String avatarUrl,
        Instant friendsSince) {
}
