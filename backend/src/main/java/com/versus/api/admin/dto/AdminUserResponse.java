package com.versus.api.admin.dto;

import java.time.Instant;

public record AdminUserResponse(
        String id,
        String username,
        String email,
        String avatarUrl,
        String role,
        boolean isActive,
        Instant createdAt
) { }
