package com.versus.api.duel.engine;

import com.versus.api.duel.dto.PlayerRoundOutcome;
import com.versus.api.duel.dto.RoundResultPayload;

import java.util.List;

/**
 * Resultado de evaluar un round por parte de un {@link DuelEngine}.
 * Inmutable; el orchestrator usa estos datos para actualizar runtime y broadcastear.
 */
public record RoundResolution(
        List<PlayerRoundOutcome> outcomes,
        RoundResultPayload.Reveal reveal) {
}
