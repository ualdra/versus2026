package com.versus.api.practice;

import com.versus.api.cards.CardService;
import com.versus.api.cards.domain.Card;
import com.versus.api.common.exception.ApiException;
import com.versus.api.practice.dto.PracticeAnswerRequest;
import com.versus.api.practice.dto.PracticeAnswerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Evalúa respuestas de Practice. El questionId recibido es el cardId
 * (devuelto por QuestionService.getRandom como id del response).
 * Para BINARY, el questionId es el cardA.getId() del par.
 */
@Service
@RequiredArgsConstructor
public class PracticeService {

    private static final MathContext PRECISION_CONTEXT = new MathContext(12, RoundingMode.HALF_UP);

    private final CardService cardService;

    @Transactional(readOnly = true)
    public PracticeAnswerResponse evaluate(PracticeAnswerRequest request) {
        Card card = cardService.getById(request.questionId());

        if (request.optionId() != null) {
            return evaluateBinary(card, request.optionId());
        }
        if (request.value() != null) {
            return evaluateNumeric(card, request.value());
        }
        throw ApiException.validation("optionId or value is required");
    }

    private PracticeAnswerResponse evaluateBinary(Card cardA, UUID optionId) {
        // cardA.getId() == optionId → seleccionó cardA; en ese caso buscamos cardB no disponible aquí.
        // Para practice binario el frontend ya tiene ambos IDs del par; solo validamos si eligió cardA.
        // La respuesta correcta se determina por inverse: si inverse, gana el de menor valor → cardA.
        // Como no tenemos cardB en este contexto (solo tenemos cardA por su ID), devolvemos
        // si optionId == cardA.getId() como "posible" y dejamos que el frontend compare con correctOptionId.
        // Indicamos correctOptionId = cardA.getId() siempre (el frontend ya sabe quién ganó por el DTO).
        boolean correct = optionId.equals(cardA.getId());
        return new PracticeAnswerResponse(
                correct,
                cardA.getId(),
                null,
                null,
                null,
                null);
    }

    private PracticeAnswerResponse evaluateNumeric(Card card, BigDecimal value) {
        BigDecimal correctValue = card.getValor();
        if (correctValue == null || BigDecimal.ZERO.compareTo(correctValue) == 0) {
            throw ApiException.validation("Card has invalid value (zero or null)");
        }

        BigDecimal tolerance = new BigDecimal("5");
        BigDecimal deviationPercent = value
                .subtract(correctValue)
                .abs()
                .divide(correctValue.abs(), PRECISION_CONTEXT)
                .multiply(new BigDecimal("100"));

        boolean correct = deviationPercent.compareTo(tolerance) <= 0;
        double roundedDeviation = deviationPercent.setScale(2, RoundingMode.HALF_UP).doubleValue();

        return new PracticeAnswerResponse(
                correct,
                null,
                correctValue,
                roundedDeviation,
                card.getUnidad(),
                null);
    }
}
