package com.versus.api.duel.dto;

import com.versus.api.duel.state.SabotageType;

import java.util.UUID;

public record SabotageActivatedPayload(SabotageType type, UUID by, UUID target, int appliesOnRound) {
}
