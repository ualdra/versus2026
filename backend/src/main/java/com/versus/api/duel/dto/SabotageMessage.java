package com.versus.api.duel.dto;

import com.versus.api.duel.state.SabotageType;

import java.util.UUID;

public record SabotageMessage(UUID matchId, SabotageType type, UUID targetUserId) {
}
