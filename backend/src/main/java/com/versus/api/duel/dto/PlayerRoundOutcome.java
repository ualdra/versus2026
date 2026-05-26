package com.versus.api.duel.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resultado por jugador para un round terminado.
 * `answered=false` indica que el jugador no respondió antes del deadline.
 */
public record PlayerRoundOutcome(
        UUID userId,
        boolean answered,
        Boolean isCorrect,
        Double deviation,
        BigDecimal valueGiven,
        UUID optionGiven,
        int lifeDelta) {
}
