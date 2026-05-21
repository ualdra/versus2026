package com.versus.api.match.dto;

import java.util.UUID;

public record PlayerReadyEvent(UUID userId, boolean ready) {
}
