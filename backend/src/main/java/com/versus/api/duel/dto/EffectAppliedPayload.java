package com.versus.api.duel.dto;

import com.versus.api.duel.state.SabotageType;

import java.util.UUID;

public record EffectAppliedPayload(SabotageType type, UUID target, int roundNumber) {
}
