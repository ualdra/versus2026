package com.versus.api.users.dto;

import java.time.Instant;

public record UserPublicResponse(
        String id,
        String username,
        String avatarUrl,
        String role,
        Instant createdAt
) { }
