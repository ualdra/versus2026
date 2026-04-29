package com.versus.api.users.dto;

import jakarta.validation.constraints.Size;

public record UpdateMeRequest(
        @Size(min = 3, max = 64) String username,
        @Size(max = 512) String avatarUrl
) { }
