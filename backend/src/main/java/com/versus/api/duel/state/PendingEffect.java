package com.versus.api.duel.state;

import java.util.UUID;

public record PendingEffect(SabotageType type, UUID activatedBy) {
}
