package com.versus.api.duel.engine;

import com.versus.api.cards.domain.Card;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Contexto de resolución de un round basado en cards scrapeadas.
 * Reemplaza el uso de {@code Question} en los engines para que la fuente
 * de verdad sea siempre la tabla {@code cards}.
 */
public record CardRoundContext(
        UUID cardAId,
        UUID cardBId,
        BigDecimal cardAValue,
        BigDecimal cardBValue,
        boolean inverse,
        BigDecimal correctValue
) {
    /**
     * Construye el contexto para un round binario (Survival, Binary Duel, Sabotage).
     * La opción correcta es la que tiene mayor valor (o menor si inverse=true).
     */
    public static CardRoundContext binary(Card a, Card b) {
        return new CardRoundContext(a.getId(), b.getId(), a.getValor(), b.getValor(), a.isInverse(), null);
    }

    /**
     * Construye el contexto para un round numérico (Precision Duel).
     */
    public static CardRoundContext numeric(Card card) {
        return new CardRoundContext(card.getId(), null, card.getValor(), null, false, card.getValor());
    }

    /**
     * Devuelve el ID de la opción correcta para preguntas binarias.
     * Si inverse=true, gana quien tenga el valor MÁS BAJO (mejor posición en ranking).
     */
    public UUID correctOptionId() {
        if (cardBId == null) return null;
        if (inverse) {
            return cardAValue.compareTo(cardBValue) <= 0 ? cardAId : cardBId;
        } else {
            return cardAValue.compareTo(cardBValue) >= 0 ? cardAId : cardBId;
        }
    }
}
