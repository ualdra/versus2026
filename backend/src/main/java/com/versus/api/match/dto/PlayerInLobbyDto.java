package com.versus.api.match.dto;

import java.util.UUID;

public record PlayerInLobbyDto(UUID userId, String username, String avatarUrl, boolean ready) {
}
