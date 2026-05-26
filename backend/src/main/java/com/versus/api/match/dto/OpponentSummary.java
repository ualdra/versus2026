package com.versus.api.match.dto;

import java.util.UUID;

public record OpponentSummary(
        UUID id,
        String username,
        String avatarUrl
) {
}
