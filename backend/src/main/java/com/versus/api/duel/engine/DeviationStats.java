package com.versus.api.duel.engine;

import com.versus.api.common.exception.ApiException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Helper compartido por GameService (single-player Precision) y PrecisionDuelEngine.
 * Calcula el porcentaje de desviación de una respuesta respecto al valor correcto.
 */
public final class DeviationStats {

    private static final MathContext CTX = new MathContext(12, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private DeviationStats() {
    }

    public static BigDecimal deviationPercent(BigDecimal value, BigDecimal correctValue) {
        if (value == null) {
            throw ApiException.validation("Numeric answer is required");
        }
        if (correctValue == null || BigDecimal.ZERO.compareTo(correctValue) == 0) {
            throw ApiException.validation("Numeric question has invalid correct value");
        }
        return value.subtract(correctValue)
                .abs()
                .divide(correctValue.abs(), CTX)
                .multiply(HUNDRED);
    }

    public static double roundToTwo(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
