package com.versus.api.users.dto;

import java.time.Instant;

public record UserMeResponse(
        String id,
        String username,
        String email,
        String avatarUrl,
        String role,
        Instant createdAt
) { }
